# 第 4 層：Frameworks & Drivers（框架與驅動）

> 位置：`<infrastructure>` + `<main>`｜依賴：所有層｜被誰使用：無（程式進入點）

## 職責

**細節**。框架、資料庫驅動、Web server、設定檔、啟動程式，以及把所有物件接起來的 **Composition Root**。
這一層換掉，業務不應該知道。

## 遵循規則

| 必須 | 禁止 |
|---|---|
| 物件組裝**只發生在這裡**（`<main>` 或模組的 `module.x`） | 其他層 `new` adapter 或使用 service locator |
| 設定值（環境變數、設定檔）在這裡讀取，以參數傳給 adapter | adapter / use case 自己讀 `env` |
| 依設定切換實作（`STORAGE=csv|sql`） | 用 `if` 散落在各處判斷要用哪種儲存 |
| 使用 DI 容器時，只在這一層設定 | use case / entity 上加 DI 註解（`@Injectable`、`@Service`） |
| 保持精簡：只有接線與啟動 | 任何業務邏輯 |

## 放什麼 / 不放什麼

| 種類 | 範例 |
|---|---|
| 設定讀取 | `config.x`：讀 env、驗證必填值 |
| 連線與資源 | `database.x`：連線池；`smtp_client.x` |
| 框架初始化 | `web_server.x`：建立 server、middleware |
| Composition Root | `<main>`：建立 adapter → 建立 use case → 接到 controller → 啟動 |
| 模組組裝（by-feature） | `modules/<module>/module.x`：組裝該模組，回傳其 controller 與 public API |
| Migration / Seed | `migrations/`、`data-seed/` |
| ❌ 不放 | 業務規則、格式轉換、SQL 查詢邏輯（那是 repository adapter） |

## 怎麼寫（步驟）

1. 讀設定（含 `storage`、`dataDir`、`csvEncoding`、`dbUrl`）
2. 依設定建立資源（DB 連線只在 `storage=sql` 時建立）
3. **由外往內建立**：driven adapters → use cases（注入 port 實作）→ driving adapters（注入 use case）
4. 註冊路由 / CLI 指令，啟動
5. 新增 use case 時：在這裡加一行建立它，並接到對應的 controller

## Pseudocode

```
// FILE: <infrastructure>/config.x
FUNCTION loadConfig(env) -> Config
  RETURN Config(
    port:        env.PORT ?? 3000,
    storage:     env.STORAGE ?? "csv",              // 新功能預設 CSV
    dataDir:     env.DATA_DIR ?? "./data",
    csvEncoding: env.CSV_ENCODING ?? "utf-8",
    dbUrl:       env.DATABASE_URL,                  // storage=sql 時必填
    smtp:        SmtpConfig(env.SMTP_HOST, env.SMTP_USER, env.SMTP_PASS))

// FILE: <main>
FUNCTION main()
  config ← loadConfig(env)

  // 1. Driven adapters（依設定切換）
  IF config.storage == "csv"
    store   ← CsvStore(config.dataDir, config.csvEncoding)
    members ← CsvMemberRepository(store)
    books   ← CsvBookRepository(store)
    loans   ← CsvLoanRepository(store)
    uow     ← CsvUnitOfWork(config.dataDir)
  ELSE
    db      ← connectDatabase(config.dbUrl)
    members ← SqlMemberRepository(db)
    books   ← SqlBookRepository(db)
    loans   ← SqlLoanRepository(db)
    uow     ← SqlUnitOfWork(db)
  clock    ← SystemClock()
  notifier ← SmtpNotifier(SmtpClient(config.smtp), members)

  // 2. Use cases：注入 port 實作
  borrowBook ← BorrowBook(members, books, loans, clock, notifier, uow)
  returnBook ← ReturnBook(books, loans, clock, uow)
  renewLoan  ← RenewLoan(loans, clock)

  // 3. Driving adapters：注入 use cases
  controller ← HttpLoanController(borrowBook, returnBook, renewLoan)

  // 4. 啟動
  server ← WebServer()
  controller.registerRoutes(server)
  server.listen(config.port)
```

by-feature 時，每個模組有自己的組裝函式，`<main>` 只負責呼叫：

```
// FILE: src/modules/lending/module.x
FUNCTION createLendingModule(infra: SharedInfra, membership: MembershipPublicApi) -> LendingModule
  loans    ← infra.storage == "csv" ? CsvLoanRepository(infra.store) : SqlLoanRepository(infra.db)
  borrower ← MembershipBorrowerLookup(membership)             // 跨模組：透過對方 public API
  borrowBook ← BorrowBook(borrower, ..., loans, infra.clock, ...)
  RETURN LendingModule(controller: HttpLoanController(borrowBook, ...))

// FILE: src/main.x
FUNCTION main()
  infra      ← createSharedInfra(loadConfig(env))
  membership ← createMembershipModule(infra)
  lending    ← createLendingModule(infra, membership.publicApi)
  server     ← WebServer()
  membership.controller.registerRoutes(server)
  lending.controller.registerRoutes(server)
  server.listen(infra.config.port)
```

## 與其他層的配合

```
               ┌──────────────── <main>（Composition Root）────────────────┐
  env / 設定 ──▶│ 1. 建立連線、client                                        │
               │ 2. new Driven Adapters(連線)        ──┐                     │
               │ 3. new Use Cases(adapters 當作 port) ◀─┘──┐                 │
               │ 4. new Driving Adapters(use cases)  ◀────┘──┐              │
               │ 5. 路由 / 指令 ──▶ Driving Adapters ◀───────┘ → 啟動 server │
               └──────────────────────────────────────────────────────────┘
       執行期：請求 ──▶ 框架 ──▶ Controller ──▶ Use Case ──▶ Entity / Port ──▶ Adapter
```

| 對象 | 關係 | 傳什麼 |
|---|---|---|
| Driven Adapters | **我建立它**，並注入連線、client、設定值 | DB 連線、`CsvStore`、SMTP client |
| Use Cases | **我建立它**，注入 port 的實作 | adapter 實例（以 port 型別傳入） |
| Driving Adapters | **我建立它**，注入 use case；把路由接到它 | use case 實例、server |
| Entities | **不接觸** | — |
| 我依賴誰 | 所有層（這是唯一允許的地方） | — |

**關鍵**：這是整個系統**唯一知道所有具體實作**的地方。換資料庫、換框架、加新入口，都只在這裡改接線。

## 測試

- 不寫單元測試；用**端到端測試 / smoke test** 確認接線正確：啟動 → 打一個 API → 檢查結果
- `storage=csv` 與 `storage=sql` 各跑一次主要路徑（new-feature Step 8、Step 9）

## 自我檢查

- [ ] 全專案只有這一層出現 `new XxxAdapter(...)` / `SystemClock()`
- [ ] 沒有業務邏輯；設定只在這裡讀
- [ ] 換儲存方式只需要改設定值
- [ ] 使用 DI 容器時，內層類別上沒有容器註解
