# Workflow：新增 / 替換 Adapter

適用：換資料庫、加第三方服務、加新入口（CLI、Message Queue）。
**這是 Clean Architecture 回本的時刻：domain 與 application 一行都不用改。**
範例為 Java，相容 JDK 1.7。

---

## 情境 A：替換 Driven Adapter（例：CSV → 資料庫）

最常見的情況：新功能依 [CSV 優先](../concepts/csv-first.md) 跑通後，換成正式資料庫。換成其他資料庫（例如 Oracle → PostgreSQL、或 MongoDB）步驟相同。

### 步驟

1. **找到 port**：`<application>/port/LoanRepository.java`。不修改它。
2. **新增實作**：`<adapters>/persistence/sql/SqlLoanRepository.java`（含建表 SQL / migration）
3. **新增 mapper**：Entity ⇄ DB row（欄位名稱可沿用 CSV 版）
4. **跑同一套 repository contract test**（見下方）
5. **新增 `SqlStorageConfig`（`@Profile("sql")`），切換 profile**：`spring.profiles.active=sql`
6. **CSV adapter 建議保留**，作為本機開發與展示用；若使用者決定刪除，記錄到 `conventions.md`

```java
// FILE: <adapters>/persistence/sql/SqlLoanRepository.java    （Spring JdbcTemplate，Spring 3 以上、Java 7 可用）
public class SqlLoanRepository implements LoanRepository {
    private static final RowMapper<Loan> ROW_MAPPER = new RowMapper<Loan>() {
        @Override
        public Loan mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp returnedAt = rs.getTimestamp("returned_at");
            return Loan.reconstitute(
                    new LoanId(rs.getString("id")), new MemberId(rs.getString("member_id")),
                    new BookId(rs.getString("book_id")),
                    toLocalDateTime(rs.getTimestamp("borrowed_at")), toLocalDate(rs.getDate("due_date")),
                    returnedAt == null ? null : toLocalDateTime(returnedAt),
                    rs.getInt("renew_count"));
        }
    };

    private final JdbcTemplate jdbc;

    public SqlLoanRepository(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override
    public List<Loan> findOpenByMember(MemberId memberId) {
        return jdbc.query("SELECT * FROM loans WHERE member_id = ? AND returned_at IS NULL",
                ROW_MAPPER, memberId.getValue());
    }

    @Override
    public void save(Loan loan) {
        int updated = jdbc.update(
                "UPDATE loans SET due_date = ?, returned_at = ?, renew_count = ? WHERE id = ?",
                toSqlDate(loan.getDueDate()), toTimestamp(loan.getReturnedAt()), loan.getRenewCount(),
                loan.getId().getValue());
        if (updated == 0) {
            jdbc.update("INSERT INTO loans (id, member_id, book_id, borrowed_at, due_date, returned_at, renew_count) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    loan.getId().getValue(), loan.getMemberId().getValue(), loan.getBookId().getValue(),
                    toTimestamp(loan.getBorrowedAt()), toSqlDate(loan.getDueDate()),
                    toTimestamp(loan.getReturnedAt()), loan.getRenewCount());
        }
    }

    // findById、nextId、日期轉換的 private static 方法（toLocalDate、toTimestamp…）略
}
```

> 使用 JPA / MyBatis 時做法相同：`@Entity` 類別或 Mapper XML 只出現在 `adapter/persistence/`，以 mapper 轉成 domain entity。

### Repository Contract Test（強烈建議）

同一組測試對所有實作都跑一次，保證行為一致。JUnit 4 用**抽象測試類別**：

```java
// FILE: <tests>/contract/LoanRepositoryContract.java
public abstract class LoanRepositoryContract {
    protected abstract LoanRepository createRepository();

    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Test
    public void save後findById取得相同內容() {
        LoanRepository repo = createRepository();
        Loan loan = Loan.open(new LoanId("l1"), new MemberId("m1"), new BookId("b1"), now);
        repo.save(loan);
        assertEquals(loan.getDueDate(), repo.findById(new LoanId("l1")).getDueDate());
    }

    @Test
    public void findOpenByMember不包含已歸還的借閱() {
        LoanRepository repo = createRepository();
        Loan returned = Loan.open(new LoanId("l1"), new MemberId("m1"), new BookId("b1"), now);
        returned.close(now);
        repo.save(returned);
        assertTrue(repo.findOpenByMember(new MemberId("m1")).isEmpty());
    }

    @Test
    public void save同一id兩次為更新而非新增() { /* ... */ }
}

// FILE: <tests>/contract/InMemoryLoanRepositoryTest.java
public class InMemoryLoanRepositoryTest extends LoanRepositoryContract {
    @Override protected LoanRepository createRepository() { return new InMemoryLoanRepository(); }
}

// FILE: <tests>/contract/CsvLoanRepositoryTest.java
public class CsvLoanRepositoryTest extends LoanRepositoryContract {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Override protected LoanRepository createRepository() {
        return new CsvLoanRepository(new CsvStore(tmp.getRoot(), Charset.forName("UTF-8")));   // 用專案的 CSV 編碼
    }
}

// FILE: <tests>/contract/SqlLoanRepositoryTest.java     （接測試資料庫，例如 H2 或測試用 schema）
public class SqlLoanRepositoryTest extends LoanRepositoryContract {
    @Override protected LoanRepository createRepository() { return new SqlLoanRepository(TestDatabase.cleanDataSource()); }
}
```

