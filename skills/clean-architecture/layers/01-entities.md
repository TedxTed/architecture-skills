# 第 1 層：Entities（企業業務規則）

> 位置：`<domain>`｜依賴：**無**（最內層）｜被誰使用：Use Cases、Adapters（mapper）

## 職責

表達「業務本身」的規則。**就算明天不用電腦、改用紙本，這些規則仍然成立。**
例：停權會員不能借書、借期 14 天、逾期每天罰 10 元。

## 遵循規則

| 必須 | 禁止 |
|---|---|
| 規則寫成 entity 的**方法**（行為），不是 use case 裡的 `if` | import 任何外層、框架、ORM、HTTP、SDK |
| 時間、亂數等外部值**用參數傳入**（`renew(now)`） | 直接呼叫 `now()`、`random()`、`uuid()` |
| 方法名稱用業務動詞（`markAsLent`、`renew`） | `setStatus()`、`setDueDate()` 這類 setter |
| 違反規則時回報**領域錯誤**（`BookNotAvailable`） | 回報 HTTP 狀態碼或框架例外 |
| 狀態只能透過方法改變，欄位對外唯讀 | 欄位上有 `@Entity`、`@Column`、`@JsonProperty` |
| 用 Value 包裝有意義的值（`LoanId`、`Money`） | 知道「誰呼叫我」、知道資料怎麼存 |

## 放什麼 / 不放什麼

| 種類 | 說明 | 範例 |
|---|---|---|
| Entity | 有 id、有生命週期、有行為 | `Member`、`Book`、`Loan` |
| Value | 無 id、不可變、以值比較相等 | `MemberId`、`Money`、`Email` |
| Domain Error | 違反業務規則 | `BookNotAvailable`、`LoanLimitExceeded` |
| Domain Service | 不屬於單一 entity 的規則，無狀態純函式 | `FineCalculator`（若罰金規則跨多個 entity） |
| ❌ 不放 | Repository、DTO、Port、通知、log、設定值讀取 | — |

## 怎麼寫（步驟）

1. 從需求找出**名詞** → entity / value；**動詞** → entity 方法
2. 每條業務規則對應到「它主要關於哪個 entity」，寫成該 entity 的方法
3. 需要外部資訊（時間、其他 entity 的資料）→ 加成方法參數
4. 每種違規定義一個領域錯誤
5. 提供兩種建構方式：`open(...)`（新建，檢查規則）與 `reconstitute(...)`（從儲存還原，不檢查）
6. 寫單元測試：每條規則至少一個成功、一個失敗

## Pseudocode

