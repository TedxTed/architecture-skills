# 第 1 層：Entities（企業業務規則）

> 位置：`<domain>`｜依賴：**無**（最內層）｜被誰使用：Use Cases、Adapters（mapper）

## 職責

表達「業務本身」的規則。**就算明天不用電腦、改用紙本，這些規則仍然成立。**
例：停權會員不能借書、借期 14 天、逾期每天罰 10 元。

## 遵循規則

| 必須 | 禁止 |
|---|---|
| 規則寫成 entity 的**方法**（行為），不是 use case 裡的 `if` | import 任何外層、框架、ORM、HTTP、SDK |
| 時間、亂數等外部值**用參數傳入**（`renew(now)`） | 直接呼叫 `LocalDateTime.now()`、`new Date()`、`UUID.randomUUID()` |
| 方法名稱用業務動詞（`markAsLent`、`renew`） | `setStatus()`、`setDueDate()` 這類 setter |
| 違反規則時 throw **領域錯誤**（`BookNotAvailableException`） | 回報 HTTP 狀態碼或框架例外 |
| 狀態只能透過方法改變，欄位對外唯讀 | 欄位上有 `@Entity`、`@Column`、`@JsonProperty` |
| 用 Value 包裝有意義的值（`LoanId`、`Money`） | 知道「誰呼叫我」、知道資料怎麼存 |

## 放什麼 / 不放什麼

| 種類 | 說明 | 範例 |
|---|---|---|
| Entity | 有 id、有生命週期、有行為 | `Member`、`Book`、`Loan` |
| Value | 無 id、不可變、以值比較相等 | `MemberId`、`Money`、`Email` |
| Domain Error | 違反業務規則 | `BookNotAvailable`、`LoanLimitExceeded` |
| Domain Service | 不屬於單一 entity 的規則，無狀態純函式 | `FineCalculator`（若罰金規則跨多個 entity） |
| Domain Event | 業務上已發生的事（過去式），entity 記錄、use case 發佈 | `BookBorrowed`（見 [domain-events.md](../concepts/domain-events.md)） |
| ❌ 不放 | Repository、DTO、Port、通知、log、設定值讀取 | — |

## 怎麼寫（步驟）

1. 從需求找出**名詞** → entity / value；**動詞** → entity 方法
2. 每條業務規則對應到「它主要關於哪個 entity」，寫成該 entity 的方法
3. 需要外部資訊（時間、其他 entity 的資料）→ 加成方法參數
4. 每種違規定義一個領域錯誤
5. 提供兩種建構方式：`open(...)`（新建，檢查規則）與 `reconstitute(...)`（從儲存還原，不檢查）
6. 寫單元測試：每條規則至少一個成功、一個失敗

## Aggregate：一起被讀寫的 entity 群組

**Aggregate** 是一組必須一起保持一致的 entity，對外只透過其中一個 **root** 存取。
Repository 以 aggregate 為單位：**一個 aggregate 一個 repository**，存取一律經過 root。

| 規則 | 範例 |
|---|---|
| 外部只能拿到 root，不能直接改內部的 entity | 訂單的明細只能透過 `order.addLine()` 改，不能直接改 `orderLine.qty` |
| aggregate 之間只用 **id** 互相參照 | `Loan` 存 `bookId`，不存 `Book` 物件 |
| 一個 aggregate 內的規則，在一次儲存中保證成立 | 訂單總額不得超過上限 |

本範例中 `Member`、`Book`、`Loan` 各自是一個 aggregate（都只有 root）。

**交易邊界的取捨**：嚴格的 DDD 主張「一次交易只改一個 aggregate」，跨 aggregate 用 domain event 達成最終一致。
本 skill 的 `BorrowBook` 在同一交易中改了 `Book` 與 `Loan`（透過 UnitOfWork），這是 Clean Architecture 常見的**務實做法**：

| 做法 | 適用 |
|---|---|
| 同一交易改多個 aggregate（UnitOfWork） | 同一模組、同一資料庫、需要立即一致（借出後書一定是借出狀態） |
| 一個交易一個 aggregate + domain event | 跨模組、跨服務、可以接受短暫不一致 |

選了哪一種，記入專案的 `decisions.md`。

## 可變 vs 不可變的 entity

本 skill 的範例用**可變** entity（`book.markAsLent()` 直接改自己的狀態，欄位對外唯讀）。
另一種做法是**不可變**：方法回傳新的 entity（`const lent = book.markAsLent()`）。兩種都符合 Clean Architecture：

