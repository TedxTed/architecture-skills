---
name: clean-architecture
description: 以 Clean Architecture（乾淨架構）設計、實作、重構與審查 Java 程式碼（相容 JDK 1.7 / 1.8）。提供分層規則、各層寫法與範例、資料夾結構、DTO / 錯誤 / 交易的處理方式、CSV 優先的新功能流程、首次專案訪談、專案記憶與審查報告格式。當使用者要求「用乾淨架構」「分層」「解耦框架」「新增功能 / use case」「重構舊程式」或「審查架構」時使用。
---

# Clean Architecture

## 概述

這個 skill 讓你用 Robert C. Martin 的 Clean Architecture 寫出**業務規則不依賴框架、資料庫與 UI** 的程式碼。

它提供三件事：

- **規則**：哪些依賴不可以出現、程式碼該放哪一層
- **做法**：新增功能、換資料庫、重構舊程式、審查程式碼的逐步流程
- **範例**：可以直接照抄的 Java 程式碼

範例使用 **Java，而且只用 JDK 1.7 能編譯的語法**：不用 lambda、stream、`Optional`、`record`、`var`。
日期用 `java.time` 的寫法；Java 7 專案改 import ThreeTen Backport（`org.threeten.bp`），程式碼不用改。

範例領域是**圖書借閱**：會員（`Member`）借書（`Book`），產生借閱紀錄（`Loan`）。規則如下：

| 編號 | 規則 |
|---|---|
| R1 | 停權會員不能借書 |
| R2 | 一般會員最多借 3 本，VIP 最多 5 本 |
| R3 | 有逾期未還的書時，不能再借 |
| R4 | 書必須是可借狀態 |
| R5 | 借期 14 天 |
| R6 | 逾期歸還，每天罰 10 元 |
| R7 | 可續借 1 次；已逾期不能續借 |

## 何時使用

- 開發業務規則多、會長期維護的功能
- 同一個功能要支援多種入口（HTTP、CLI、排程、Message Queue）
- 將來可能要換資料庫、框架或外部服務
- 重構「controller 裡塞滿 SQL 與業務判斷」的舊程式
- 審查程式碼是否違反分層與依賴方向

## 何時不要用

- 純 CRUD、幾乎沒有業務規則的功能
- 原型、一次性腳本

遇到這些情況，先告訴使用者這個取捨，由使用者決定要不要套用。

---

## 開始之前：讀取順序

這個檔案很長，請依情況只讀需要的部分：

1. **專案已有 `docs/architecture/card.md`**：只讀 `card.md` 與 `conventions.md`，照上面寫的做。不用讀本檔其餘部分。
2. **專案還沒有**：先做「工作流程 A：首次設定」。**先問使用者，不要掃描原始碼去推理。**
3. **只需要某一部分**：先搜尋 `^## ` 與 `^### ` 取得章節列表，只讀需要的章節。（「專案記憶」一節的 `## C-001`、`## DEC-004` 是條目格式範例，不是章節。）
4. **每個任務結束前**：做「工作流程 F：收尾記錄」。

---

## 核心概念

### 依賴規則

**原始碼的依賴只能由外往內。內層永遠不知道外層的存在。**

```
┌──────────────────────────────────────────────┐
│ Frameworks & Drivers：Spring 設定、資料庫連線   │
│  ┌────────────────────────────────────────┐  │
│  │ Interface Adapters：Controller、Repository │  │
│  │  ┌──────────────────────────────────┐  │  │
│  │  │ Use Cases：系統的操作流程          │  │  │
│  │  │  ┌────────────────────────────┐  │  │  │
│  │  │  │ Entities：業務規則          │  │  │  │
│  │  │  └────────────────────────────┘  │  │  │
│  │  └──────────────────────────────────┘  │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
                 依賴方向：外 → 內
```

內層需要呼叫外層時（例如 use case 要存資料），用**依賴反轉**：

1. 內層定義介面（**Port**），例如 `LoanRepository`
2. 外層實作介面（**Adapter**），例如 `SqlLoanRepository implements LoanRepository`
3. 在組裝的地方把 adapter 注入 use case

執行時是 use case 呼叫 adapter；但原始碼的依賴是 adapter 指向 port。這就是整個架構的關鍵。

### 四層

| 層 | Java package | 放什麼 | 一句話判斷 |
|---|---|---|---|
| Entities | `domain` | Entity、Value、領域錯誤 | 沒有電腦，櫃台人員也照做的規則 |
| Use Cases | `application` | Use case、Port、DTO | 系統執行一個操作的步驟 |
| Interface Adapters | `adapter` | Controller、Repository 實作 | 外部格式與內部格式的轉換 |
| Frameworks & Drivers | `config`、`infrastructure` | Spring 設定、啟動程式 | 換掉它，業務不應該知道 |

### 五條硬規則

任何時候都不可以違反：

