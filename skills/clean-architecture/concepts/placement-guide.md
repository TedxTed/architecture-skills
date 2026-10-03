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
    是 → application/use_cases/

Q6. 它是否是內層需要、但由外部提供的能力（存取、寄送、時間、ID）？
    是 → application/ports/ 放介面，adapters/ 放實作
```

## 快速對照表（圖書範例）

| 程式碼片段 | 放哪 | 原因 |
|---|---|---|
| `if member.suspended then fail` | `domain/member` | R1 是企業規則 |
| `dueDate = now + 14 days` | `domain/loan` | R5 是企業規則 |
| `fine = lateDays × 10` | `domain/loan` | R6 是企業規則 |
| `member ← members.findById(id); if none fail MemberNotFound` | `application/use_cases/borrow_book` | 載入資料是流程步驟 |
| `notifier.notifyBookBorrowed(...)` | `application/use_cases/borrow_book` | A1 是應用規則 |
| `interface LoanRepository` | `application/ports/` | 內層需要的能力 |
| `SELECT * FROM loans WHERE member_id = ?` | `adapters/persistence/` | 技術細節 |
| `req.body.memberId` → `BorrowBookInput` | `adapters/http/` | 格式轉換 |
| `BookNotAvailable` → HTTP 409 | `adapters/http/` | 錯誤翻譯 |
| `if (!isUuid(req.body.bookId)) return 400` | `adapters/http/` | 傳輸格式驗證 |
| `new SqlLoanRepository(db)` | `main` | 組裝 |
| `process.env.DATABASE_URL` | `infrastructure/config` | 讀設定 |
| 寄信的 HTML 模板 | `adapters/notification/` | 呈現細節 |
| 「VIP 會員借書不寄信」 | `application/use_cases/borrow_book` | 跟寄信這個「應用行為」有關 → 應用規則 |
| 「VIP 會員可借 5 本」 | `domain/member` | 跟借閱本身有關 → 企業規則 |

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
- 規則不屬於任何一個 → **Domain Service**（`domain/services/`，無狀態、純函式、不碰 port）
- 規則需要查資料庫才能判斷 → use case 先查好，再傳給 entity / domain service

### 查詢（Read）也要走 use case 嗎？

- 有任何邏輯（權限、組合多來源）→ 走 use case
- 純粹列表顯示 → 可以用 **Query Service port**（`application/ports/loan_queries`），回傳 read model DTO，不必載入 entity。仍然不可讓 controller 直接寫 SQL。
