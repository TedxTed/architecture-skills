# 第 3 層：Interface Adapters（介面轉接）

> 位置：`<adapters>`｜依賴：Use Cases、Entities、外部函式庫｜被誰使用：Frameworks & Drivers（組裝、路由）

## 職責

**翻譯**。把外面的格式（HTTP、CLI 參數、CSV、DB row、第三方 API）轉成內層看得懂的，
把內層的結果轉成外面要的。**Adapter 不做業務判斷。**

## 兩個方向

| | Driving（輸入端 / Primary） | Driven（輸出端 / Secondary） |
|---|---|---|
| 誰呼叫誰 | 外界 → adapter → use case | use case → port → adapter → 外界 |
| 範例 | HTTP controller、CLI handler、MQ consumer | CSV / SQL repository、SMTP notifier、SystemClock |
| 依賴 | use case 類別（或 input port） | 實作 `<application>` 定義的 port |

## 遵循規則

| 必須 | 禁止 |
|---|---|
| 只做三件事：**轉換、委派、翻譯錯誤** | 業務判斷（`if member.tier == VIP`） |
| Controller 一個請求只呼叫**一個** use case | 在 controller 編排多個 use case（那是新的 use case） |
| Repository 進出的是 **entity** | 回傳 ORM model / DB row 給 use case |
| 外部錯誤翻譯成內層錯誤；內層錯誤翻譯成外部格式 | 讓 `SQLException`、SDK 錯誤型別往內洩漏 |
| 錯誤對應（領域錯誤 → HTTP 狀態碼）集中在一處 | 每個 controller 各寫一套錯誤對應 |
| 第三方 SDK、ORM、框架註解只出現在本層 | adapter 之間直接互相呼叫，或 adapter 注入別的 port 繞過 use case 查資料 |

## 放什麼 / 不放什麼

| 種類 | 方向 | 範例 | 做什麼 |
|---|---|---|---|
| Web adapter / CLI | Driving | `LoanWebAdapter` + 框架綁定（`LoanServlet`）、`AdminCli` | 解析輸入 → Input DTO → 呼叫 use case → 轉回應 |
| Presenter（選用） | Driving | `BorrowBookJsonPresenter` | Output DTO → JSON / HTML |
| Error Mapping | Driving | `ErrorMapping` | 領域 / 應用錯誤 → HTTP 狀態碼 |
| Repository 實作 | Driven | `CsvLoanRepository`、`SqlLoanRepository` | Entity ⇄ 儲存格式 |
| Gateway | Driven | `SmtpNotifier`、`SystemClock` | 呼叫外部服務 |
| Mapper | 雙向 | `LoanMapper` | 純轉換函式 |
| Request / Response Model | Driving | `BorrowRequest`、`BorrowResponse` | API 格式與傳輸驗證（見 [dto.md](../concepts/dto.md)） |
| ORM model / Row | Driven | `LoanRow` | 資料表結構（也可放 infrastructure） |
| ❌ 不放 | — | — | 業務規則、流程編排、組裝（`new` 其他 adapter） |

## 怎麼寫（步驟）

**Driven（先做，見 [csv-first.md](../concepts/csv-first.md)）**
1. 找到要實作的 port，不修改它
2. 寫 mapper：entity ⇄ row（`reconstitute` 還原，讀欄位寫出）
3. 先實作 CSV 版本 → 通過 contract test
4. 使用者確認功能後，才實作 SQL 版本 → 通過同一套 contract test

**Driving**
1. 解析並驗證**傳輸格式**（欄位存在、型別正確）→ 不合法回 400
2. 組成 Input DTO，呼叫 use case
3. 成功：Output DTO → 回應格式；失敗：交給 error mapping

## 範例程式碼

Java，相容 JDK 1.7。Web adapter 拆成兩部分，**預設不依賴任何框架**：

- **轉換與錯誤對應**（`LoanWebAdapter`、`ErrorMapping`）：普通 Java 類別，換框架時不用改
- **框架綁定**（Servlet / Spring MVC / JAX-RS…）：只有幾行，把框架的 request / response 接到上面的類別

