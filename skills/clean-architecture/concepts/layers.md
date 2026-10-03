# 四層詳解

每一層用同樣格式說明：**職責 → 放什麼 → 不放什麼 → 範例 → 測試方式**。
範例領域見 [example-domain.md](../../../shared/example-domain.md)。

---

## 1. Entities（`domain/`）

**職責**：表達「業務本身」的規則。就算明天不用電腦、改用紙本，這些規則仍然成立。

| ✅ 放這裡 | ❌ 不放這裡 |
|---|---|
| Entity（有 id、有行為）：`Member`、`Book`、`Loan` | 存取資料庫的程式碼 |
| Value（無 id、不可變）：`MemberId`、`Money`、`DateRange` | `Clock.now()` 呼叫（時間要從參數傳進來） |
| 領域錯誤：`BookNotAvailable`、`LoanLimitExceeded` | JSON / ORM 註解 |
| 純計算：罰金計算、到期日計算 | 寄 email、記 log |
| 跨多個 entity 的規則（Domain Service，無狀態） | 「誰呼叫我」的知識 |

**關鍵技巧：把「現在時間」當參數傳入**，entity 就完全不需要外部依賴。

```
// FILE: <domain>/loan.x
ENTITY Loan
  id:         LoanId
  memberId:   MemberId
  bookId:     BookId
  borrowedAt: DateTime
  dueDate:    Date
  returnedAt: DateTime | Nothing
  renewCount: Int

  CONSTANT LOAN_DAYS = 14                          // R5
  CONSTANT FINE_PER_DAY = Money(10)                // R6
  CONSTANT MAX_RENEW = 1                           // R7

  STATIC FUNCTION open(id, memberId, bookId, now: DateTime) -> Loan
    RETURN Loan(id, memberId, bookId, borrowedAt: now,
                dueDate: now.date + LOAN_DAYS, returnedAt: Nothing, renewCount: 0)

  FUNCTION isOverdue(now: DateTime) -> Bool
    RETURN returnedAt == Nothing AND now.date > dueDate

  FUNCTION close(now: DateTime) -> Money            // 回傳罰金
    IF returnedAt != Nothing
      FAIL LoanAlreadyClosed
    returnedAt ← now
    lateDays ← max(0, now.date - dueDate)
    RETURN FINE_PER_DAY × lateDays

  FUNCTION renew(now: DateTime)
    IF isOverdue(now)           FAIL CannotRenewOverdueLoan
    IF renewCount >= MAX_RENEW  FAIL RenewLimitExceeded
    dueDate    ← dueDate + LOAN_DAYS
    renewCount ← renewCount + 1

  MUST NOT import application, adapters, infrastructure, any framework
```

```
// FILE: <domain>/member.x
ENUM MemberTier { REGULAR, VIP }

ENTITY Member
  id:        MemberId
  email:     Email
  tier:      MemberTier
  suspended: Bool

  FUNCTION loanLimit() -> Int
    RETURN tier == VIP ? 5 : 3                                   // R2

  // 把「借書資格」集中在一個方法，use case 只需呼叫它
  FUNCTION assertCanBorrow(openLoans: List<Loan>, now: DateTime)
    IF suspended                                  FAIL MemberSuspended       // R1
    IF openLoans.count >= loanLimit()             FAIL LoanLimitExceeded     // R2
    IF openLoans.any(l -> l.isOverdue(now))       FAIL HasOverdueLoans       // R3
```

**測試方式**：純單元測試，不需要 mock，不需要 DB。這一層應該有**最多**的測試。

---

## 2. Use Cases（`application/`）

**職責**：編排「這套系統」的一個操作。像食譜：取材料（讀資料）→ 照規則處理（呼叫 entity）→ 存起來 → 通知。