| | 可變（本 skill 範例） | 不可變 |
|---|---|---|
| 優點 | 寫法直觀；與多數 ORM 相容 | 不會被意外修改；容易推理與測試 |
| 注意 | 狀態只能透過方法改變 | use case 要記得使用回傳的新物件去儲存 |

在專案中選定一種，記入 `conventions.md`。

## 範例程式碼

Java，相容 JDK 1.7（約定見 [code-conventions.md](../../../shared/code-conventions.md)）。

```java
// FILE: <domain>/LoanId.java          （MemberId、BookId 寫法相同）
public final class LoanId {
    private final String value;

    public LoanId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidIdException("LoanId");
        }
        this.value = value;
    }

    public String getValue() { return value; }

    @Override public boolean equals(Object o) {
        return o instanceof LoanId && ((LoanId) o).value.equals(value);
    }
    @Override public int hashCode() { return value.hashCode(); }
}

// FILE: <domain>/DomainException.java
public abstract class DomainException extends RuntimeException {
    private final String code;
    protected DomainException(String code) { super(code); this.code = code; }
    public String getCode() { return code; }
}

// FILE: <domain>/BookNotAvailableException.java      （其他領域錯誤寫法相同）
public class BookNotAvailableException extends DomainException {
    public BookNotAvailableException() { super("BOOK_NOT_AVAILABLE"); }
}
// 其他：MemberSuspendedException、LoanLimitExceededException、HasOverdueLoansException、
//       CannotRenewOverdueLoanException、RenewLimitExceededException、LoanAlreadyClosedException

// FILE: <domain>/Loan.java
// 禁止 import：application、adapter、infrastructure、任何框架
public class Loan {
    private static final int LOAN_DAYS = 14;          // R5
    private static final long FINE_PER_DAY = 10;      // R6，單位：元
    private static final int MAX_RENEW = 1;          // R7

    private final LoanId id;
    private final MemberId memberId;
    private final BookId bookId;
    private final LocalDateTime borrowedAt;
    private LocalDate dueDate;
    private LocalDateTime returnedAt;                 // null = 尚未歸還
    private int renewCount;

    private Loan(LoanId id, MemberId memberId, BookId bookId, LocalDateTime borrowedAt,
                 LocalDate dueDate, LocalDateTime returnedAt, int renewCount) {
        this.id = id;
        this.memberId = memberId;
        this.bookId = bookId;
        this.borrowedAt = borrowedAt;
        this.dueDate = dueDate;
        this.returnedAt = returnedAt;
        this.renewCount = renewCount;
    }

    // 新建：套用規則
    public static Loan open(LoanId id, MemberId memberId, BookId bookId, LocalDateTime now) {
        return new Loan(id, memberId, bookId, now, now.toLocalDate().plusDays(LOAN_DAYS), null, 0);
    }

    // 還原：給 repository mapper 用，不檢查規則
    public static Loan reconstitute(LoanId id, MemberId memberId, BookId bookId, LocalDateTime borrowedAt,
                                    LocalDate dueDate, LocalDateTime returnedAt, int renewCount) {
        return new Loan(id, memberId, bookId, borrowedAt, dueDate, returnedAt, renewCount);
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
        return Math.max(0, lateDays) * FINE_PER_DAY;   // R6
    }

    public void renew(LocalDateTime now) {
        if (isOverdue(now)) {
            throw new CannotRenewOverdueLoanException();   // R7
        }
        if (renewCount >= MAX_RENEW) {
            throw new RenewLimitExceededException();       // R7
        }
        dueDate = dueDate.plusDays(LOAN_DAYS);
        renewCount++;
    }

    public LoanId getId() { return id; }
    public MemberId getMemberId() { return memberId; }
    public BookId getBookId() { return bookId; }
    public LocalDateTime getBorrowedAt() { return borrowedAt; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDateTime getReturnedAt() { return returnedAt; }
    public int getRenewCount() { return renewCount; }
}

// FILE: <domain>/Member.java
public class Member {
    private final MemberId id;
    private final String name;
    private final String email;
    private final MemberTier tier;          // enum MemberTier { REGULAR, VIP }
    private final boolean suspended;

    public Member(MemberId id, String name, String email, MemberTier tier, boolean suspended) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.tier = tier;
        this.suspended = suspended;
    }

    public int loanLimit() {
        return tier == MemberTier.VIP ? 5 : 3;              // R2
    }

    // 跨 entity 的規則：其他 entity 的資料由參數傳入
    public void assertCanBorrow(List<Loan> openLoans, LocalDateTime now) {
        if (suspended) {
            throw new MemberSuspendedException();           // R1
        }
        if (openLoans.size() >= loanLimit()) {
            throw new LoanLimitExceededException();         // R2
        }
        for (Loan loan : openLoans) {
            if (loan.isOverdue(now)) {
                throw new HasOverdueLoansException();       // R3
            }
        }
    }

    public MemberId getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public MemberTier getTier() { return tier; }
    public boolean isSuspended() { return suspended; }
}

// FILE: <domain>/Book.java
public class Book {
    private final BookId id;
    private final String title;
    private BookStatus status;              // enum BookStatus { AVAILABLE, ON_LOAN }

    public Book(BookId id, String title, BookStatus status) {
        this.id = id;
        this.title = title;
        this.status = status;
    }

    public void markAsLent() {
        if (status != BookStatus.AVAILABLE) {
            throw new BookNotAvailableException();          // R4
        }
        status = BookStatus.ON_LOAN;
    }

    public void markAsReturned() {
        status = BookStatus.AVAILABLE;
    }

    public BookId getId() { return id; }
    public String getTitle() { return title; }
    public BookStatus getStatus() { return status; }
}
```

