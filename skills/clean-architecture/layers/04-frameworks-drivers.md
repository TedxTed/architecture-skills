# 第 4 層：Frameworks & Drivers（框架與驅動）

> 位置：`<infrastructure>` + `<main>`｜依賴：所有層｜被誰使用：無（程式進入點）

## 職責

**細節**。框架、資料庫驅動、Web server、設定檔、啟動程式，以及把所有物件接起來的 **Composition Root**。
這一層換掉，業務不應該知道。

## 遵循規則

| 必須 | 禁止 |
|---|---|
| 物件組裝**只發生在這裡**（`<main>`：一個專門建立物件的類別） | 其他層 `new` adapter 或使用 service locator |
| 設定值（properties、環境變數）在這裡讀取，以建構子參數傳給 adapter | adapter / use case 自己讀 `System.getenv` 或設定檔 |
| 依設定切換實作（`storage=csv` / `sql`），**整個專案只在這裡判斷一次** | 用 `if` 散落在各處判斷要用哪種儲存 |
| 使用 DI 框架（Spring、Guice…）時，只在這一層設定 | use case / entity 上加 DI 註解（例如 `@Service`、`@Component`、`@Inject`） |
| 保持精簡：只有接線與啟動 | 任何業務邏輯 |

## 放什麼 / 不放什麼

| 種類 | 範例 |
|---|---|
| 設定讀取 | 讀 `.properties` 檔或 `System.getenv` |
| 連線與資源 | `DataSource` 連線池、寄信用的 client |
| 框架初始化 | `ServletContextListener`、`web.xml`；用 Spring 時的 `@Configuration` 或 XML |
| Composition Root | `<main>`：`LendingModule`（普通 Java 類別，建立 adapter → use case → 輸入端 adapter） |
| 模組組裝（by-feature） | 每個模組一個組裝類別（`LendingModule`、`CatalogModule`） |
| Migration / Seed | `migrations/`、`data-seed/` |
| ❌ 不放 | 業務規則、格式轉換、SQL 查詢邏輯（那是 repository adapter） |

## 怎麼寫（步驟）

1. 讀設定（含 `storage`、`data.dir`、`csv.encoding`、資料庫連線設定）
2. 依設定建立資源（DB 連線只在 `storage=sql` 時建立）
3. **由外往內建立**：driven adapters → use cases（注入 port 實作）→ driving adapters（注入 use case）
4. 交給框架或 `main` 啟動
5. 新增 use case 時：在組裝類別加一行建立它，並接到對應的輸入端 adapter

## 範例程式碼

Java，相容 JDK 1.7。**預設寫法是一個普通的 Java 組裝類別，不需要任何 DI 框架。**
CSV 或 SQL 依設定值決定；CSV 模式不會建立資料庫連線，所以沒有資料庫也能啟動。

```properties
# FILE: config/application.properties
storage=csv                 # 新功能預設 csv；使用者確認後改成 sql
data.dir=./data
csv.encoding=UTF-8
```

```java
// FILE: <main>/LendingModule.java                （Composition Root：普通 Java 類別）
public class LendingModule {
    private final LoanWebAdapter loanWebAdapter;
    private final AdminCli adminCli;

    public LendingModule(Properties config) {
        String storage = config.getProperty("storage", "csv");
        File dataDir = new File(config.getProperty("data.dir", "./data"));

        // 1. 輸出端 adapter：整個專案只有這裡決定用 CSV 還是 SQL
        MemberRepository members;
        BookRepository books;
        LoanRepository loans;
        UnitOfWork uow;
        if ("sql".equals(storage)) {
            DataSource dataSource = DataSources.create(config);          // 只有 sql 模式才建立連線
            members = new SqlMemberRepository(dataSource);
            books = new SqlBookRepository(dataSource);
            loans = new SqlLoanRepository(dataSource);
            uow = new JdbcUnitOfWork(dataSource);
        } else {
            CsvStore store = new CsvStore(dataDir, Charset.forName(config.getProperty("csv.encoding", "UTF-8")));
            members = new CsvMemberRepository(store);
            books = new CsvBookRepository(store);
            loans = new CsvLoanRepository(store);
            uow = new CsvUnitOfWork(dataDir);
        }
        Clock clock = new SystemClock();
        Notifier notifier = new SmtpNotifier(MailSenders.create(config));

        // 2. use case
        BorrowBook borrowBook = new BorrowBook(members, books, loans, clock, notifier, uow);
        ReturnBook returnBook = new ReturnBook(books, loans, clock, uow);

        // 3. 輸入端 adapter（同一組 use case，可以接多個入口）
        this.loanWebAdapter = new LoanWebAdapter(borrowBook);
        this.adminCli = new AdminCli(returnBook);
    }

    public LoanWebAdapter getLoanWebAdapter() { return loanWebAdapter; }
    public AdminCli getAdminCli() { return adminCli; }
}
```