```java
// FILE: <adapters>/web/LoanWebAdapter.java            （Driving；不依賴任何框架）
public class LoanWebAdapter {
    private final BorrowBook borrowBook;

    public LoanWebAdapter(BorrowBook borrowBook) { this.borrowBook = borrowBook; }

    // 輸入：已從 HTTP 取出的欄位；輸出：狀態碼與回應內容
    public WebResponse borrow(String memberId, String bookId) {
        if (memberId == null || bookId == null) {
            return ErrorMapping.toResponse("INVALID_INPUT");          // 傳輸格式驗證
        }
        try {
            BorrowBookOutput out = borrowBook.execute(new BorrowBookInput(memberId, bookId));
            Map<String, String> body = new LinkedHashMap<String, String>();
            body.put("loanId", out.getLoanId());
            body.put("dueDate", out.getDueDate().toString());       // ISO 8601：2026-10-17
            return new WebResponse(201, body);
        } catch (DomainException e) {
            return ErrorMapping.toResponse(e.getCode());
        } catch (AppException e) {
            return ErrorMapping.toResponse(e.getCode());
        }
    }
}
// WebResponse：final class，欄位為 int status 與 Map<String, String> body

// FILE: <adapters>/web/ErrorMapping.java              （錯誤碼 → HTTP 狀態碼，集中一處）
public final class ErrorMapping {
    private static final Map<String, Integer> STATUS = new HashMap<String, Integer>();
    static {
        STATUS.put("INVALID_INPUT", 400);
        STATUS.put("MEMBER_NOT_FOUND", 404);
        STATUS.put("BOOK_NOT_FOUND", 404);
        STATUS.put("BOOK_NOT_AVAILABLE", 409);
        STATUS.put("HAS_OVERDUE_LOANS", 409);
        STATUS.put("MEMBER_SUSPENDED", 422);
        STATUS.put("LOAN_LIMIT_EXCEEDED", 422);
    }

    public static WebResponse toResponse(String code) {
        Map<String, String> body = new LinkedHashMap<String, String>();
        body.put("error", code);
        Integer status = STATUS.get(code);
        return new WebResponse(status != null ? status : 400, body);
    }
}

// FILE: <adapters>/web/LoanServlet.java               （框架綁定：Servlet）
public class LoanServlet extends HttpServlet {
    private LoanWebAdapter adapter;

    @Override
    public void init() {
        adapter = (LoanWebAdapter) getServletContext().getAttribute("loanWebAdapter");   // 由組裝類別放入
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        WebResponse r;
        try {
            r = adapter.borrow(req.getParameter("memberId"), req.getParameter("bookId"));
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(LoanServlet.class).error("未預期的錯誤", e);   // 技術 log 在 adapter
            r = new WebResponse(500, Collections.singletonMap("error", "INTERNAL_ERROR"));
        }
        resp.setStatus(r.getStatus());
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(Json.write(r.getBody()));   // 用專案既有的 JSON 函式庫（Jackson、Gson…）
    }
}
```

