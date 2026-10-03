# 端到端範例：借書（BorrowBook）

從 HTTP 請求一路到 CSV / 資料庫，列出每個檔案與實作順序。這是**所有寫入型功能的樣板**，換掉名詞即可套用。
Java，相容 JDK 1.7（約定見 [code-conventions.md](../../../shared/code-conventions.md)）。

## 檔案總覽（Maven 專案，by-feature）

`<domain>` 等佔位符依專案 card.md 的路徑表替換；下例為 `com.example.library.lending` 模組。

```
src/main/java/com/example/library/lending/
├── domain/                                  <domain>
│   ├── LoanId.java, MemberId.java, BookId.java       ① Value
│   ├── DomainException.java + 各領域錯誤              ② 領域錯誤
│   ├── Member.java, Book.java, Loan.java             ③ Entity
│   └── MemberTier.java, BookStatus.java              （enum）
├── application/                             <application>
│   ├── AppException.java                             ④ 應用錯誤
│   ├── port/                                         ⑤ Output port
│   │   ├── MemberRepository.java, BookRepository.java, LoanRepository.java
│   │   ├── Clock.java, UnitOfWork.java
│   │   └── Notifier.java, Recipient.java
│   └── usecase/borrowbook/
│       ├── BorrowBookInput.java, BorrowBookOutput.java   ⑥ DTO
│       └── BorrowBook.java                           ⑦ Use case
├── adapter/                                 <adapters>
│   ├── web/LoanController.java, ErrorMapping.java    ⑧ Driving adapter
│   ├── persistence/LoanMapper.java
│   ├── persistence/csv/CsvStore.java, CsvLoanRepository.java, CsvUnitOfWork.java   ⑨ 先做
│   ├── persistence/sql/SqlLoanRepository.java       ⑨ 使用者確認後才做
│   ├── notification/SmtpNotifier.java               ⑨
│   └── time/SystemClock.java                        ⑨
└── config/                                  <main>
    ├── LendingConfig.java, CsvStorageConfig.java    ⑩ Composition root
    └── SqlStorageConfig.java

src/test/java/com/example/library/lending/   <tests>
├── domain/MemberTest.java, LoanTest.java             ⑪
├── application/BorrowBookTest.java                   ⑫
└── fakes/                                            ⑫ in-memory 假實作
```

**實作順序 = 編號順序**（由內往外）。

## 各部分的程式碼在哪