✅ **檢查點**：`git diff` 中 `domain/` 與 `application/` 沒有任何變動。若有，代表原本的 port 洩漏了技術細節，先修 port。

---

## 情境 B：新增外部服務（例：借書後也要發 LINE 通知）

1. **確認 port 是否已足夠**：`Notifier.notifyBookBorrowed(...)` 已描述「要通知」，不描述「用什麼通知」→ 不改 port
2. **新增實作**：`<adapters>/notification/LineNotifier.java`
3. **要同時發 email 與 LINE？** 用組合，不改 use case：

```java
// FILE: <adapters>/notification/CompositeNotifier.java
public class CompositeNotifier implements Notifier {
    private final List<Notifier> notifiers;

    public CompositeNotifier(List<Notifier> notifiers) { this.notifiers = notifiers; }

    @Override
    public void notifyBookBorrowed(Recipient to, String bookTitle, LocalDate dueDate) {
        for (Notifier n : notifiers) {
            n.notifyBookBorrowed(to, bookTitle, dueDate);
        }
    }
}

// FILE: <main>/LendingConfig.java   （節錄）
@Bean
public Notifier notifier(JavaMailSender mailSender, LineClient lineClient) {
    return new CompositeNotifier(Arrays.<Notifier>asList(
            new SmtpNotifier(new SpringMailSender(mailSender)), new LineNotifier(lineClient)));
}
```

**新管道需要新的收件資料時**（例如 LINE 需要 `lineUserId`）：擴充 `Recipient`，由 use case 從 entity 填入。
**不要**讓 `LineNotifier` 注入 `MemberRepository` 自己查——adapter 不可以繞過 use case 讀資料。

```java
// FILE: <application>/port/Recipient.java
public final class Recipient {
    private final String email;
    private final String name;
    private final String lineUserId;             // 沒有綁定 LINE 時為 null
    // 建構子與 getter 略
}
```

4. **第三方 SDK 只能出現在 adapter 內**，連它的例外型別也不能往外拋，要翻譯：

```java
// FILE: <adapters>/notification/LineNotifier.java
public class LineNotifier implements Notifier {
    private final LineClient lineClient;
    public LineNotifier(LineClient lineClient) { this.lineClient = lineClient; }

    @Override
    public void notifyBookBorrowed(Recipient to, String bookTitle, LocalDate dueDate) {
        if (to.getLineUserId() == null) {
            return;
        }
        try {
            lineClient.pushMessage(to.getLineUserId(), "《" + bookTitle + "》到期日 " + dueDate);
        } catch (LineApiException e) {
            LoggerFactory.getLogger(LineNotifier.class).warn("LINE 通知失敗", e);   // 依需求記 log 吞掉，
            // 或 throw new AppException("NOTIFICATION_FAILED");                    // 或翻譯成應用錯誤
        }
    }
}
```

---

## 情境 C：新增 Driving Adapter（例：加 CLI 管理工具）

1. **重用既有 use case**，不要為 CLI 寫一份新的邏輯
2. 新增 `<adapters>/cli/AdminCli.java`
3. 新增 CLI 的進入點（另一個 `main` 方法，或在既有進入點依參數分流），組裝方式與 HTTP 相同

```java
// FILE: <adapters>/cli/AdminCli.java
public class AdminCli {
    private final ReturnBook returnBook;

    public AdminCli(ReturnBook returnBook) { this.returnBook = returnBook; }

    // 用法：java -jar library-admin.jar return <loanId>
    public int run(String[] args) {
        if (args.length == 2 && "return".equals(args[0])) {
            try {
                ReturnBookOutput out = returnBook.execute(new ReturnBookInput(args[1]));
                System.out.println("歸還成功，罰金：" + out.getFine());
                return 0;
            } catch (DomainException e) {
                System.err.println("失敗：" + e.getCode());
                return 1;
            } catch (AppException e) {
                System.err.println("失敗：" + e.getCode());
                return 1;
            }
        }
        System.err.println("用法：return <loanId>");
        return 2;
    }
}
```

> Java 7 的 multi-catch 可以寫成 `catch (DomainException | AppException e)`，但兩者沒有共同的 `getCode()` 父型別時需各自處理；
> 若想合併，可讓兩者實作同一個 `HasErrorCode` 介面。

✅ **檢查點**：HTTP 與 CLI 呼叫的是**同一個** `ReturnBook` 類別；兩者之間沒有複製貼上的邏輯。

---

## 情境 D：Message Queue 消費者

與情境 C 相同，只是輸入來源是訊息（以 JMS 為例）：

```java
// FILE: <adapters>/messaging/BookDamagedListener.java
public class BookDamagedListener implements MessageListener {
    private final MarkBookDamaged markBookDamaged;

    public BookDamagedListener(MarkBookDamaged markBookDamaged) { this.markBookDamaged = markBookDamaged; }

    @Override
    public void onMessage(Message message) {
        try {
            String bookId = ((TextMessage) message).getText();
            markBookDamaged.execute(new MarkBookDamagedInput(bookId));
            message.acknowledge();                   // ack / 重試是 adapter 的責任
        } catch (JMSException e) {
            throw new IllegalStateException("讀取訊息失敗", e);   // 交給 MQ 重送
        }
    }
}
```
