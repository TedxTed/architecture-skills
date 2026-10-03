# 反模式（Anti-patterns）

每個反模式包含：**症狀 → 為什麼不好 → 修法（含前後對照）**。範例為 Java，相容 JDK 1.7。

---

## 1. 貧血模型（Anemic Domain Model）

**症狀**：Entity 只有欄位與 getter / setter，所有規則都寫在 use case（或 Service）。

```java
// ❌
public class Loan {
    private LocalDate dueDate;
    private int renewCount;
    // 只有 getter / setter
}

public class RenewLoan {
    public void execute(...) {
        if (loan.getRenewCount() >= 1) throw ...;
        if (now.toLocalDate().isAfter(loan.getDueDate())) throw ...;
        loan.setDueDate(loan.getDueDate().plusDays(14));
        loan.setRenewCount(loan.getRenewCount() + 1);
    }
}
```

**為什麼不好**：同一條規則會在多個 use case 中重複（例如管理員代續借），改規則要改好幾處。

```java
// ✅
public class Loan {
    public void renew(LocalDateTime now) { /* 規則集中於此 */ }
}

public class RenewLoan {
    public RenewLoanOutput execute(RenewLoanInput input) {
        // ...
        loan.renew(clock.now());
    }
}
```

---

## 2. 框架滲透（Framework Leak）

**症狀**：Domain entity 上有 JPA / Jackson 註解；use case 上有 Spring 註解或欄位注入。

```java
// ❌ domain
@Entity
@Table(name = "loans")
public class Loan {
    @Id private String id;
    @Column(name = "due_date") private Date dueDate;
}

// ❌ application
@Service
@Transactional
public class BorrowBook {
    @Autowired private LoanRepository loans;
}
```

**為什麼不好**：換 ORM、資料表結構改變、或換掉 Spring 時，domain 與 use case 被迫跟著改；無法在沒有框架的情況下測試。

**修法**：
- Domain entity 與 `LoanJpaEntity` 分開，在 `adapter/persistence/` 寫 mapper（見 [crossing-boundaries.md](../concepts/crossing-boundaries.md)）
- Use case 用建構子注入、不加註解，在 `@Configuration` 用 `@Bean` 建立（見 [04-frameworks-drivers.md](../layers/04-frameworks-drivers.md)）
- 交易改用 `UnitOfWork` port

---

## 3. 肥胖 Controller

**症狀**：controller 有業務判斷、直接呼叫 repository、或呼叫多個 use case 拼湊流程。

```java
// ❌
@RequestMapping(method = RequestMethod.POST)
public ResponseEntity<?> borrow(@RequestBody BorrowRequest req) {
    Member member = memberRepository.findById(new MemberId(req.getMemberId()));
    if (member.getTier() == MemberTier.VIP) { /* ... */ }
    borrowBook.execute(...);
    updateStats.execute(...);                  // 流程編排在 controller
}
```

**修法**：規則搬到 entity；流程編排搬到 use case。controller 只呼叫**一個** use case。

---

## 4. 上帝 Service

**症狀**：`LoanService` 有 `borrow`、`returnBook`、`renew`、`list`、`calculateFine`… 二十個方法、十個依賴。

**為什麼不好**：每個方法只用到部分依賴；測試要 mock 一堆無關的東西；多人修改易衝突。

**修法**：一個 use case 一個類別（`BorrowBook`、`ReturnBook`…），各自只注入需要的 port。

---

## 5. 洩漏的 Port（Leaky Port）

**症狀**：port 的形狀由資料庫決定，而不是由 use case 需求決定。

```java
// ❌
public interface LoanRepository {
    List<Map<String, Object>> query(String sql, Object... args);
    List<Loan> findBy(Map<String, Object> where);
    Connection beginTransaction();
}
```

**修法**：用業務語言、為需求命名。

```java
// ✅
public interface LoanRepository {
    List<Loan> findOpenByMember(MemberId memberId);
    void save(Loan loan);
}
```

---

## 6. 回傳 Entity 給外層

**症狀**：use case 回傳 `Loan` entity，controller 直接交給 Jackson 序列化成 JSON。

**為什麼不好**：外層可以呼叫 `loan.renew()` 繞過 use case；entity 內部欄位變動會直接改變 API 格式。

**修法**：回傳 Output DTO（`BorrowBookOutput`）。

---

## 7. 隱藏的時間依賴

**症狀**：entity 或 use case 直接呼叫 `new Date()`、`System.currentTimeMillis()`、`LocalDateTime.now()`、`Calendar.getInstance()`。

**為什麼不好**：「逾期」相關的測試無法穩定重現。

**修法**：use case 透過 `Clock` port 取得時間，以參數傳入 entity。

---

## 8. 過度設計（Over-engineering）

**症狀**：純 CRUD 也做了 4 層、每個 use case 都有輸入 port 介面 + presenter + 3 個 DTO，而且只有一個實作。

**為什麼不好**：成本大於效益；團隊會因此排斥架構。

**修法**：
- 純 CRUD → 考慮不用 Clean Architecture
- 只有一個實作的輸入 port → 省略介面
- 簡單輸出 → use case 直接回傳 DTO，不寫 presenter
- 見 [structure/choosing.md 精簡版](../structure/choosing.md#小專案精簡版a-或-b-都適用)

**不可省略的永遠是依賴方向。**

---

## 9. 共享 Entity 跨模組（by-feature 結構）

**症狀**：`lending` 模組直接 import `com.example.library.membership.domain.Member`。

**修法**：`lending` 定義自己的 `Borrower` 模型與 `BorrowerLookup` port。見 [by-feature.md](../structure/by-feature.md)。

---

## 10. 萬用 DTO

**症狀**：一個 `LoanDto` 同時當建立的輸入、更新的輸入、查詢結果、API 回應，大部分欄位可能是 null。

```java
// ❌
public class LoanDto {
    private String id, memberId, bookId, bookTitle;
    private Date dueDate, returnedAt;
    private Long fine;
    // 全部 getter / setter，不同用途各填一部分
}
```

**為什麼不好**：看不出每個操作真正需要哪些欄位；改一個 API 回應會影響所有 use case；欄位能不能是 null 全靠猜。

**修法**：每個 use case 自己的 Input / Output；API 格式另外定義 Request / Response。見 [dto.md](../concepts/dto.md)。

---

## 11. Adapter 繞過 use case 查資料

**症狀**：driven adapter 注入另一個 port 或 adapter，自己去查需要的資料。

```java
// ❌
public class SmtpNotifier implements Notifier {
    private final MailSender mailSender;
    private final MemberRepository members;                    // adapter 自己查會員

    @Override
    public void notifyBookBorrowed(MemberId memberId, String bookTitle, LocalDate dueDate) {
        Member member = members.findById(memberId);
        // ...
    }
}
```

**為什麼不好**：資料流不經過 use case，use case 看不出這個操作讀了哪些資料；adapter 之間形成隱藏的依賴，換掉 repository 會牽動 notifier。

**修法**：use case 查好資料，以 port 參數傳入（`notifyBookBorrowed(new Recipient(email, name), ...)`）。新管道需要更多資料時，擴充 port 的參數 DTO。

---

## 12. 交易內做副作用

**症狀**：在 `@Transactional` 方法或 `uow.run(...)` 裡寄 email / 呼叫外部 API。

**為什麼不好**：交易 rollback 後，信已經寄出了。

**修法**：副作用放在交易成功之後；需要保證時使用 Outbox pattern（見 [domain-events.md](../concepts/domain-events.md#可靠性outbox)）。
