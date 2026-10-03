# 這段程式碼該放哪？—— 判斷指南

## 決策流程

依序回答，**第一個答「是」的就是答案**：

```
Q1. 它是否 import 了框架 / DB driver / HTTP / SDK，或在讀環境變數？
    是 → adapters/（翻譯用途）或 infrastructure/（啟動、連線、設定）

Q2. 它是否在「建立物件並接在一起」？
    是 → main / composition root

Q3. 它是否在做格式轉換（JSON、DB row、HTTP status ⇄ 內部型別）？
    是 → adapters/

Q4. 拿掉這套軟體，圖書館櫃台人員是否仍會遵守這條規則？
    是 → domain/（Entity 方法、Value、Domain Service）

Q5. 它是否在描述「系統執行某個操作的步驟順序」（讀 → 處理 → 存 → 通知）？
    是 → application/usecase/

Q6. 它是否是內層需要、但由外部提供的能力（存取、寄送、時間、ID）？
    是 → application/port/ 放介面，adapter/ 放實作
```

## 快速對照表（圖書範例）

| 程式碼片段 | 放哪 | 原因 |
|---|---|---|
| `if (suspended) throw new MemberSuspendedException()` | `domain/Member.java` | R1 是企業規則 |
| `dueDate = now.toLocalDate().plusDays(14)` | `domain/Loan.java` | R5 是企業規則 |
| `fine = lateDays * 10` | `domain/Loan.java` | R6 是企業規則 |
| `Member member = members.findById(id); if (member == null) throw ...` | `application/usecase/borrowbook` | 載入資料是流程步驟 |
| `notifier.notifyBookBorrowed(...)` | `application/usecase/borrowbook/BorrowBook.java` | A1 是應用規則 |
| `interface LoanRepository` | `application/port/` | 內層需要的能力 |
| `SELECT * FROM loans WHERE member_id = ?` | `adapter/persistence/sql/` | 技術細節 |
| `request.getMemberId()` → `BorrowBookInput` | `adapter/web/` | 格式轉換 |
| `BookNotAvailableException` → HTTP 409 | `adapter/web/ErrorMapping.java` | 錯誤翻譯 |
| `@NotNull @Size(max = 64)`、格式不符回 400 | `adapter/web/` | 傳輸格式驗證 |
| `new SqlLoanRepository(dataSource)` | `config/`（組裝類別） | 組裝 |
| `config.getProperty("db.url")`、`System.getenv(...)` | `config/` | 讀設定 |
| 寄信的 HTML 模板 | `adapter/notification/` | 呈現細節 |
| 「VIP 會員借書不寄信」 | `application/usecase/borrowbook/BorrowBook.java` | 跟寄信這個「應用行為」有關 → 應用規則 |
| 「VIP 會員可借 5 本」 | `domain/Member.java` | 跟借閱本身有關 → 企業規則 |

## 灰色地帶

### 驗證：哪種放哪？

| 驗證類型 | 例子 | 放哪 |
|---|---|---|
| 傳輸格式 | JSON 欄位存在、型別正確、字串長度 | adapter（controller） |
| 值的合法性 | email 格式、id 格式 | domain 的 Value 建構子（`Email.of(str)`） |
| 存在性 | 會員是否存在 | use case（查 port 後判斷） |
| 業務規則 | 能不能借 | domain entity |

### 跨多個 Entity 的規則放哪？

- 規則主要關於某一個 entity → 放那個 entity，其他的當參數傳入（例：`member.assertCanBorrow(openLoans, now)`）
- 規則不屬於任何一個 → **Domain Service**（`domain/service/`，無狀態、純函式、不碰 port）
- 規則需要查資料庫才能判斷 → use case 先查好，再傳給 entity / domain service

### 查詢（Read）也要走 use case 嗎？

- 有任何邏輯（權限、組合多來源）→ 走 use case
- 純粹列表顯示 → 可以用 **Query Service port**（`application/port/LendingQueries`），回傳 read model DTO，不必載入 entity。仍然不可讓 controller 直接寫 SQL。
- 多個純查詢 use case（沒有規則、只有一行委派）可以**合併成一個查詢類別**（例如 `LendingQueryService` 有 `listBooks()`、`listMemberLoans()` 多個方法）；寫入型 use case 仍維持一個操作一個類別。

### Log 放哪？

| Log 種類 | 例子 | 怎麼做 |
|---|---|---|
| 技術 log | 請求進出、DB 錯誤、外部 API 逾時、未預期例外 | 在 **adapter** 與 **main** 直接用 log 函式庫 |
| 業務稽核紀錄 | 「誰在何時借了哪本書」需要被查詢或保存 | 當成業務資料：用 **port**（`AuditLog.record(...)`）或 domain event，由 adapter 寫入 |
| use case 的除錯訊息 | 想知道流程走到哪 | 盡量不寫；真的需要時注入 `Logger` port，不要直接 import log 函式庫 |

Domain 層**不寫 log**：有需要記錄的事，回報錯誤或記錄 domain event，由外層決定要不要寫 log。