1. **依賴只能由外往內**：`config → adapter → application → domain`。
2. **domain 與 application 不 import 框架**：Spring、JPA、Servlet、JDBC、Jackson 都不行。
3. **規則放 Entity，流程放 Use Case，轉換放 Adapter。**
4. **需要外部能力時，內層定義 Port，外層實作 Adapter。** 時間、ID、亂數也算外部能力。
5. **物件只在 Composition Root 組裝。** Composition Root 就是 Spring 的 `@Configuration` 或 `main` 方法。use case 不加 `@Service`。

### 程式碼該放哪

依序回答，第一個答「是」的就是答案：

1. 有 import 框架，或在讀設定檔？ → `adapter` 或 `config`
2. 在建立物件、把它們接起來？ → `config`
3. 在做格式轉換（JSON、資料表欄位、HTTP 狀態碼）？ → `adapter`
4. 沒有電腦，櫃台人員也會遵守這條規則？ → `domain`
5. 在描述「先做什麼、再做什麼」？ → `application` 的 use case
6. 是內層需要、但由外部提供的能力？ → 介面放 `application`，實作放 `adapter`

---

## 各層寫法

### 第 1 層：Entities（`domain`）

**職責**：表達業務本身的規則。

**要做**

- 規則寫成 entity 的方法，用業務動詞命名，例如 `markAsLent()`、`renew()`
- 時間等外部值用參數傳入，例如 `renew(now)`
- 違反規則時丟出領域錯誤（unchecked 例外，帶錯誤碼）
- 提供兩種建立方式：`open()` 新建時檢查規則；`reconstitute()` 從資料庫還原時不檢查

**不要做**

- 在 entity 上加 `@Entity`、`@Column`、`@JsonProperty`
- 呼叫 `LocalDateTime.now()`、`new Date()`、`UUID.randomUUID()`
- 用 setter 讓外面直接改狀態

**範例**

```java
// FILE: <domain>/DomainException.java
public abstract class DomainException extends RuntimeException {
    private final String code;

    protected DomainException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() { return code; }
}

// FILE: <domain>/BookNotAvailableException.java    （其他領域錯誤寫法相同）
public class BookNotAvailableException extends DomainException {
    public BookNotAvailableException() { super("BOOK_NOT_AVAILABLE"); }
}
```

```java
// FILE: <domain>/Loan.java
public class Loan {
    private static final int LOAN_DAYS = 14;          // R5
    private static final long FINE_PER_DAY = 10;      // R6

    private final LoanId id;
    private final MemberId memberId;
    private final BookId bookId;
    private LocalDate dueDate;
    private LocalDateTime returnedAt;                 // null 代表尚未歸還
    private int renewCount;

    // 新建：套用規則
    public static Loan open(LoanId id, MemberId memberId, BookId bookId, LocalDateTime now) {
        return new Loan(id, memberId, bookId, now.toLocalDate().plusDays(LOAN_DAYS), null, 0);
    }

    // 還原：給 repository 用，不檢查規則
    public static Loan reconstitute(LoanId id, MemberId memberId, BookId bookId,
                                    LocalDate dueDate, LocalDateTime returnedAt, int renewCount) {
        return new Loan(id, memberId, bookId, dueDate, returnedAt, renewCount);
    }

    public boolean isOverdue(LocalDateTime now) {
        return returnedAt == null && now.toLocalDate().isAfter(dueDate);
    }

    // 歸還，回傳罰金
    public long close(LocalDateTime now) {
        if (returnedAt != null) {
            throw new LoanAlreadyClosedException();
        }
        returnedAt = now;
        long lateDays = ChronoUnit.DAYS.between(dueDate, now.toLocalDate());
        return Math.max(0, lateDays) * FINE_PER_DAY;
    }

    public void renew(LocalDateTime now) {            // R7
        if (isOverdue(now)) {
            throw new CannotRenewOverdueLoanException();
        }
        if (renewCount >= 1) {
            throw new RenewLimitExceededException();
        }
        dueDate = dueDate.plusDays(LOAN_DAYS);
        renewCount++;
    }

    // private 建構子與 getter 省略
}
```

```java
// FILE: <domain>/Member.java
public class Member {
    // 欄位：id、name、email、tier（REGULAR / VIP）、suspended；建構子與 getter 省略

    public int loanLimit() {
        return tier == MemberTier.VIP ? 5 : 3;                        // R2
    }

    // 規則需要其他 entity 的資料時，由參數傳入
    public void assertCanBorrow(List<Loan> openLoans, LocalDateTime now) {
        if (suspended) {
            throw new MemberSuspendedException();                     // R1
        }
        if (openLoans.size() >= loanLimit()) {
            throw new LoanLimitExceededException();                   // R2
        }
        for (Loan loan : openLoans) {
            if (loan.isOverdue(now)) {
                throw new HasOverdueLoansException();                 // R3
            }
        }
    }
}
```

`MemberId`、`BookId`、`LoanId` 是 Value：`final class`、`final` 欄位、建構子檢查不可為空、覆寫 `equals` 與 `hashCode`。

**Aggregate**：一群必須一起保持一致的 entity，外部只能透過其中的 root 存取。一個 aggregate 對應一個 repository；aggregate 之間只用 id 參照。

**檢查問題**