```
// FILE: <domain>/ids.x
VALUE LoanId   { value: String }        // 建構時檢查非空
VALUE MemberId { value: String }
VALUE BookId   { value: String }

// FILE: <domain>/errors.x
DOMAIN_ERROR MemberSuspended          code "MEMBER_SUSPENDED"
DOMAIN_ERROR LoanLimitExceeded        code "LOAN_LIMIT_EXCEEDED"
DOMAIN_ERROR HasOverdueLoans          code "HAS_OVERDUE_LOANS"
DOMAIN_ERROR BookNotAvailable         code "BOOK_NOT_AVAILABLE"
DOMAIN_ERROR CannotRenewOverdueLoan   code "CANNOT_RENEW_OVERDUE_LOAN"
DOMAIN_ERROR RenewLimitExceeded       code "RENEW_LIMIT_EXCEEDED"
DOMAIN_ERROR LoanAlreadyClosed        code "LOAN_ALREADY_CLOSED"

// FILE: <domain>/loan.x
ENTITY Loan
  id:         LoanId
  memberId:   MemberId
  bookId:     BookId
  borrowedAt: DateTime
  dueDate:    Date
  returnedAt: DateTime | Nothing
  renewCount: Int

  CONSTANT LOAN_DAYS    = 14                       // R5
  CONSTANT FINE_PER_DAY = Money(10)                // R6
  CONSTANT MAX_RENEW    = 1                        // R7

  // 新建：套用規則
  STATIC FUNCTION open(id, memberId, bookId, now: DateTime) -> Loan
    RETURN Loan(id, memberId, bookId, borrowedAt: now,
                dueDate: now.date + LOAN_DAYS, returnedAt: Nothing, renewCount: 0)

  // 還原：給 repository mapper 用，不檢查規則
  STATIC FUNCTION reconstitute(id, memberId, bookId, borrowedAt, dueDate, returnedAt, renewCount) -> Loan

  FUNCTION isOverdue(now: DateTime) -> Bool
    RETURN returnedAt == Nothing AND now.date > dueDate

  FUNCTION close(now: DateTime) -> Money            // 回傳罰金
    IF returnedAt != Nothing  FAIL LoanAlreadyClosed
    returnedAt ← now
    RETURN FINE_PER_DAY × max(0, now.date - dueDate)

  FUNCTION renew(now: DateTime)
    IF isOverdue(now)           FAIL CannotRenewOverdueLoan     // R7
    IF renewCount >= MAX_RENEW  FAIL RenewLimitExceeded         // R7
    dueDate    ← dueDate + LOAN_DAYS
    renewCount ← renewCount + 1

  MUST NOT import application, adapters, infrastructure, any framework

// FILE: <domain>/member.x
ENUM MemberTier { REGULAR, VIP }

ENTITY Member
  id: MemberId, email: Email, tier: MemberTier, suspended: Bool

  FUNCTION loanLimit() -> Int
    RETURN tier == VIP ? 5 : 3                                    // R2

  // 跨 entity 的規則：其他 entity 的資料由參數傳入
  FUNCTION assertCanBorrow(openLoans: List<Loan>, now: DateTime)
    IF suspended                             FAIL MemberSuspended       // R1
    IF openLoans.count >= loanLimit()        FAIL LoanLimitExceeded     // R2
    IF openLoans.any(l -> l.isOverdue(now))  FAIL HasOverdueLoans       // R3

// FILE: <domain>/book.x
ENUM BookStatus { AVAILABLE, ON_LOAN }

ENTITY Book
  id: BookId, title: String, status: BookStatus

  FUNCTION markAsLent()
    IF status != AVAILABLE  FAIL BookNotAvailable                 // R4
    status ← ON_LOAN

  FUNCTION markAsReturned()
    status ← AVAILABLE
```

## 與其他層的配合

```
        ┌──────────────────────────── Use Cases ────────────────────────────┐
        │  BorrowBook：                                                      │
        │    member ← members.findById(...)      ← 透過 port 取得 entity     │
        │    member.assertCanBorrow(loans, now)  ← 呼叫 entity 方法執行規則  │
        │    loan ← Loan.open(..., now)          ← 用工廠建立新 entity       │
        │    loans.save(loan)                    ← 把 entity 交給 port       │
        └───────────────┬───────────────────────────────────────────────────┘
                        │ 呼叫方法、傳入參數（時間、其他 entity）
                        ▼
        ┌────────────── Entities ──────────────┐
        │  回傳：結果值 / 改變自身狀態 / FAIL    │
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

```
// FILE: <tests>/domain/loan_test.x
TEST "逾期的借閱不能續借"
  loan ← Loan.open(LoanId("l1"), MemberId("m1"), BookId("b1"), now: 2026-01-01)
  EXPECT loan.renew(now: 2026-01-20) FAILS WITH CannotRenewOverdueLoan

TEST "逾期 3 天歸還，罰金 30 元"
  loan ← Loan.open(..., now: 2026-01-01)                 // 到期 2026-01-15
  EXPECT loan.close(now: 2026-01-18) == Money(30)
```

## 自我檢查

- [ ] `<domain>` 內沒有任何 import 指向外層或框架
- [ ] 沒有呼叫 `now()` / `random()` / `uuid()`
- [ ] 每條業務規則都在 entity 方法內，use case 中找不到同樣的判斷
- [ ] 測試不需要任何 mock

常見錯誤：貧血模型、框架滲透、隱藏的時間依賴 → [anti-patterns.md](../review/anti-patterns.md)