| 編號 | 內容 | 程式碼 |
|---|---|---|
| ① ② ③ | Value、領域錯誤、Entity | [01-entities.md](../layers/01-entities.md#範例程式碼) |
| ④ ⑤ | 應用錯誤、Port | [02-use-cases.md](../layers/02-use-cases.md#範例程式碼)、[ports-and-adapters.md](../../../shared/ports-and-adapters.md#常見-port-清單圖書範例) |
| ⑥ ⑦ | DTO、Use case | [02-use-cases.md](../layers/02-use-cases.md#範例程式碼) |
| ⑧ ⑨ | Controller、錯誤對應、Mapper、CSV repository、Notifier、Clock | [03-interface-adapters.md](../layers/03-interface-adapters.md#範例程式碼)；CsvStore / CsvUnitOfWork 見 [csv-first.md](../concepts/csv-first.md#樣板) |
| ⑩ | Spring Java Config | [04-frameworks-drivers.md](../layers/04-frameworks-drivers.md#範例程式碼) |
| ⑪ ⑫ | 測試與 fakes | 下方 |

本頁只補上其他文件沒有的部分：`BookRepository` port 與完整的測試替身。

## ⑤ BookRepository / MemberRepository

```java
// FILE: <application>/port/BookRepository.java
public interface BookRepository {
    Book findById(BookId id);        // 找不到回傳 null
    void save(Book book);
}

// FILE: <application>/port/MemberRepository.java
public interface MemberRepository {
    Member findById(MemberId id);    // 找不到回傳 null
}
```

## ⑪ Domain 測試（不需要任何假物件）

```java
// FILE: <tests>/domain/MemberTest.java     （JUnit 4）
public class MemberTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 10, 0);

    private Loan openLoan(String id) {
        return Loan.open(new LoanId(id), new MemberId("m1"), new BookId("b-" + id), NOW);
    }

    @Test(expected = LoanLimitExceededException.class)
    public void 一般會員已借3本時不能再借() {
        Member member = new Member(new MemberId("m1"), "王小明", "a@b.c", MemberTier.REGULAR, false);
        member.assertCanBorrow(Arrays.asList(openLoan("1"), openLoan("2"), openLoan("3")), NOW);
    }

    @Test
    public void VIP已借3本仍可借() {
        Member member = new Member(new MemberId("m2"), "陳美玲", "c@d.e", MemberTier.VIP, false);
        member.assertCanBorrow(Arrays.asList(openLoan("1"), openLoan("2"), openLoan("3")), NOW);   // 不丟例外即通過
    }
}
```

## ⑫ Use case 測試與 in-memory fakes

```java
// FILE: <tests>/fakes/InMemoryLoanRepository.java
public class InMemoryLoanRepository implements LoanRepository {
    private final Map<LoanId, Loan> items = new LinkedHashMap<LoanId, Loan>();
    private int sequence = 0;

    @Override public Loan findById(LoanId id) { return items.get(id); }

    @Override public List<Loan> findOpenByMember(MemberId memberId) {
        List<Loan> result = new ArrayList<Loan>();
        for (Loan loan : items.values()) {
            if (loan.getMemberId().equals(memberId) && loan.getReturnedAt() == null) {
                result.add(loan);
            }
        }
        return result;
    }

    @Override public void save(Loan loan) { items.put(loan.getId(), loan); }

    @Override public LoanId nextId() { return new LoanId("loan-" + (++sequence)); }
}
// InMemoryMemberRepository、InMemoryBookRepository 寫法相同（建構子接收初始資料）

// FILE: <tests>/fakes/FixedClock.java
public class FixedClock implements Clock {
    private final LocalDateTime time;
    public FixedClock(LocalDateTime time) { this.time = time; }
    @Override public LocalDateTime now() { return time; }
}

// FILE: <tests>/fakes/SpyNotifier.java
public class SpyNotifier implements Notifier {
    private int callCount = 0;
    @Override public void notifyBookBorrowed(Recipient to, String bookTitle, LocalDate dueDate) { callCount++; }
    public int getCallCount() { return callCount; }
}

// FILE: <tests>/fakes/DirectUnitOfWork.java
public class DirectUnitOfWork implements UnitOfWork {
    @Override public void run(Runnable work) { work.run(); }    // 測試中不需要真正的交易
}

// FILE: <tests>/application/BorrowBookTest.java     （JUnit 4）
public class BorrowBookTest {
    private final InMemoryBookRepository books = new InMemoryBookRepository(
            new Book(new BookId("b1"), "Clean Architecture", BookStatus.AVAILABLE));
    private final InMemoryLoanRepository loans = new InMemoryLoanRepository();
    private final SpyNotifier notifier = new SpyNotifier();
    private final BorrowBook useCase = new BorrowBook(
            new InMemoryMemberRepository(new Member(new MemberId("m1"), "王小明", "a@b.c", MemberTier.REGULAR, false)),
            books, loans, new FixedClock(LocalDateTime.of(2026, 1, 1, 10, 0)), notifier, new DirectUnitOfWork());

    @Test
    public void 借書成功_書變為借出_到期日為14天後_寄出通知() {
        BorrowBookOutput out = useCase.execute(new BorrowBookInput("m1", "b1"));

        assertEquals(LocalDate.of(2026, 1, 15), out.getDueDate());
        assertEquals(BookStatus.ON_LOAN, books.findById(new BookId("b1")).getStatus());
        assertEquals(1, notifier.getCallCount());
    }

    @Test
    public void 會員不存在時回報MEMBER_NOT_FOUND_且不寄信() {
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