> `LocalDate`、`LocalDateTime`、`ChronoUnit`：Java 8 用 `java.time`；Java 7 改 import `org.threeten.bp`（ThreeTen Backport），程式碼不用改。

## 與其他層的配合

```
        ┌──────────────────────────── Use Cases ────────────────────────────┐
        │  BorrowBook：                                                      │
        │    member = members.findById(...)      ← 透過 port 取得 entity     │
        │    member.assertCanBorrow(loans, now)  ← 呼叫 entity 方法執行規則  │
        │    loan = Loan.open(..., now)          ← 用工廠建立新 entity       │
        │    loans.save(loan)                    ← 把 entity 交給 port       │
        └───────────────┬───────────────────────────────────────────────────┘
                        │ 呼叫方法、傳入參數（時間、其他 entity）
                        ▼
        ┌────────────── Entities ──────────────┐
        │  回傳：結果值 / 改變自身狀態 / throw   │
        └──────────────────────────────────────┘
                        ▲
                        │ reconstitute(...) / 讀取欄位
        ┌────── Adapters：Repository mapper ────┐
        │  DB row / CSV row ⇄ Entity           │
        └──────────────────────────────────────┘
```

| 對象 | 關係 | 跨邊界傳什麼 |
|---|---|---|
| Use Cases | **呼叫我**：取得 entity 後呼叫方法 | 進：參數（時間、其他 entity）；出：結果值、領域錯誤 |
| Adapters（mapper） | **建構我**：`reconstitute` 還原、讀欄位轉成 row | Entity ⇄ 儲存格式 |
| Adapters（controller） | **不直接接觸**：controller 只拿到 Output DTO | — |
| Frameworks & Drivers | **不接觸** | — |
| 我依賴誰 | **沒有人**。只依賴語言標準庫的基本型別 | — |

**關鍵**：Entity 不知道自己會被誰呼叫、怎麼被存。領域錯誤由最外層的 adapter 翻譯成 HTTP 狀態碼。

## 測試

純單元測試：**不需要 mock、不需要 DB、不需要框架**。這一層應該有全專案最多的測試。

```java
// FILE: <tests>/domain/LoanTest.java     （JUnit 4）
public class LoanTest {
    private static final LocalDateTime JAN_1 = LocalDateTime.of(2026, 1, 1, 10, 0);

    private Loan openLoan() {
        return Loan.open(new LoanId("l1"), new MemberId("m1"), new BookId("b1"), JAN_1);   // 到期 2026-01-15
    }

    @Test(expected = CannotRenewOverdueLoanException.class)
    public void 逾期的借閱不能續借() {
        openLoan().renew(LocalDateTime.of(2026, 1, 20, 10, 0));
    }

    @Test
    public void 逾期3天歸還_罰金30元() {
        assertEquals(30L, openLoan().close(LocalDateTime.of(2026, 1, 18, 10, 0)));
    }
}
```

## 自我檢查

- [ ] `<domain>` 內沒有任何 import 指向外層或框架
- [ ] 沒有呼叫 `LocalDateTime.now()` / `new Date()` / `UUID.randomUUID()`
- [ ] 每條業務規則都在 entity 方法內，use case 中找不到同樣的判斷
- [ ] 測試不需要任何 mock

常見錯誤：貧血模型、框架滲透、隱藏的時間依賴 → [anti-patterns.md](../review/anti-patterns.md)
