# 共用範例領域：圖書借閱系統

本專案所有架構 skill（Clean、Hexagonal、DDD…）都使用**同一個範例領域**。
這樣做的目的：當你比較不同架構時，差異只在「程式碼怎麼擺」，而不是「業務是什麼」。

> AI 使用說明：當使用者的專案沒有指定領域時，用這份文件理解範例；
> 當使用者有自己的領域時，把範例中的名詞**對應替換**成使用者的名詞，結構不變。

---

## 1. 名詞（Ubiquitous Language）

| 名詞 | 英文 | 說明 |
|---|---|---|
| 會員 | Member | 可以借書的人，有等級（一般 / VIP）與狀態（正常 / 停權） |
| 書 | Book | 一本實體書（簡化：一筆 Book = 一本實體館藏） |
| 借閱 | Loan | 一次借書紀錄，從借出到歸還 |
| 到期日 | DueDate | 應歸還日期 |
| 罰金 | Fine | 逾期歸還產生的費用 |

## 2. 業務規則

| 編號 | 規則 | 屬於哪一類 |
|---|---|---|
| R1 | 停權會員不能借書 | 企業規則（Entity） |
| R2 | 一般會員最多同時借 3 本，VIP 最多 5 本 | 企業規則（Entity） |
| R3 | 會員有任何逾期未還的書時，不能再借 | 企業規則（Entity） |
| R4 | 只有「可借」狀態的書可以被借出 | 企業規則（Entity） |
| R5 | 借期 14 天 | 企業規則（Entity） |
| R6 | 逾期歸還，每逾期一天罰 10 元 | 企業規則（Entity） |
| R7 | 每筆借閱最多續借 1 次，續借延長 14 天；已逾期不可續借 | 企業規則（Entity） |
| A1 | 借書成功後要寄通知 email 給會員 | 應用規則（Use Case） |
| A2 | 借書 API 必須回傳到期日 | 應用規則（Use Case Output） |

> **企業規則 vs 應用規則**：
> 企業規則 = 就算沒有這套軟體，圖書館櫃台人員也會照做的規則。
> 應用規則 = 因為「這套軟體」才存在的流程（寄 email、回傳格式）。

## 3. 狀態

```
Book.status:   AVAILABLE ──借出──▶ ON_LOAN ──歸還──▶ AVAILABLE

Loan:          OPEN (returnedAt = null) ──歸還──▶ CLOSED (returnedAt = 某時間)
               OPEN 且 now > dueDate     ⇒ 逾期 (overdue)
```

## 4. 用例（Use Cases）

| 用例 | 輸入 | 輸出 | 會用到的規則 |
|---|---|---|---|
| BorrowBook 借書 | memberId, bookId | loanId, dueDate | R1–R5, A1, A2 |
| ReturnBook 還書 | loanId | fine | R6 |
| RenewLoan 續借 | loanId | newDueDate | R7 |
| ListMemberLoans 查詢借閱 | memberId | 借閱清單 | （查詢，無規則） |

## 5. 外部世界（會變成 Adapter 的東西）

| 外部事物 | 範例技術 | 在架構中的角色 |
|---|---|---|
| HTTP API | REST / GraphQL | 輸入端（Driving / Primary） |
| CLI 管理工具 | 指令列 | 輸入端 |
| 資料庫 | PostgreSQL / MongoDB | 輸出端（Driven / Secondary） |
| Email 服務 | SMTP / SendGrid | 輸出端 |
| 系統時間 | OS clock | 輸出端（是的，時間也是外部依賴） |
