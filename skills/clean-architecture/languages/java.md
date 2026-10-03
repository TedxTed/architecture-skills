# Java 對照

## Pseudocode → Java

| Pseudocode | Java 慣用寫法（Java 17+） |
|---|---|
| `ENTITY` | `class`，`private` 欄位，以方法改變狀態 |
| `VALUE` | `record`（建構子中驗證） |
| `ENUM` | `enum` |
| `PORT` | `interface` |
| `ADAPTER ... IMPLEMENTS` | `class X implements Port` |
| `USE_CASE` | `class` + `execute()`，建構子注入，`final` 欄位 |
| `DTO` | `record` |
| `FAIL` | `throw new XxxException()`（繼承自定義的 `DomainException`，unchecked） |
| `Nothing` | `Optional<T>` |

## 資料夾（package）

```
A. by-layer                              B. by-feature
com/example/library/                     com/example/library/
├── domain/                              ├── lending/
├── application/                         │   ├── LendingApi.java    # 模組對外公開（放在模組根 package）
│   ├── port/                            │   ├── domain/
│   └── usecase/                         │   ├── application/port/ , usecase/
├── adapter/                             │   ├── adapter/web/ , persistence/
│   ├── web/                             │   └── config/            # 模組組裝
│   └── persistence/                     ├── catalog/ ...
└── config/   # composition root         └── sharedkernel/
```

B 結構與 [Spring Modulith](https://spring.io/projects/spring-modulith) 的慣例一致：模組根 package 是公開 API，子 package 是內部實作。

進階：用 Gradle / Maven **多模組**（`library-domain`、`library-application`、`library-adapters`、`library-boot`），由建置工具保證依賴方向 —— 內層模組的 build 檔根本不宣告 Spring 依賴。

## 範例

```java
// domain/DomainException.java
public abstract class DomainException extends RuntimeException {
    private final String code;
    protected DomainException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}

// domain/BookNotAvailableException.java
public final class BookNotAvailableException extends DomainException {
    public BookNotAvailableException() { super("BOOK_NOT_AVAILABLE"); }
}

// domain/Book.java
public class Book {
    private final BookId id;
    private final String title;
    private BookStatus status;

    public Book(BookId id, String title, BookStatus status) {
        this.id = id; this.title = title; this.status = status;
    }

    public void markAsLent() {
        if (status != BookStatus.AVAILABLE) throw new BookNotAvailableException(); // R4
        status = BookStatus.ON_LOAN;
    }

    public BookId id() { return id; }
    public String title() { return title; }
    public BookStatus status() { return status; }
}

// application/port/LoanRepository.java
public interface LoanRepository {
    List<Loan> findOpenByMember(MemberId memberId);
    void save(Loan loan);
    LoanId nextId();
}

// application/usecase/borrowbook/BorrowBook.java
public class BorrowBook {
    public record Input(String memberId, String bookId) {}
    public record Output(String loanId, LocalDate dueDate) {}

    private final MemberRepository members;
    private final BookRepository books;
    private final LoanRepository loans;
    private final Clock clock;
    private final Notifier notifier;
    private final UnitOfWork uow;

    public BorrowBook(MemberRepository members, BookRepository books, LoanRepository loans,
                      Clock clock, Notifier notifier, UnitOfWork uow) {
        this.members = members; this.books = books; this.loans = loans;
        this.clock = clock; this.notifier = notifier; this.uow = uow;
    }

    public Output execute(Input input) {
        var now = clock.now();
        var member = members.findById(new MemberId(input.memberId()))
                .orElseThrow(MemberNotFoundException::new);
        var book = books.findById(new BookId(input.bookId()))
                .orElseThrow(BookNotFoundException::new);
        var openLoans = loans.findOpenByMember(member.id());

        member.assertCanBorrow(openLoans, now);
        book.markAsLent();
        var loan = Loan.open(loans.nextId(), member.id(), book.id(), now);

        uow.run(() -> {
            books.save(book);
            loans.save(loan);
        });

        notifier.notifyBookBorrowed(member.id(), book.title(), loan.dueDate());
        return new Output(loan.id().value(), loan.dueDate());
    }
}
```

> `Clock` 此處指自定義的 port。也可以直接使用 `java.time.Clock`（標準庫，不是框架），測試時用 `Clock.fixed(...)`。

## Spring Boot 整合

**原則：Spring 註解只出現在 `adapter/` 與 `config/`。**

| ❌ 不要 | ✅ 改為 |
|---|---|
| use case 上加 `@Service` | 在 `config/` 用 `@Bean` 建立 |
| use case 上加 `@Transactional` | `UnitOfWork` port，或在 config 用 decorator 包裝 |
| domain entity 上加 `@Entity` | `adapter/persistence/LoanJpaEntity` + mapper |
| use case 回傳 `ResponseEntity` | controller 中轉換 |

```java
// config/LendingConfig.java   ← Composition root
@Configuration
public class LendingConfig {
    @Bean
    BorrowBook borrowBook(MemberRepository m, BookRepository b, LoanRepository l,
                          Clock c, Notifier n, UnitOfWork u) {
        return new BorrowBook(m, b, l, c, n, u);
    }
}

// adapter/persistence/SpringUnitOfWork.java
@Component
class SpringUnitOfWork implements UnitOfWork {
    private final TransactionTemplate tx;
    SpringUnitOfWork(TransactionTemplate tx) { this.tx = tx; }
    public void run(Runnable work) { tx.executeWithoutResult(s -> work.run()); }
}

// adapter/web/LoanController.java
@RestController
class LoanController {
    private final BorrowBook borrowBook;
    LoanController(BorrowBook borrowBook) { this.borrowBook = borrowBook; }

    @PostMapping("/loans")
    ResponseEntity<?> borrow(@RequestBody BorrowRequest req) {
        var out = borrowBook.execute(new BorrowBook.Input(req.memberId(), req.bookId()));
        return ResponseEntity.status(201).body(Map.of("loanId", out.loanId(), "dueDate", out.dueDate()));
    }
}

// adapter/web/ErrorMapping.java
@RestControllerAdvice
class ErrorMapping {
    @ExceptionHandler(BookNotAvailableException.class)
    ResponseEntity<?> conflict(DomainException e) { return ResponseEntity.status(409).body(Map.of("error", e.code())); }
    // ...
}
```

## 強制依賴規則

使用 [ArchUnit](https://www.archunit.org/)：

```java
@AnalyzeClasses(packages = "com.example.library")
class ArchitectureTest {
    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringAllDependencies()
        .layer("Domain").definedBy("..domain..")
        .layer("Application").definedBy("..application..")
        .layer("Adapter").definedBy("..adapter..")
        .layer("Config").definedBy("..config..")
        .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Config")
        .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Config")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Config");

    @ArchTest
    static final ArchRule noSpringInside = noClasses()
        .that().resideInAnyPackage("..domain..", "..application..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "jakarta.persistence..", "javax.persistence..");
}
```

上面的規則用 `..domain..` 等萬用字元，**A、B 兩種結構都適用**。
B 另外需要模組隔離：用 Spring Modulith 的 `ApplicationModules.of(LibraryApplication.class).verify()`，
它會檢查模組只透過根 package（公開 API）互相存取。
