# 反模式（Anti-patterns）

每個反模式包含：**症狀 → 為什麼不好 → 修法（含前後對照）**。

---

## 1. 貧血模型（Anemic Domain Model）

**症狀**：Entity 只有欄位與 getter/setter，所有規則都寫在 use case。

```
// ❌
ENTITY Loan { dueDate, renewCount }   // 沒有任何方法

USE_CASE RenewLoan
  IF loan.renewCount >= 1 FAIL ...
  IF now > loan.dueDate   FAIL ...
  loan.dueDate ← loan.dueDate + 14
  loan.renewCount ← loan.renewCount + 1
```

**為什麼不好**：同一條規則會在多個 use case 中重複（例如管理員代續借），改規則要改好幾處。

```
// ✅
ENTITY Loan
  FUNCTION renew(now) ...              // 規則集中於此

USE_CASE RenewLoan
  TRY loan.renew(clock.now())
```

---

## 2. 框架滲透（Framework Leak）

**症狀**：Entity 上有 ORM / 序列化註解；use case 繼承框架基底類別。

```
// ❌
@Table("loans")
ENTITY Loan
  @Column("due_date") dueDate
```

**為什麼不好**：換 ORM 或資料表結構改變時，domain 被迫跟著改；無法在沒有框架的情況下測試。

**修法**：分開 Entity 與 ORM model，於 `adapters/persistence/` 寫 mapper。見 [crossing-boundaries.md](../concepts/crossing-boundaries.md)。

---

## 3. 肥胖 Controller

**症狀**：controller 有業務判斷、直接呼叫 repository、或呼叫多個 use case 拼湊流程。

```
// ❌
FUNCTION handleBorrow(req)
  member ← memberRepo.findById(...)
  IF member.tier == VIP ...
  borrowBook.execute(...)
  updateStats.execute(...)            // 流程編排在 controller
```

**修法**：規則搬到 entity；流程編排搬到 use case。controller 只呼叫**一個** use case。

---

## 4. 上帝 Service

**症狀**：`LoanService` 有 `borrow`、`return`、`renew`、`list`、`calculateFine`… 二十個方法、十個依賴。

**為什麼不好**：每個方法只用到部分依賴；測試要 mock 一堆無關的東西；多人修改易衝突。

**修法**：一個 use case 一個類別（`BorrowBook`、`ReturnBook`…），各自只注入需要的 port。

---

## 5. 洩漏的 Port（Leaky Port）

**症狀**：port 的形狀由資料庫決定，而不是由 use case 需求決定。

```
// ❌
PORT LoanRepository
  FUNCTION query(sql: String) -> List<Row>
  FUNCTION findBy(where: Map) -> List<Loan>
  FUNCTION beginTransaction() -> Transaction
```

**修法**：用業務語言、為需求命名。

```
// ✅
PORT LoanRepository
  FUNCTION findOpenByMember(memberId) -> List<Loan>
  FUNCTION save(loan)
```

---

## 6. 回傳 Entity 給外層

**症狀**：use case 回傳 `Loan` entity，controller 直接序列化成 JSON。

**為什麼不好**：外層可以呼叫 `loan.renew()` 繞過 use case；entity 內部欄位變動會直接改變 API 格式。

**修法**：回傳 Output DTO。

---

## 7. 隱藏的時間依賴

**症狀**：entity 或 use case 直接呼叫 `now()` / `Date.now()` / `time.Now()`。

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

**症狀**：`lending` 模組直接 import `membership/domain/member`。

**修法**：`lending` 定義自己的 `Borrower` 模型與 `BorrowerLookup` port。見 [by-feature.md](../structure/by-feature.md)。

---

## 10. 交易內做副作用

**症狀**：在交易 commit 之前寄 email / 呼叫外部 API。

**為什麼不好**：交易 rollback 後，信已經寄出了。

**修法**：副作用放在交易成功之後；需要保證時使用 Outbox pattern。