- `domain` 裡有沒有任何 import 指向外層或框架？
- 每條業務規則是不是都在 entity 方法裡，而不是在 use case 的 `if`？
- 測試是不是不需要任何 mock 就能跑？

### 第 2 層：Use Cases（`application`）

**職責**：編排「系統執行一個操作」的步驟。它決定先做什麼、再做什麼，但**不判斷業務規則**。

**標準六步**

1. 把輸入轉成 Value（建構子會檢查格式）
2. 透過 port 載入 entity；找不到就丟應用錯誤
3. 呼叫 entity 的方法執行規則
4. 透過 port 儲存；改到多個 entity 時包在 `UnitOfWork` 裡
5. 交易成功後才做副作用，例如寄信
6. 回傳 Output DTO，不回傳 entity

**要做**

- 一個操作一個類別，例如 `BorrowBook`、`ReturnBook`
- 依賴透過建構子注入
- 需要的外部能力在本層定義成 port

**不要做**

- 寫 `if (openLoans.size() >= 3)` 這種業務判斷（這是 entity 的事）
- import adapter、Spring、JDBC
- 加 `@Service`、`@Transactional`
- 回傳 entity 或 `ResponseEntity`

**範例**

```java
// FILE: <application>/port/LoanRepository.java
public interface LoanRepository {
    Loan findById(LoanId id);                         // 找不到回傳 null
    List<Loan> findOpenByMember(MemberId memberId);
    void save(Loan loan);
    LoanId nextId();
}

// 其他 port
public interface Clock { LocalDateTime now(); }
public interface UnitOfWork { void run(Runnable work); }         // work 丟例外時全部 rollback
public interface Notifier { void notifyBookBorrowed(Recipient to, String bookTitle, LocalDate dueDate); }
// Recipient 是 DTO（email、name）：adapter 需要的資料由 use case 準備好傳入
```

```java
// FILE: <application>/usecase/borrowbook/BorrowBook.java
public class BorrowBook {
    private final MemberRepository members;
    private final BookRepository books;
    private final LoanRepository loans;
    private final Clock clock;
    private final Notifier notifier;
    private final UnitOfWork uow;

    // 建構子注入省略

    public BorrowBookOutput execute(BorrowBookInput input) {
        // 1. 輸入轉成 Value
        MemberId memberId = new MemberId(input.getMemberId());
        BookId bookId = new BookId(input.getBookId());
        LocalDateTime now = clock.now();

        // 2. 載入
        Member member = members.findById(memberId);
        if (member == null) {
            throw new AppException("MEMBER_NOT_FOUND");
        }
        final Book book = books.findById(bookId);      // Java 7：匿名類別用到的變數要宣告 final
        if (book == null) {
            throw new AppException("BOOK_NOT_FOUND");
        }

        // 3. 業務規則交給 entity
        member.assertCanBorrow(loans.findOpenByMember(memberId), now);
        book.markAsLent();
        final Loan loan = Loan.open(loans.nextId(), memberId, bookId, now);

        // 4. 同一個交易儲存
        uow.run(new Runnable() {
            @Override
            public void run() {
                books.save(book);
                loans.save(loan);
            }
        });

        // 5. 交易成功後才寄信
        notifier.notifyBookBorrowed(new Recipient(member.getEmail(), member.getName()),
                book.getTitle(), loan.getDueDate());

        // 6. 回傳 DTO
        return new BorrowBookOutput(loan.getId().getValue(), loan.getDueDate());
    }
}
```

`BorrowBookInput`、`BorrowBookOutput` 是 `final class`，只有 `final` 欄位、建構子與 getter。

**查詢**：只讀資料時可以跳過 entity，用查詢 port 直接回傳畫面需要的 DTO。多個沒有規則的查詢，可以合併成一個查詢類別的多個方法；寫入型的操作仍然一個類別一個操作。

**檢查問題**

- 每個 use case 是不是只負責一個操作？
- `if` 是不是只用在「找不到」這類流程判斷？
- 回傳的是 DTO 嗎？
- 測試是不是只用 in-memory 的假實作，不需要資料庫？

### 第 3 層：Interface Adapters（`adapter`）

**職責**：翻譯。把外部格式（HTTP、資料表、第三方 API）轉成內層看得懂的，再把結果轉回去。

Adapter 分成兩個方向：

| 方向 | 意思 | 例子 |
|---|---|---|
| Driving（輸入端） | 外界呼叫 use case | `LoanController`、`AdminCli`、MQ listener |
| Driven（輸出端） | 實作 use case 定義的 port | `SqlLoanRepository`、`CsvLoanRepository`、`SmtpNotifier` |

**要做**

- 只做三件事：轉換格式、呼叫 use case、翻譯錯誤
- Controller 一個請求只呼叫一個 use case
- Repository 回傳 entity，不回傳資料表的 row
- 錯誤對應（領域錯誤 → HTTP 狀態碼）集中在一個地方
- 外部例外（`SQLException`、SDK 例外）在這裡處理掉，不往內丟

**不要做**

- 寫業務判斷，例如 `if (member.getTier() == VIP)`
- 在 controller 裡依序呼叫好幾個 use case（那應該是一個新的 use case）
- 讓 adapter 注入別的 port 自己去查資料（需要的資料由 use case 傳入）