| ✅ 放這裡 | ❌ 不放這裡 |
|---|---|
| 一個 use case 一個類別 / 函式：`BorrowBook` | 業務規則判斷（`if openLoans.count >= 3` ← 應在 Entity） |
| 輸出 Port 介面：`LoanRepository`、`Clock`、`Notifier` | Port 的實作 |
| Input / Output DTO | HTTP status code、JSON 格式 |
| 交易邊界（透過 `UnitOfWork` port） | SQL、ORM 查詢 |
| 應用層錯誤：`MemberNotFound` | 框架的 Request / Response 物件 |

**Use case 的標準步驟（幾乎所有寫入型 use case 都長這樣）**：

```
1. 驗證輸入（格式層面，例如 id 非空）
2. 透過 port 載入需要的 entity
3. 呼叫 entity 的方法執行業務規則
4. 透過 port 儲存變更
5. 觸發副作用（通知、事件）
6. 回傳 Output DTO
```

完整範例見 [borrow-book-end-to-end.md](../pseudocode/borrow-book-end-to-end.md)。

**測試方式**：單元測試，用**假的 port 實作**（in-memory repository、固定時間的 clock）。不需要 DB。

---

## 3. Interface Adapters（`adapters/`）

**職責**：翻譯。把外面的格式變成內層看得懂的，把內層的結果變成外面要的。

| 子類型 | 方向 | 範例 | 做什麼 |
|---|---|---|---|
| Controller | 外 → 內 | `HttpLoanController` | HTTP request → Input DTO → 呼叫 use case |
| Presenter / View Model | 內 → 外 | `BorrowBookJsonPresenter` | Output DTO → JSON / HTML |
| Repository 實作 | 內 → 外 | `SqlLoanRepository` | Entity ⇄ DB row |
| Gateway | 內 → 外 | `SmtpNotifier` | 呼叫外部服務 |
| Mapper | 雙向 | `LoanRowMapper` | 純轉換函式 |

| ✅ 放這裡 | ❌ 不放這裡 |
|---|---|
| 解析 request、驗證格式（JSON schema） | 業務規則 |
| 錯誤翻譯（`BookNotAvailable` → 409） | 直接呼叫另一個 controller |
| ORM model / DB schema 定義（或放 infrastructure） | 多個 use case 的流程編排（那是新的 use case） |

**測試方式**：
- Controller：用假的 use case，驗證「request → input」與「output → response」轉換正確
- Repository：整合測試，接真的（或容器化的）DB

---

## 4. Frameworks & Drivers（`infrastructure/` + `main`）

**職責**：細節。框架、資料庫驅動、設定檔、啟動程式、DI 組裝。

| ✅ 放這裡 | ❌ 不放這裡 |
|---|---|
| DB 連線池、migration | 任何業務邏輯 |
| Web server 啟動、路由註冊 | |
| 讀取環境變數 / 設定 | |
| **Composition Root**：建立所有 adapter 與 use case 並接起來 | |

```
// FILE: <main>
FUNCTION main()
  config   ← loadConfig(env)
  db       ← connectDatabase(config.dbUrl)

  // 1. 建立 driven adapters（輸出端）
  loans    ← SqlLoanRepository(db)
  books    ← SqlBookRepository(db)
  members  ← SqlMemberRepository(db)
  clock    ← SystemClock()
  notifier ← SmtpNotifier(config.smtp)
  uow      ← SqlUnitOfWork(db)

  // 2. 建立 use cases，注入 ports
  borrowBook ← BorrowBook(members, books, loans, clock, notifier, uow)
  returnBook ← ReturnBook(books, loans, clock, uow)

  // 3. 建立 driving adapters（輸入端），注入 use cases
  controller ← HttpLoanController(borrowBook, returnBook)

  // 4. 啟動框架
  server ← WebServer()
  controller.registerRoutes(server)
  server.listen(config.port)
```

**這是整個系統唯一知道「所有東西」的地方。** 也是唯一可以使用 DI 容器的地方（若有用的話）。
