# 第 2 層：Use Cases（應用業務規則）

> 位置：`<application>`｜依賴：Entities｜被誰使用：Driving Adapters（controller / CLI）

## 職責

編排「**這套系統**」執行一個操作的步驟。像食譜：取材料（讀資料）→ 照規則處理（呼叫 entity）→ 存起來 → 通知。
**業務規則不在這裡判斷**，這裡只決定「先做什麼、再做什麼」。

## 遵循規則

| 必須 | 禁止 |
|---|---|
| 一個 use case 一個類別 / 函式，一個公開方法（`execute`） | `LoanService` 這種裝十幾個操作的上帝類別 |
| 需要外部能力時**在本層定義 Port**（介面） | import adapter、ORM、HTTP、框架 |
| 依賴透過**建構子注入** port | 在 use case 內 `new SqlLoanRepository()` |
| 輸入用 Input DTO、輸出用 Output DTO | 參數是 `HttpRequest`；回傳 entity 或 `{ status: 404 }` |
| 業務規則交給 entity 方法 | `if openLoans.count >= 3`（這是 entity 的事） |
| 副作用（通知）放在交易成功之後 | 在交易內寄信 |

`if` 只用於**流程判斷**：找不到資料、權限、是否需要通知。

## 放什麼 / 不放什麼

| 種類 | 說明 | 範例 |
|---|---|---|
| Use Case（Interactor） | 一個操作 | `BorrowBook`、`RenewLoan` |
| Input / Output DTO | 跨邊界的純資料，每個 use case 各自一組（詳見 [dto.md](../concepts/dto.md)） | `BorrowBookInput`、`BorrowBookOutput` |
| Output Port | 本層需要、外層實作的介面 | `LoanRepository`、`Clock`、`Notifier`、`UnitOfWork` |
| Query Port | 查詢用，回傳 read model DTO | `LoanQueries.listByMember` |
| Application Error | 流程層面的錯誤 | `MemberNotFound`、`BookNotFound` |
| （選用）Input Port | use case 的介面；只有一個實作時可省略 | `BorrowBookUseCase` |
| ❌ 不放 | Port 的實作、SQL、HTTP 狀態碼、JSON 格式 | — |

## 怎麼寫（步驟）

1. 定義 Input / Output DTO（用基本型別，controller 不必認識 domain 型別）
2. 列出需要的外部能力 → 沿用既有 port，必要時才新增方法或 port
3. 照**標準六步**寫 `execute`：
   ```
   1. 輸入轉成 domain 型別（Value 建構時會檢查格式）
   2. 透過 port 載入 entity（找不到 → 應用錯誤）
   3. 呼叫 entity 方法執行規則
   4. 透過 port 儲存（多個 entity 時包在 UnitOfWork 內）
   5. 副作用（通知、事件）
   6. 回傳 Output DTO
   ```
4. 用 in-memory fakes 寫測試：成功路徑 + 每種失敗 + 失敗時副作用沒發生

## 範例程式碼