### 在哪裡建立組裝類別

組裝邏輯都在 `LendingModule`，依專案使用的框架決定由誰建立它：

| 專案使用 | 在哪裡建立 `LendingModule` |
|---|---|
| 無框架 / CLI | `main` 方法 |
| Servlet | `ServletContextListener.contextInitialized()`，把 adapter 放進 `ServletContext` |
| Spring（Java Config） | `@Configuration` 類別中，用 `@Bean` 方法呼叫同樣的建構子；可用 `@Profile("csv")` / `@Profile("sql")` 取代 `if` |
| Spring（XML） | `<bean>` 加 `<constructor-arg ref="...">`；可用 `<beans profile="csv">` 切換 |
| Guice 或其他 DI | 在該框架的 Module 中綁定 |

```java
// FILE: <main>/Main.java                          （無框架 / CLI）
public class Main {
    public static void main(String[] args) throws IOException {
        Properties config = new Properties();
        InputStream in = new FileInputStream("config/application.properties");
        try {
            config.load(in);
        } finally {
            in.close();
        }
        LendingModule lending = new LendingModule(config);
        lending.getAdminCli().run(args);
    }
}

// FILE: <main>/AppContextListener.java            （Servlet）
public class AppContextListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        Properties config = loadConfig(event.getServletContext());
        LendingModule lending = new LendingModule(config);
        event.getServletContext().setAttribute("loanWebAdapter", lending.getLoanWebAdapter());
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) { }

    // loadConfig 省略
}
```

使用 Spring 的專案，寫法見 [languages/java.md 的「使用 Spring 時」](../languages/java.md#使用-spring-時)。
不論用哪一種，**use case 與 entity 類別上都不加任何框架註解**。

by-feature 時，模組之間的依賴也在組裝時接上：

```java
// FILE: <main>/LibraryApplication.java    （節錄）
MembershipModule membership = new MembershipModule(config);
LendingModule lending = new LendingModule(config, membership.getMembershipApi());   // 傳入對方的公開 API
// LendingModule 內部：new MembershipBorrowerLookup(membershipApi)
```

## 與其他層的配合

```
               ┌──────────────── <main>（Composition Root）────────────────┐
  設定檔 ──────▶│ 1. 建立連線、client                                        │
               │ 2. new Driven Adapters(連線)        ──┐                     │
               │ 3. new Use Cases(adapters 當作 port) ◀─┘──┐                 │
               │ 4. new Driving Adapters(use cases)  ◀────┘──┐              │
               │ 5. 交給框架 / main 啟動 ──▶ Driving Adapters ◀┘             │
               └──────────────────────────────────────────────────────────┘
       執行期：請求 ──▶ 框架 ──▶ 輸入端 adapter ──▶ Use Case ──▶ Entity / Port ──▶ Adapter
```

| 對象 | 關係 | 傳什麼 |
|---|---|---|
| Driven Adapters | **我建立它**，並注入連線、client、設定值 | `DataSource`、`CsvStore`、寄信 client |
| Use Cases | **我建立它**，注入 port 的實作 | adapter 實例（以 port 型別傳入） |
| Driving Adapters | **我建立它**，注入 use case；交給框架或 `main` | use case 實例 |
| Entities | **不接觸** | — |
| 我依賴誰 | 所有層（這是唯一允許的地方） | — |

**關鍵**：這是整個系統**唯一知道所有具體實作**的地方。換資料庫、換框架、加新入口，都只在這裡改接線。

## 測試

- 不寫單元測試；用**端到端測試 / smoke test** 確認接線正確：啟動 → 打一個 API → 檢查結果
- `storage=csv` 與 `storage=sql` 各跑一次主要路徑（new-feature Step 8、Step 9）

## 自我檢查

- [ ] 全專案只有這一層出現 `new XxxAdapter(...)` / `new SystemClock()`
- [ ] 用 CSV 還是 SQL 的判斷只出現在這裡
- [ ] 沒有業務邏輯；設定只在這裡讀
- [ ] 換儲存方式只需要改設定值
- [ ] 使用 DI 框架時，內層類別上沒有框架註解