**範例**

```java
// FILE: <adapters>/web/LoanController.java      （Spring 4 MVC，支援 Java 7）
@RestController
@RequestMapping("/api/loans")
public class LoanController {
    private final BorrowBook borrowBook;

    @Autowired      // Spring 4.3 起，只有一個建構子時可以省略
    public LoanController(BorrowBook borrowBook) {
        this.borrowBook = borrowBook;
    }

    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<BorrowResponse> borrow(@Valid @RequestBody BorrowRequest req) {
        BorrowBookOutput out = borrowBook.execute(
                new BorrowBookInput(req.getMemberId(), req.getBookId()));
        BorrowResponse body = new BorrowResponse(out.getLoanId(), out.getDueDate().toString());
        return new ResponseEntity<BorrowResponse>(body, HttpStatus.CREATED);
    }
}
```

```java
// FILE: <adapters>/web/ErrorMapping.java        （錯誤對應集中在這裡）
@ControllerAdvice
public class ErrorMapping {
    private static final Map<String, HttpStatus> STATUS = new HashMap<String, HttpStatus>();
    static {
        STATUS.put("MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND);
        STATUS.put("BOOK_NOT_AVAILABLE", HttpStatus.CONFLICT);
        STATUS.put("LOAN_LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, String>> domain(DomainException e) {
        return toResponse(e.getCode());
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, String>> app(AppException e) {
        return toResponse(e.getCode());
    }

    // 其他 Exception：記 log，回 500 INTERNAL_ERROR（省略）

    private ResponseEntity<Map<String, String>> toResponse(String code) {
        Map<String, String> body = new HashMap<String, String>();
        body.put("error", code);
        HttpStatus status = STATUS.containsKey(code) ? STATUS.get(code) : HttpStatus.BAD_REQUEST;
        return new ResponseEntity<Map<String, String>>(body, status);
    }
}
```

**檢查問題**

- Controller 有沒有業務判斷？是不是只呼叫一個 use case？
- Repository 回傳的是 entity 嗎？
- JPA 類別、資料表的 row、第三方 SDK 的型別有沒有跑進內層？

### 第 4 層：Frameworks & Drivers（`config`）

**職責**：把所有物件建立起來、接在一起，然後啟動。這裡是**唯一知道所有具體實作**的地方。

**做法**

1. 讀設定
2. 建立輸出端 adapter（repository、notifier）
3. 建立 use case，把 adapter 當成 port 注入
4. Controller 由 Spring 掃描後自動注入 use case

儲存方式用 Spring profile 切換（Spring 3.1 起支援）。這樣在 CSV 模式下，沒有設定資料庫也能啟動。

```java
// FILE: <main>/CsvStorageConfig.java       （application.properties：spring.profiles.active=csv）
@Configuration
@Profile("csv")
public class CsvStorageConfig {
    @Value("${data.dir:./data}")
    private String dataDir;

    @Bean
    public CsvStore csvStore() {
        return new CsvStore(new File(dataDir), Charset.forName("UTF-8"));
    }

    @Bean
    public LoanRepository loanRepository(CsvStore store) {
        return new CsvLoanRepository(store);
    }
}
// SqlStorageConfig 寫法相同，加上 @Profile("sql")，改建立 SqlLoanRepository
```

```java
// FILE: <main>/LendingConfig.java
@Configuration
public class LendingConfig {

    @Bean
    public Clock clock() {
        return new SystemClock();
    }

    @Bean
    public BorrowBook borrowBook(MemberRepository members, BookRepository books, LoanRepository loans,
                                 Clock clock, Notifier notifier, UnitOfWork uow) {
        return new BorrowBook(members, books, loans, clock, notifier, uow);   // use case 本身沒有 Spring 註解
    }
}
```

還在用 XML 設定的舊專案：use case 是一般 Java 類別，用 `<bean>` 加 `<constructor-arg ref="...">` 組裝，用 `<beans profile="csv">` 切換儲存方式。

**檢查問題**

- 全專案是不是只有這一層在 `new` adapter？
- use case 類別上有沒有 Spring 註解？

---

## 跨越邊界：DTO、錯誤、交易

### DTO 放哪

DTO 是只裝資料、沒有業務方法的物件。判斷原則：**誰的方法簽章用到這個型別，型別就屬於誰。**

| DTO | 放在 | 用途 |
|---|---|---|
| `BorrowRequest`、`BorrowResponse` | `adapter.web` | API 的 JSON 格式；驗證註解只能放這裡 |
| `BorrowBookInput`、`BorrowBookOutput` | `application.usecase` | use case 的輸入與輸出 |
| `LoanSummary` | `application.port` | 查詢結果 |
| 資料表的 row、JPA 類別 | `adapter.persistence` | 儲存格式，不能離開 adapter |

常見疑問：「DTO 不是應該放在 controller 那一層嗎？」API 的 JSON 格式是；但 use case 的 Input / Output 不是。因為 use case 的方法簽章用到它們，如果放在 `adapter`，就變成 application import adapter，違反硬規則 1。