使用 Spring MVC、JAX-RS 或公司自有框架時，只要換掉「框架綁定」那一個類別，寫法見 [languages/java.md 的「使用 Spring 時」](../languages/java.md#使用-spring-時)。
已經深度使用框架的專案，也可以讓 controller 直接呼叫 use case、錯誤對應改用框架機制（例如 Spring 的 `@ControllerAdvice`）；
只要遵守「不做業務判斷、只呼叫一個 use case」即可，選擇記入 `conventions.md`。

```java
// FILE: <adapters>/persistence/LoanMapper.java          （CSV 與 SQL 共用）
public final class LoanMapper {
    public static final String[] COLUMNS =
            {"id", "member_id", "book_id", "borrowed_at", "due_date", "returned_at", "renew_count"};

    public static Loan toEntity(Map<String, String> row) {
        String returnedAt = row.get("returned_at");
        return Loan.reconstitute(
                new LoanId(row.get("id")), new MemberId(row.get("member_id")), new BookId(row.get("book_id")),
                LocalDateTime.parse(row.get("borrowed_at")), LocalDate.parse(row.get("due_date")),
                returnedAt.isEmpty() ? null : LocalDateTime.parse(returnedAt),
                Integer.parseInt(row.get("renew_count")));
    }

    public static Map<String, String> toRow(Loan loan) {
        Map<String, String> row = new LinkedHashMap<String, String>();
        row.put("id", loan.getId().getValue());
        row.put("member_id", loan.getMemberId().getValue());
        row.put("book_id", loan.getBookId().getValue());
        row.put("borrowed_at", loan.getBorrowedAt().toString());
        row.put("due_date", loan.getDueDate().toString());
        row.put("returned_at", loan.getReturnedAt() == null ? "" : loan.getReturnedAt().toString());
        row.put("renew_count", String.valueOf(loan.getRenewCount()));
        return row;
    }
}

// FILE: <adapters>/persistence/csv/CsvLoanRepository.java     （Driven，先做）
public class CsvLoanRepository implements LoanRepository {
    private static final String FILE = "loans.csv";
    private final CsvStore store;

    public CsvLoanRepository(CsvStore store) { this.store = store; }

    @Override
    public List<Loan> findOpenByMember(MemberId memberId) {
        List<Loan> result = new ArrayList<Loan>();
        for (Map<String, String> row : store.readAll(FILE)) {
            if (row.get("member_id").equals(memberId.getValue()) && row.get("returned_at").isEmpty()) {
                result.add(LoanMapper.toEntity(row));
            }
        }
        return result;
    }

    @Override
    public void save(Loan loan) {
        store.upsert(FILE, LoanMapper.toRow(loan), LoanMapper.COLUMNS);   // 原地取代，保持列順序
    }

    // findById、nextId 略（nextId：return new LoanId(UUID.randomUUID().toString());）
}

// FILE: <adapters>/notification/SmtpNotifier.java       （Driven，Gateway）
public class SmtpNotifier implements Notifier {
    private final MailSender mailSender;                 // 不注入 repository：需要的資料由 use case 傳入

    public SmtpNotifier(MailSender mailSender) { this.mailSender = mailSender; }

    @Override
    public void notifyBookBorrowed(Recipient to, String bookTitle, LocalDate dueDate) {
        try {
            mailSender.send(to.getEmail(), "借書成功", to.getName() + " 您好，《" + bookTitle + "》到期日 " + dueDate);
        } catch (MailException e) {
            LoggerFactory.getLogger(SmtpNotifier.class).warn("寄信失敗", e);   // 外部錯誤不往內洩漏
        }
    }
}

// FILE: <adapters>/time/SystemClock.java
public class SystemClock implements Clock {
    @Override
    public LocalDateTime now() { return LocalDateTime.now(); }
}
```

## 與其他層的配合

```
  外界                     Interface Adapters（本層）                    內層
 ───────                  ─────────────────────────                   ──────
 HTTP 請求 ──▶ Controller ──解析/驗證格式──▶ InputDTO ──execute──▶ Use Case
 HTTP 回應 ◀── Controller ◀──轉換格式／error mapping── OutputDTO / 錯誤 ◀──┘
                                                                        │
                                                  呼叫 port（介面在內層）│
                                                                        ▼
 CSV / DB  ◀──▶ CsvLoanRepository / SqlLoanRepository ◀── mapper ──▶ Entity
 SMTP      ◀──  SmtpNotifier
 OS 時間   ──▶  SystemClock
```

| 對象 | 關係 | 跨邊界傳什麼 |
|---|---|---|
| Use Cases | Driving：**我呼叫它**；Driven：**我實作它定義的 port** | Input / Output DTO；port 的參數與回傳（entity、domain 型別） |
| Entities | mapper **建構與讀取**它（`reconstitute`、讀欄位） | Entity ⇄ row |
| Frameworks & Drivers | **組裝我**、把連線 / 設定注入給我、把路由接到我 | DB 連線、SMTP client、設定值 |
| 外界 | **我直接面對**：HTTP、檔案、DB、第三方 API | 外部格式 |
| 其他 adapter / 其他 port | **不互相呼叫，也不注入別的 port 自己查資料**。adapter 需要的資料由 use case 查好傳入（例：use case 把會員 email 傳給 Notifier，而不是 SmtpNotifier 自己查 MemberRepository） | — |

**關鍵**：所有「外部格式 ⇄ 內部型別」的轉換都在這一層完成，而且**只在這一層**。

## 測試

| 對象 | 方式 |
|---|---|
| Controller | 用假的 use case，驗證「request → InputDTO」與「OutputDTO / 錯誤 → response」 |
| Repository | **Contract test**：InMemory、CSV、SQL 跑同一套測試（見 [add-adapter.md](../workflows/add-adapter.md#repository-contract-test強烈建議)） |
| Gateway | 用假的外部 client，驗證呼叫參數與錯誤翻譯 |
| Mapper | 單元測試：entity → row → entity 結果相同 |

## 自我檢查

- [ ] 沒有業務判斷；controller 只呼叫一個 use case
- [ ] Repository 回傳 entity，不是 row / ORM model
- [ ] 外部錯誤型別沒有往內洩漏；錯誤對應集中一處
- [ ] 所有 repository 實作通過同一套 contract test

常見錯誤：肥胖 controller、框架滲透 → [anti-patterns.md](../review/anti-patterns.md)
