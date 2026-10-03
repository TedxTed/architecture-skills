# Workflow：把既有程式重構成 Clean Architecture

適用：controller 裡塞滿 SQL 與業務判斷的「大泥球」程式。
**原則：漸進式。一次只搬一個功能，每一步都能跑、測試都能過。不要一次重寫。**

---

## 起點：典型的大泥球

```java
// FILE: src/main/java/com/example/library/web/LoanController.java   （重構前）
@Controller
public class LoanController {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MailSender mailSender;

    @RequestMapping(value = "/loans", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity<?> borrow(@RequestParam String memberId, @RequestParam String bookId) {
        List<Map<String, Object>> members = jdbc.queryForList("SELECT * FROM members WHERE id = ?", memberId);
        if (members.isEmpty()) return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
        Map<String, Object> member = members.get(0);
        if ((Boolean) member.get("suspended")) return new ResponseEntity<String>("停權", HttpStatus.UNPROCESSABLE_ENTITY);
        int count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM loans WHERE member_id = ? AND returned_at IS NULL", Integer.class, memberId);
        int limit = "VIP".equals(member.get("tier")) ? 5 : 3;
        if (count >= limit) return new ResponseEntity<String>("超過上限", HttpStatus.UNPROCESSABLE_ENTITY);
        Map<String, Object> book = jdbc.queryForMap("SELECT * FROM books WHERE id = ?", bookId);
        if (!"AVAILABLE".equals(book.get("status"))) return new ResponseEntity<String>(HttpStatus.CONFLICT);
        jdbc.update("UPDATE books SET status = 'ON_LOAN' WHERE id = ?", bookId);
        Calendar due = Calendar.getInstance();
        due.add(Calendar.DATE, 14);
        jdbc.update("INSERT INTO loans (...) VALUES (...)", /* ... */ due.getTime());
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo((String) member.get("email"));
        mail.setText("借書成功");
        mailSender.send(mail);                                        // 交易還沒 commit 就寄信
        return new ResponseEntity<Date>(due.getTime(), HttpStatus.CREATED);
    }
}
```

---

## Step 0 — 安全網：先寫特徵測試（Characterization Test）

在動任何程式碼之前，用 HTTP 層的測試**鎖定現在的行為**（包括怪異的行為）。
Spring 專案可用 `MockMvc`（Spring 3.2 以上）加上測試資料庫：

```java
// FILE: src/test/java/com/example/library/web/LoanControllerCharacterizationTest.java
public class LoanControllerCharacterizationTest {
    @Test public void POST_loans成功時回201與到期日() { /* mockMvc.perform(post("/loans")...) */ }
    @Test public void 停權會員回422() { /* ... */ }
    @Test public void 書已借出回409() { /* ... */ }
}
```

專案不寫自動化測試時：至少用 curl / Postman 把這些情境的**請求與回應記錄下來**，每一步重構後重打一次比對。

✅ **檢查點**：測試全過。之後每一步都要維持全過。

---

## Step 1 — 標記：在原檔案中標出每一行屬於哪一層

```java
List<Map<String, Object>> members = jdbc.queryForList(...);   // [ADAPTER-persistence]
if (members.isEmpty()) return ...NOT_FOUND;                   // [USE CASE] 流程 + [ADAPTER-web] 狀態碼
if ((Boolean) member.get("suspended")) return ...;            // [DOMAIN] R1
int limit = "VIP".equals(member.get("tier")) ? 5 : 3;          // [DOMAIN] R2
due.add(Calendar.DATE, 14);                                   // [DOMAIN] R5 + [PORT] Clock（Calendar.getInstance()）
mailSender.send(mail);                                        // [USE CASE] A1 + [ADAPTER] SMTP
@Transactional                                                // [ADAPTER] 交易 → UnitOfWork
```

這一步不改程式，只是讓你與使用者看見結構。

---

## Step 2 — 抽出 Domain（風險最低，收益最高）

1. 建立 `domain/Member.java`、`domain/Book.java`、`domain/Loan.java`
2. 把標記為 `[DOMAIN]` 的邏輯搬成 entity 方法
3. 原 controller 改成：查出 row → 轉成 entity → 呼叫方法
4. 為 entity 寫單元測試