規則：

- 每個 use case 有自己的 Input / Output，不要一個 `LoanDto` 到處共用
- 想只維護一套時，可以只留 Input / Output 讓 controller 直接用；反過來不行
- PATCH（局部更新）要分辨「沒傳」和「傳了 null」：Java 7 用自訂的 `Patch<T>` 類別

### 錯誤

| 種類 | 定義在 | 例子 |
|---|---|---|
| 領域錯誤 | `domain` | `BookNotAvailableException` |
| 應用錯誤 | `application` | `AppException("MEMBER_NOT_FOUND")` |
| 技術錯誤 | 不定義，由 adapter 包裝 | `SQLException` |

全部用 unchecked 例外，只在最外層（`@ControllerAdvice`）轉成 HTTP 狀態碼一次。

### 交易

- Use case 透過 `UnitOfWork` port 開交易
- Spring 的實作用 `TransactionTemplate`
- `@Transactional` 只能放在 adapter，不能放在 use case
- 寄信等副作用放在交易成功之後；需要保證一定送達時用 Outbox（交易內寫入 outbox 表，另一個程序負責發送）

**競態條件**：use case 在交易外先讀資料再檢查，兩個人同時借同一本書，可能都通過檢查。解法都在 adapter 裡：

- **條件式更新**（推薦）：`UPDATE books SET status='ON_LOAN' WHERE id=? AND status='AVAILABLE'`，影響 0 列就丟 `BookNotAvailableException`
- **樂觀鎖**：JPA 類別加 `@Version`
- **悲觀鎖**：`SELECT ... FOR UPDATE`
- **序列化**：同一時間只執行一個寫入（只適合單一程序）

---

## 資料夾結構

兩種都符合 Clean Architecture，依專案選擇。`{base}` 代表 base package，例如 `com.example.library`。

**A. by-layer**：專案頂層按層切。適合單一業務領域的小專案。

```
{base}/domain/
{base}/application/port/
{base}/application/usecase/borrowbook/
{base}/adapter/web/
{base}/adapter/persistence/csv/
{base}/adapter/persistence/sql/
{base}/config/
```

**B. by-feature**：頂層按業務模組切，模組內再分層。適合多個業務領域、多人協作。

```
{base}/lending/api/            ← 模組對外公開的介面與 DTO
{base}/lending/domain/
{base}/lending/application/
{base}/lending/adapter/
{base}/lending/config/
{base}/catalog/ ...
{base}/sharedkernel/           ← 各模組共用的 port（Clock 等）
{base}/infrastructure/         ← 共用的 adapter、啟動類別
```

by-feature 的規則：

- **模組是業務領域**（lending、catalog），不是單一操作（borrowbook）。共用同一組 entity 的 use case 放同一個模組。
- **模組之間只能透過對方的 `api` package 溝通。**
- **需要其他模組的資料時，當成呼叫外部系統**：在自己模組定義 port（例如 `BorrowerLookup`），在 `adapter/integration` 實作，把對方的資料翻譯成自己的模型。
- 不跨模組 join 資料表；`sharedkernel` 保持極小。

不確定時選 B。使用者有偏好或專案已有結構時，照使用者的，並把差異記入 `conventions.md`。

---

## 工作流程

### A. 首次設定

**用問的，取代用推理的。** 只在專案還沒有 `docs/architecture/` 時做一次。

**步驟 1：低成本偵測（不打開原始碼）**

只讀這些：

- `pom.xml` 或 `build.gradle`：JDK 版本、Spring 版本、原始碼編碼
- `src/main/java` 往下到 base package 再兩層的資料夾名稱
- 既有的 `CLAUDE.md`、`AGENTS.md`、`.editorconfig`

**步驟 2：一輪問完**

- 最多 12 題，每題附選項與預設值
- 偵測到的寫成「請確認」，不要重新問
- 使用者可以回「全部預設」，或只回要改的題號

```
開始之前想確認幾件事。答案會記在 docs/architecture/，之後不會再問。
可以回「全部預設」，或只回要改的題號（例如「3A、11B」）。

【已偵測，請確認】Java 1.7 / Spring 4.3 MVC / Maven；base package：com.example.library；原始碼編碼：MS950

1. 專案狀態？      A. 新專案（預設）  B. 既有且已分層  C. 既有未分層，需要重構
2. 專案形態？      A. 純後端 API（預設）  B. 全端  C. 純前端  D. CLI / 批次 / MQ
3. 資料夾結構？    A. by-layer  B. by-feature（預設）
4. 模組放哪？      A. com.example.library.<模組>（預設）  B. ...modules.<模組>
5. 有哪些業務模組？
6. 新功能儲存？    A. 先用 CSV，確認後再接資料庫（預設）  B. 直接接資料庫
7. 正式資料庫？    A. Oracle  B. MySQL  C. PostgreSQL  D. 還沒決定（預設）
8. 原始碼編碼？    A. UTF-8（預設）  B. Big5 / MS950
9. CSV 編碼？      A. 同原始碼（預設）  B. UTF-8 with BOM  C. Big5
10. 錯誤處理？     A. unchecked 例外（預設）  B. Result 型別
11. 測試？         A. 寫自動化測試（預設）  B. 不寫，改用手動驗證
12. 其他慣例，或「不要這樣做」的事？
```