Java，相容 JDK 1.7。Port 的完整定義（`LoanRepository`、`Clock`、`Notifier`、`UnitOfWork`）見 [ports-and-adapters.md](../../../shared/ports-and-adapters.md#常見-port-清單圖書範例)。

```java
// FILE: <application>/AppException.java
public class AppException extends RuntimeException {
    private final String code;
    public AppException(String code) { super(code); this.code = code; }
    public String getCode() { return code; }
}
// 使用：throw new AppException("MEMBER_NOT_FOUND");
// 也可以為每種錯誤建子類別（MemberNotFoundException extends AppException）

// FILE: <application>/usecase/borrowbook/BorrowBookInput.java
public final class BorrowBookInput {
    private final String memberId;
    private final String bookId;
    public BorrowBookInput(String memberId, String bookId) { this.memberId = memberId; this.bookId = bookId; }
    public String getMemberId() { return memberId; }
    public String getBookId() { return bookId; }
}

// FILE: <application>/usecase/borrowbook/BorrowBookOutput.java
public final class BorrowBookOutput {
    private final String loanId;
    private final LocalDate dueDate;
    public BorrowBookOutput(String loanId, LocalDate dueDate) { this.loanId = loanId; this.dueDate = dueDate; }
    public String getLoanId() { return loanId; }
    public LocalDate getDueDate() { return dueDate; }
}

// FILE: <application>/usecase/borrowbook/BorrowBook.java
// 禁止 import：adapter、infrastructure、Spring、JPA、Servlet
public class BorrowBook {
    private final MemberRepository members;
    private final BookRepository books;
    private final LoanRepository loans;
    private final Clock clock;
    private final Notifier notifier;
    private final UnitOfWork uow;

    public BorrowBook(MemberRepository members, BookRepository books, LoanRepository loans,
                      Clock clock, Notifier notifier, UnitOfWork uow) {
        this.members = members;
        this.books = books;
        this.loans = loans;
        this.clock = clock;
        this.notifier = notifier;
        this.uow = uow;
    }

    public BorrowBookOutput execute(BorrowBookInput input) {
        // 1. 輸入轉換（Value 建構子會檢查格式）
        MemberId memberId = new MemberId(input.getMemberId());
        BookId bookId = new BookId(input.getBookId());
        LocalDateTime now = clock.now();

        // 2. 載入
        Member member = members.findById(memberId);
        if (member == null) {
            throw new AppException("MEMBER_NOT_FOUND");
        }
        final Book book = books.findById(bookId);          // Java 7：匿名類別使用的變數須為 final
        if (book == null) {
            throw new AppException("BOOK_NOT_FOUND");
        }
        List<Loan> openLoans = loans.findOpenByMember(memberId);

        // 3. 規則：全部交給 entity
        member.assertCanBorrow(openLoans, now);
        book.markAsLent();
        final Loan loan = Loan.open(loans.nextId(), memberId, bookId, now);

        // 4. 儲存（同一交易）
        uow.run(new Runnable() {
            @Override
            public void run() {
                books.save(book);
                loans.save(loan);
            }
        });

        // 5. 副作用（交易成功後）
        notifier.notifyBookBorrowed(new Recipient(member.getEmail(), member.getName()),
                book.getTitle(), loan.getDueDate());

        // 6. 回傳 DTO，不回傳 entity
        return new BorrowBookOutput(loan.getId().getValue(), loan.getDueDate());
    }
}
```

> Java 8 可把 `new Runnable() { ... }` 換成 `() -> { books.save(book); loans.save(loan); }`。

查詢型 use case 可以跳過 entity，直接用 Query Port。多個純查詢（沒有規則、只有一行委派）可以合併成**一個查詢類別、多個方法**，
這是「一個 use case 一個類別」的唯一例外；寫入型 use case 仍然一個類別一個操作：

```java
// FILE: <application>/usecase/LendingQueryService.java
public class LendingQueryService {
    private final LendingQueries queries;       // 回傳 read model DTO 的 port

    public LendingQueryService(LendingQueries queries) { this.queries = queries; }

    public List<BookView> listBooks() { return queries.listBooks(); }

    public List<LoanSummary> listMemberLoans(String memberId) {
        return queries.listOpenLoansByMember(new MemberId(memberId));
    }
}
```

## 與其他層的配合

```
 Driving Adapter                Use Case                         Entities
 (controller)                   (本層)
     │  execute(InputDTO)          │                                 │
     │────────────────────────────▶│                                 │
     │                             │ findById(id) ──▶ «port» ──▶ Driven Adapter（CSV / SQL）
     │                             │◀── Entity ─────────────────────│
     │                             │ member.assertCanBorrow(...) ───▶│
     │                             │◀── OK / 領域錯誤 ───────────────│
     │                             │ save(entity) ──▶ «port» ──▶ Driven Adapter
     │                             │ notify(...)  ──▶ «port» ──▶ Driven Adapter（SMTP）
     │◀── OutputDTO / 錯誤 ────────│
```

| 對象 | 關係 | 跨邊界傳什麼 |
|---|---|---|
| Entities | **我呼叫它**：載入後呼叫方法、用工廠建立 | 進：參數；出：結果、領域錯誤 |
| Driving Adapters | **呼叫我**：controller / CLI 呼叫 `execute` | 進：Input DTO；出：Output DTO、領域 / 應用錯誤 |
| Driven Adapters | **實作我定義的 port**；我透過 port 呼叫它，**不知道**它是 CSV 還是 SQL | 進出：entity、domain 型別、DTO |
| Frameworks & Drivers | **組裝我**：composition root 建立我並注入 port 實作 | 建構子參數 |
| 我依賴誰 | 只有 Entities 與本層的 port 介面 | — |

**依賴反轉的關鍵**：控制流是 use case → adapter，但原始碼依賴是 adapter → port（在本層）。
所以換掉 CSV / SQL / Mongo，本層一行都不用改。

## 測試

用 in-memory fakes 實作 port，不需要 DB 與框架：

```java
// FILE: <tests>/application/BorrowBookTest.java     （JUnit 4）
public class BorrowBookTest {
    @Test
    public void 會員不存在時回報MEMBER_NOT_FOUND_且不寄信() {
        SpyNotifier notifier = new SpyNotifier();
        BorrowBook useCase = new BorrowBook(
                new InMemoryMemberRepository(),
                new InMemoryBookRepository(availableBook("b1")),
                new InMemoryLoanRepository(),
                new FixedClock(LocalDateTime.of(2026, 1, 1, 10, 0)),
                notifier,
                new DirectUnitOfWork());              // 直接執行 work，不做交易

        try {
            useCase.execute(new BorrowBookInput("nobody", "b1"));
            fail("應該丟出 AppException");
        } catch (AppException e) {
            assertEquals("MEMBER_NOT_FOUND", e.getCode());
        }
        assertEquals(0, notifier.getCallCount());
    }
}
```

## 自我檢查

- [ ] 沒有 import adapters / infrastructure / 框架
- [ ] `if` 只做流程判斷，沒有業務規則
- [ ] 回傳 DTO，不是 entity
- [ ] port 方法名是業務語言，參數 / 回傳是內層型別
- [ ] 測試只用 fakes，此時可以完全沒有 adapter

常見錯誤：上帝 Service、回傳 entity、洩漏的 port、交易內副作用 → [anti-patterns.md](../review/anti-patterns.md)