```java
Map<String, Object> memberRow = jdbc.queryForMap("SELECT * FROM members WHERE id = ?", memberId);
Member member = toMemberEntity(memberRow);                 // 暫時寫在 controller 中，Step 3 再搬走
member.assertCanBorrow(openLoans, LocalDateTime.now());    // 規則已搬進 entity
```

✅ **檢查點**：特徵測試仍全過；domain 測試新增並通過。

---

## Step 3 — 抽出 Ports 與 Adapters

1. 為每種 `jdbc.query...` 的用途定義 port（`MemberRepository.findById`…）
2. 把 SQL 搬進 `adapter/persistence/sql/SqlXxxRepository.java`
3. `Calendar.getInstance()` / `new Date()` → `Clock` port；`MailSender` → `Notifier` port；`@Transactional` → `UnitOfWork` port
4. controller 改為呼叫 repository

✅ **檢查點**：controller 中不再出現 SQL 字串與 `JdbcTemplate`。

---

## Step 4 — 抽出 Use Case

1. 建立 `application/usecase/borrowbook/BorrowBook.java`
2. 把 controller 中剩下的「流程」搬進去
3. controller 只剩：解析 request → 呼叫 use case → 轉 response
4. 新增 use case 測試（in-memory fakes）

✅ **檢查點**：controller 方法少於約 20 行，沒有業務判斷的 `if`；寄信移到交易成功之後。

---

## Step 5 — 搬到 Composition Root

1. 把 `@Autowired` 欄位注入與 `new SqlXxxRepository(...)` 改成 `@Configuration` 裡的 `@Bean`，use case 不加 `@Service`
2. 加入依賴規則檢查（見 [languages/java.md](../languages/java.md#強制依賴規則)）

---

## Step 6 — 下一個功能

重複 Step 0–5。**第二個功能會快很多**，因為 entity、port、repository 大多已存在。

---

## 重構順序建議

挑選原則：**常改、但出錯時影響範圍小**的功能先做。

| 優先 | 挑選標準 |
|---|---|
| 1 | 常改、bug 多、但只影響單一流程的功能 |
| 2 | 即將要大改的功能（順便重構） |
| 後 | 影響範圍大的核心流程（結帳、權限）：等前面累積經驗、測試網較完整再做 |
| 最後 | 純 CRUD、很少動的功能（可能永遠不需要重構） |

## 安全網：讓每一步都能退回

| 技巧 | 做法 |
|---|---|
| **Facade 先行** | 舊的大型 service 先整個包在一個 port 後面（`LegacyBillingGateway implements BillingGateway`），新 use case 先呼叫它；之後再逐步把內部換掉 |
| **切換開關** | 新舊兩條路徑並存，用設定切換（`feature.new-borrow-flow=true`）；正式環境驗證無誤後才刪除舊路徑 |
| **Strangler（絞殺者）** | 對外的 API 路徑不變，內部一次只把一個端點改接到新的 use case |
| **組裝先集中** | Step 5 可以提早做：先把所有建立動作集中到 `@Configuration`，避免重構中又有新的依賴漏進內層 |

```java
// FILE: <adapters>/web/LoanController.java    （過渡期）
@Value("${feature.new-borrow-flow:false}")
private boolean useNewBorrowFlow;

@RequestMapping(value = "/loans", method = RequestMethod.POST)
public ResponseEntity<?> borrow(@RequestParam String memberId, @RequestParam String bookId) {
    if (useNewBorrowFlow) {
        return newFlow(memberId, bookId);          // 呼叫 BorrowBook use case
    }
    return legacyBorrow(memberId, bookId);         // 原本的程式，暫時保留
}
```

切換開關本身是暫時的技術債：記入 `debt.md`，舊路徑刪除時一併移除。

## 與使用者溝通

- 每完成一個 Step 回報一次，讓使用者可以隨時停下
- 明確告知：「這一步只搬移，不改變行為」
- 若發現既有行為看起來是 bug，**先記錄、不要順手修**，等重構完成後另外處理