**步驟 3：建立專案記憶**

在 `docs/architecture/` 建立五個檔案（內容見「專案記憶」一節），並在 `CLAUDE.md` 或 `AGENTS.md` 加一行：

```
架構相關任務先讀 docs/architecture/card.md 與 conventions.md；卡上沒寫到才查 skill。
```

**步驟 4：回到使用者原本的任務。**

### B. 新增功能（CSV 優先）

**新功能的儲存先用 CSV 跑通，使用者確認行為正確後才接資料庫。**
原因：CSV 沒有 JOIN、沒有 ORM。CSV 寫得出來，就代表 port 沒有偷偷依賴資料庫。

| 步驟 | 做什麼 | 完成的標準 |
|---|---|---|
| 0 | 列出規則；不清楚的一次問完，附預設值 | 每條規則都知道放哪一層 |
| 1 | 規則寫成 entity 方法，寫單元測試 | `domain` 沒有外層 import |
| 2 | 確認需要的 port；沿用既有的，必要時才加方法 | port 用業務語言命名 |
| 3 | 寫 use case 與 Input / Output | 沒有業務 `if`，回傳 DTO |
| 4 | 用 in-memory 假實作測 use case | 此時還沒有任何 adapter |
| 5 | 寫 CSV 版的 repository | 通過 contract test |
| 6 | 寫 controller 與錯誤對應 | controller 只呼叫一個 use case |
| 7 | 在 `@Configuration` 組裝 | 只有 config 在 `new` adapter |
| 8 | **用 CSV 跑通，交給使用者確認** | 成功路徑與每種錯誤都打過 |
| 9 | 使用者同意後才寫 SQL 版 repository | domain 與 application 一行都不用改 |
| 10 | 收尾記錄（工作流程 F） | — |

使用者選擇不寫自動化測試時：跳過寫測試的步驟，在步驟 8 改做「編譯 + 依賴方向檢查 + 手動打 API」，並在交付時列出驗證過的情境。

**CSV 的約定**

- 編碼依專案設定（預設 UTF-8；要給 Excel 開就用 UTF-8 with BOM）
- 第一列是欄位名稱，一個檔案存一種 entity
- 日期用 ISO 格式，空字串代表 null，金額用 `long`
- `data/` 放執行時的資料（不進版控），`data-seed/` 放範例資料（進版控）

**CsvStore 的重點**（用 Apache Commons CSV）：

- 讀取用 CSV 函式庫解析，不要自己 `split(",")`
- 更新時**原地取代**那一列；「先刪掉再加到最後」會打亂列的順序
- 寫入時先寫到 `.tmp`，再用 `Files.move(..., ATOMIC_MOVE)` 取代原檔
- 編碼器設定 `CodingErrorAction.REPORT`，遇到存不了的字元要報錯，不能變成 `?`

**Contract test**：寫一個抽象測試類別 `LoanRepositoryContract`，InMemory、CSV、SQL 三種 repository 各寫一個子類別，跑同一套測試。

可以跳過 CSV 的情況：全文搜尋、地理查詢、大量報表這類本質依賴資料庫的功能，或使用者明確要求。跳過前先告知，並記入 `conventions.md`。

### C. 換資料庫、加外部服務、加入口

**換資料庫**

1. port 不動
2. 新增 `SqlLoanRepository`（用 `JdbcTemplate`；JPA 或 MyBatis 的類別只放在 adapter）
3. 跑同一套 contract test
4. 新增 `@Profile("sql")` 的設定，切換 profile

CSV 版建議保留，給本機開發用。

**加外部服務（例如再加 LINE 通知）**

- port 已經描述「要通知」，就不用改 port
- 同時發 email 與 LINE：寫一個 `CompositeNotifier` 把兩者組合起來
- 新管道需要新資料（例如 `lineUserId`）：擴充 `Recipient`，由 use case 填入。**不要讓 adapter 自己去查。**

**加入口（CLI、Message Queue）**：重用同一個 use case，不要複製邏輯。

### D. 重構舊程式

**一次只搬一個功能，每一步都能回退。**

1. **鎖定行為**：先寫特徵測試，或用 curl 記下每個情境的請求與回應
2. **標記**：在原始碼註記每一行屬於哪一層（這一步不改程式）
3. **抽 Domain**：把業務規則搬進 entity 方法（風險最低、收益最高）
4. **抽 Port 與 Adapter**：SQL 搬進 repository；`new Date()` 換成 Clock；`@Transactional` 換成 UnitOfWork
5. **抽 Use Case**：controller 只剩「解析 → 呼叫 → 轉回應」；寄信移到交易後
6. **搬到 Composition Root**：改用 `@Configuration` 組裝，加上依賴方向檢查

安全網：

- 先用 Facade 把舊 service 整個包在 port 後面
- 新舊兩條路徑用設定開關切換，驗證後才刪舊路徑（開關記入 `debt.md`）
- 一次只把一個端點換成新的 use case
- 優先挑「常改、但出錯時影響範圍小」的功能

