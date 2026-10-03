# 第 4 層：Frameworks & Drivers（框架與驅動）

> 位置：`<infrastructure>` + `<main>`｜依賴：所有層｜被誰使用：無（程式進入點）

## 職責

**細節**。框架、資料庫驅動、Web server、設定檔、啟動程式，以及把所有物件接起來的 **Composition Root**。
這一層換掉，業務不應該知道。

## 遵循規則

| 必須 | 禁止 |
|---|---|
| 物件組裝**只發生在這裡**（`<main>`：Spring `@Configuration` 或 `main` 方法） | 其他層 `new` adapter 或使用 service locator |
| 設定值（properties、環境變數）在這裡讀取，以建構子參數傳給 adapter | adapter / use case 自己讀 `System.getenv` 或 `@Value` |
| 依設定切換實作（Spring profile `csv` / `sql`） | 用 `if` 散落在各處判斷要用哪種儲存 |
| 使用 DI 容器時，只在這一層設定 | use case / entity 上加 DI 註解（`@Service`、`@Component`、`@Autowired`） |
| 保持精簡：只有接線與啟動 | 任何業務邏輯 |

## 放什麼 / 不放什麼

| 種類 | 範例 |
|---|---|
| 設定讀取 | `application.properties` + `@Value`，或自己讀 `System.getenv` |
| 連線與資源 | `DataSource` 連線池、`JavaMailSender` |
| 框架初始化 | Spring `@Configuration`、`web.xml` / Spring Boot 啟動類別 |
| Composition Root | `<main>`：`LendingConfig`（`@Bean` 建立 adapter → use case），controller 由 Spring 掃描 |
| 模組組裝（by-feature） | 每個模組一個 `@Configuration`（`LendingConfig`、`CatalogConfig`） |
| Migration / Seed | `migrations/`、`data-seed/` |
| ❌ 不放 | 業務規則、格式轉換、SQL 查詢邏輯（那是 repository adapter） |

## 怎麼寫（步驟）

1. 讀設定（含 `storage`、`dataDir`、`csvEncoding`、`dbUrl`）
2. 依設定建立資源（DB 連線只在 `storage=sql` 時建立）
3. **由外往內建立**：driven adapters → use cases（注入 port 實作）→ driving adapters（注入 use case）
4. 註冊路由 / CLI 指令，啟動
5. 新增 use case 時：在這裡加一行建立它，並接到對應的 controller

## 範例程式碼

Java，相容 JDK 1.7。以 Spring 4 的 Java Config 當 composition root：**domain 與 use case 類別上沒有任何 Spring 註解**，
全部在這裡用 `@Bean` 建立。

```properties
# FILE: src/main/resources/application.properties
spring.profiles.active=csv        # 新功能預設 CSV；使用者確認後改成 sql
data.dir=./data
csv.encoding=UTF-8
```

儲存方式用 Spring profile 切換：CSV 與 SQL 各一個設定類別，只有啟用的那個會建立 bean（不會因為沒有資料庫而啟動失敗）。

```java
// FILE: <main>/CsvStorageConfig.java
@Configuration
@Profile("csv")
public class CsvStorageConfig {
    @Value("${data.dir:./data}")    private String dataDir;
    @Value("${csv.encoding:UTF-8}") private String csvEncoding;

    @Bean
    public CsvStore csvStore() { return new CsvStore(new File(dataDir), Charset.forName(csvEncoding)); }

    @Bean
    public LoanRepository loanRepository(CsvStore store) { return new CsvLoanRepository(store); }

    @Bean
    public UnitOfWork unitOfWork() { return new CsvUnitOfWork(new File(dataDir)); }
    // memberRepository()、bookRepository() 寫法相同
}

// FILE: <main>/SqlStorageConfig.java         （使用者確認功能後才新增）
@Configuration
@Profile("sql")
public class SqlStorageConfig {
    @Bean
    public LoanRepository loanRepository(DataSource dataSource) { return new SqlLoanRepository(dataSource); }

    @Bean
    public UnitOfWork unitOfWork(PlatformTransactionManager txManager) { return new SpringUnitOfWork(txManager); }
}

// FILE: <main>/LendingConfig.java          （by-feature 時每個模組一個）
@Configuration
public class LendingConfig {

    // 1. 與儲存無關的 driven adapters
    @Bean
    public Clock clock() { return new SystemClock(); }

    @Bean
    public Notifier notifier(JavaMailSender mailSender) { return new SmtpNotifier(new SpringMailSender(mailSender)); }

    // 2. Use cases：注入 port 實作（use case 類別本身沒有 @Service）
    @Bean
    public BorrowBook borrowBook(MemberRepository members, BookRepository books, LoanRepository loans,
                                 Clock clock, Notifier notifier, UnitOfWork uow) {
        return new BorrowBook(members, books, loans, clock, notifier, uow);
    }

    @Bean
    public ReturnBook returnBook(BookRepository books, LoanRepository loans, Clock clock, UnitOfWork uow) {
        return new ReturnBook(books, loans, clock, uow);
    }

    // 3. Driving adapters：LoanController 有 @RestController，由 Spring 掃描並注入上面的 use case
}
```

不用 Spring 時，`<main>` 就是一個普通的 `main` 方法，手動 `new` 出所有物件：

```java
// FILE: <main>/Main.java
public class Main {
    public static void main(String[] args) {
        String storage = System.getProperty("storage", "csv");
        CsvStore store = new CsvStore(new File("./data"), Charset.forName("UTF-8"));

        LoanRepository loans = "sql".equals(storage) ? new SqlLoanRepository(createDataSource()) : new CsvLoanRepository(store);
        // members、books、uow 同理

        BorrowBook borrowBook = new BorrowBook(members, books, loans, new SystemClock(), notifier, uow);
        AdminCli cli = new AdminCli(borrowBook);                   // driving adapter
        cli.run(args);
    }
}
```

by-feature 時，模組之間的依賴也在組裝時接上：

```java
// FILE: <main>/LendingConfig.java    （節錄）
@Bean
public BorrowerLookup borrowerLookup(MembershipApi membershipApi) {   // 對方模組的公開 API
    return new MembershipBorrowerLookup(membershipApi);              // 本模組的 integration adapter
}
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
