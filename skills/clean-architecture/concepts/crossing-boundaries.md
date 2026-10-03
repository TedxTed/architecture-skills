# 跨越邊界：資料、錯誤、交易

這三件事是實作時最常出錯的地方。

---

## 1. 資料怎麼跨層

DTO 的種類、位置、轉換責任、驗證、PATCH 處理，見 **[dto.md](dto.md)**。本節只補充 Entity 與儲存格式之間的轉換。

| 規則 | 原因 |
|---|---|
| Repository 進出的是 **Entity**，不是 DB row | Port 用內層語言 |
| ORM model ≠ Entity，兩者之間用 mapper 轉 | Entity 不該被資料表結構綁架 |

### 要不要 Entity 直接當 ORM model？

| 情境 | 建議 |
|---|---|
| 正式專案、業務規則多 | **分開**。Entity 在 domain，ORM model 在 adapters/persistence，寫 mapper |
| 原型、CRUD 為主、規則很少 | 可以考慮不用 Clean Architecture（見 [structure/choosing.md](../structure/choosing.md)） |

Mapper 範例：

```
// FILE: <adapters>/persistence/loan_mapper.x
FUNCTION toEntity(row: LoanRow) -> Loan
  RETURN Loan(
    id:         LoanId(row.id),
    memberId:   MemberId(row.member_id),
    bookId:     BookId(row.book_id),
    borrowedAt: row.borrowed_at,
    dueDate:    row.due_date,
    returnedAt: row.returned_at,          // null ⇄ Nothing
    renewCount: row.renew_count)

FUNCTION toRow(loan: Loan) -> LoanRow
  RETURN LoanRow(id: loan.id.value, member_id: loan.memberId.value, ...)
```

> 若 entity 欄位是 private，提供 `Loan.reconstitute(...)`（不檢查規則、僅用於還原）給 mapper 用，
> 與用於「新建」的 `Loan.open(...)` 區分。

---

## 2. 錯誤怎麼跨層

**錯誤分三類，各在不同層定義：**

| 類別 | 定義在 | 範例 |
|---|---|---|
| 領域錯誤 | `domain/errors` | `BookNotAvailable`、`LoanLimitExceeded`、`MemberSuspended` |
| 應用錯誤 | `application/errors` | `MemberNotFound`、`BookNotFound` |
| 技術錯誤 | 不定義，由 adapter 包裝或直接往上丟 | DB 斷線、timeout |

**翻譯只在最外層的 adapter 做一次：**

```
// FILE: <adapters>/http/error_mapping.x
FUNCTION toHttpStatus(error) -> (Int, String)
  MATCH error
    MemberNotFound, BookNotFound              → (404, error.code)
    BookNotAvailable, HasOverdueLoans         → (409, error.code)
    MemberSuspended, LoanLimitExceeded        → (422, error.code)
    InvalidInput                              → (400, error.code)
    otherwise                                 → (500, "INTERNAL_ERROR")   // 記 log，不洩漏細節
```

| ❌ 錯誤做法 | 原因 |
|---|---|
| Entity 拋 `HttpException(409)` | domain 知道了 HTTP |
| Repository 把 `SQLException` 直接往上丟給 controller 處理 | 外層 adapter 依賴另一個 adapter 的細節 |
| Use case 回傳 `{ status: 404 }` | 應用層知道了 HTTP |

**例外 vs Result 型別**：依語言慣例（見 `languages/`）。Pseudocode 中 `FAIL` 代表「回報錯誤」，兩種都可以。
同一個專案內保持一致即可。

---

## 3. 交易（Transaction）怎麼處理

問題：借書要同時改 `Book.status` 和新增 `Loan`，必須在同一個交易。但 use case 不能 import DB。

**解法：`UnitOfWork` port**

```
// FILE: <application>/ports/unit_of_work.x
PORT UnitOfWork
  FUNCTION run(work: Function) -> Result

// FILE: <application>/use_cases/borrow_book/borrow_book.x  （片段）
  uow.run(() ->
    books.save(book)
    loans.save(loan)
  )

// FILE: <adapters>/persistence/sql_unit_of_work.x
ADAPTER SqlUnitOfWork IMPLEMENTS UnitOfWork
  DEPENDS ON db: DatabaseConnection
  FUNCTION run(work)
    tx ← db.begin()
    TRY
      work()        // repository 需透過「目前交易」取得連線（實作方式見 languages/）
      tx.commit()
    ON ERROR e
      tx.rollback()
      FAIL e
```

**副作用（寄信）放在交易之外、成功之後**，避免「信寄了但交易 rollback」。
若需要保證一致性，用 Outbox pattern（在交易中寫入 outbox 表，另一個程序負責寄送）。

### 替代方案

| 方案 | 適用 |
|---|---|
| UnitOfWork port（上面） | 預設推薦 |
| Use case decorator：`TransactionalBorrowBook(inner, db)` 放在 adapters，包住整個 use case | Use case 一律是一個交易時，最乾淨 |
| 框架的宣告式交易（`@Transactional`）| **只能**加在 adapter 或 decorator 上，不能加在 use case 上 |