發現疑似 bug 時，先記錄下來，不要順手修。

### E. 審查程式碼

依「檢查清單」一節逐項檢查，用下面的格式回報：

```markdown
**Clean Architecture 審查報告**

**摘要**
- 整體評估：良好 / 需要改善 / 嚴重
- 主要發現（3 行以內）

**依賴規則**
- 違規：檔案:行號 —— 違反哪一條 —— 建議修法

**各層**
- Entities：良好 / 需要改善 —— 說明
- Use Cases：良好 / 需要改善 —— 說明
- Interface Adapters：良好 / 需要改善 —— 說明
- Frameworks & Drivers：良好 / 需要改善 —— 說明

**建議（依優先順序）**
1. 必須修：違反依賴規則的地方
2. 應該修：職責放錯的地方
3. 可以改善：測試、命名、自動檢查

**做得好的地方**
```

既有的違規如果使用者沒要求修，記入 `debt.md`。

### F. 收尾記錄

**每個任務結束前做。** 這次推理、搜尋、試錯得到的結論，寫下來給下次用，就不必再花 token 重想一次。

| 這次任務中…… | 記到 |
|---|---|
| 新增或修改了 entity、port 方法、use case、adapter | `map.md`，每項一行 |
| 做了需要推理的判斷、調查出結論、試過但失敗的做法 | `decisions.md` |
| 試出可以用的指令 | `card.md` 的「常用指令」 |
| 發現專案慣例與本 skill 不同，或被使用者糾正 | `conventions.md` |
| 發現違反硬規則的既有程式碼 | `debt.md` |

原則：

- 只記結論，不記推理過程
- 一次搜尋就找得到的事不記
- 只出現一次的寫法不算慣例；兩種寫法矛盾時先問使用者
- 不記密碼、連線字串
- 記錄與程式碼不符時，以程式碼為準並修正記錄
- 最後用一行告訴使用者記了什麼，例如「已更新記憶：map（RenewLoan）、DEC-004」

沒有新東西就跳過。

---

## 專案記憶

首次設定時在專案建立 `docs/architecture/`。**這些檔案一律存成 UTF-8**，即使專案是 Big5。

| 檔案 | 內容 | 何時讀 |
|---|---|---|
| `card.md` | 專案速查表：形態、語言、路徑、新功能步驟、常用指令 | 每次 |
| `conventions.md` | 與本 skill 不同的慣例，照做 | 每次 |
| `map.md` | 每個 entity、port、use case、adapter 在哪 | 改程式前，取代掃描原始碼 |
| `decisions.md` | 推理過的結論 | 只搜尋標題，命中才讀 |
| `debt.md` | 違反硬規則的既有程式碼，不仿照、不擅自修 | 審查或重構時 |

優先順序：**使用者當下的指示 > conventions.md > card.md > 本 skill**。硬規則不能被推翻。

條目格式：

```markdown
## C-001 [路徑] 模組放在 modules package 下
- 預設：com.example.library.<模組>
- 本專案：com.example.library.modules.<模組>
- 依據：使用者訪談（2026-10-03）

## DEC-004 [lending][放置] 續借上限放在 Loan.renew()，不放 Member
- 理由：上限屬於單筆借閱的規則
- 否決：放在 use case（會變成貧血模型）
- 2026-10-03｜相關檔案：Loan.java
```

`card.md` 控制在 70 行以內；`conventions.md` 超過 80 行、`map.md` 超過 150 行時合併或拆檔。

---

## 特殊情況

**Domain Event（領域事件）**

只在「一件事發生後有多個無關的後續動作」或「要通知其他模組」時才用；小專案預設不用。

- 用過去式命名，例如 `BookBorrowed`
- 欄位只放基本型別；事件 id 與時間由參數傳入
- Entity 只記錄事件，use case 在交易成功後才發佈
- 需要保證送達時用 Outbox

**Log**

- 技術 log（請求、錯誤、逾時）寫在 adapter 與 config
- 業務稽核紀錄當成業務資料，透過 port 寫入
- domain 不寫 log

**前端（Vue / React）**

- 業務規則都在後端時，前端不必分層，只要把 API 呼叫集中在一個地方
- 前端有自己的規則時：domain 是純 TypeScript；元件與 store 是輸入端 adapter；API client 與 localStorage 是輸出端 adapter

**Big5 專案**

- 讀寫檔案用 MS950，而且要嚴格模式（存不了的字元要報錯）
- 不使用 emoji
- 修改既有檔案：先轉 UTF-8、修改、再轉回 MS950，用 `git diff` 確認只有預期的變動
- Java 編譯要指定 `-encoding MS950`，Maven 設定 `project.build.sourceEncoding`

**JDK 版本**

| 項目 | JDK 1.7（範例預設） | JDK 1.8 以上可以改用 |
|---|---|---|
| 找不到資料 | 回傳 `null` | `Optional` |
| 回呼 | 匿名類別 | lambda |
| 日期 | ThreeTen Backport | `java.time` |
| 依賴檢查 | Maven 多模組 | ArchUnit |

架構規則不因版本改變，改變的只有語法。選定後記入 `conventions.md`。

---

## 反模式

**貧血模型：規則寫在 use case**

```java
// ❌ 不好：Loan 只有 getter / setter，規則散在 use case
if (loan.getRenewCount() >= 1) throw new RenewLimitExceededException();
loan.setDueDate(loan.getDueDate().plusDays(14));

// ✅ 好：規則集中在 entity
loan.renew(clock.now());
```

**框架滲透：註解跑進內層**

```java
// ❌ 不好
@Entity
public class Loan { ... }

@Service
@Transactional
public class BorrowBook { @Autowired private LoanRepository loans; }

// ✅ 好：JPA 類別與 domain entity 分開；use case 用建構子注入，在 @Configuration 建立
public class BorrowBook {
    public BorrowBook(LoanRepository loans, ...) { ... }
}
```

**洩漏的 port：介面跟著資料庫走**

```java
// ❌ 不好
List<Map<String, Object>> query(String sql, Object... args);

// ✅ 好：用業務語言命名
List<Loan> findOpenByMember(MemberId memberId);
```

**Adapter 繞過 use case 查資料**

```java
// ❌ 不好：notifier 自己注入 MemberRepository 去查 email
public SmtpNotifier(MailSender mailSender, MemberRepository members) { ... }

// ✅ 好：use case 查好，用參數傳入
notifier.notifyBookBorrowed(new Recipient(member.getEmail(), member.getName()), ...);
```

其他常見反模式：

| 反模式 | 修法 |
|---|---|
| 肥胖 controller：有業務判斷、呼叫好幾個 use case | 規則搬到 entity，流程搬到 use case |
| 上帝 Service：一個 `LoanService` 有二十個方法 | 一個操作一個 use case 類別 |
| 回傳 entity 給 controller | 回傳 Output DTO |
| 隱藏的時間依賴：`new Date()` | 透過 Clock port，用參數傳入 |
| 萬用 DTO：一個 `LoanDto` 到處用 | 每個 use case 自己的 Input / Output |
| 跨模組共用 entity | 自己的模型 + 對方的 `api` + integration adapter |
| 交易內寄信 | 交易成功後才寄；需要保證時用 Outbox |

---

## 重要提醒

### 這不是萬靈丹

- 不是每個專案都需要剛好四層。只要依賴方向正確，層數可以彈性調整。
- 分層本身不是目的。目的是讓程式**更容易理解、修改、測試**。
- 小專案可以精簡：省略只有一個實作的輸入 port、省略 presenter、DTO 寫成 use case 的巢狀類別。**唯一不能省的是依賴方向。**

### 常見陷阱

1. **過度分層**：純 CRUD 也做滿四層，團隊會因此排斥架構
2. **過度抽象**：為了「以後可能會換」做一堆介面
3. **表面套用**：資料夾分好了，但 domain 裡還是 import 了 Spring
4. **一次重寫**：應該一次搬一個功能，每一步都能回退

### 審查時問自己

- 這個分離真的有帶來好處嗎？
- 依賴方向真的是由外往內嗎？
- 換掉資料庫或框架時，domain 與 application 需要改嗎？
- 這段程式碼，業務人員看得出它在做什麼嗎？

---

## 檢查清單

**必須修（違反依賴規則）**

- [ ] `domain` 與 `application` 沒有 import 外層或框架
- [ ] 內層的類別上沒有 `@Entity`、`@Column`、`@JsonProperty`、`@Service`、`@Autowired`
- [ ] use case 的參數與回傳值沒有 `HttpServletRequest`、`ResponseEntity`、JPA 類別
- [ ] 只有 config 在建立 adapter
- [ ] 模組之間只透過 `api` 溝通

**應該修（職責放錯）**

- [ ] 業務規則在 entity 裡
- [ ] entity 沒有呼叫 `now()`、`new Date()`
- [ ] 一個 use case 一個類別、一個公開方法
- [ ] use case 的 `if` 只做流程判斷
- [ ] use case 回傳 DTO，而且每個 use case 有自己的 DTO
- [ ] port 用業務語言命名
- [ ] 副作用在交易成功之後
- [ ] controller 只呼叫一個 use case
- [ ] 錯誤對應集中在一處，外部例外沒有外洩
- [ ] adapter 沒有注入別的 port 自己查資料

**建議**

- [ ] 時間與 ID 透過 port 取得
- [ ] domain 測試不需要 mock
- [ ] 有 repository contract test
- [ ] 有自動化的依賴方向檢查
- [ ] 命名沿用專案既有慣例
- [ ] 檔案編碼與 `card.md` 一致

**怎麼強制依賴方向**

- **JDK 1.7**：Maven 多模組（`library-domain`、`library-application`、`library-adapter`、`library-boot`）。內層的 pom 不宣告 Spring 與 JDBC，違反時直接編譯失敗。
- **JDK 1.8 以上**：ArchUnit。

```java
noClasses().that().resideInAnyPackage("..domain..", "..application..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("org.springframework..", "javax.persistence..");
```
