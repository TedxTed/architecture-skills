# 模板：已知技術債（debt.md）

> AI 指引：把下方 `---` 之間的內容存為使用者專案的 `docs/architecture/debt.md`（UTF-8）。

---

# 已知技術債

> 違反架構硬規則的既有程式碼。AI：**不仿照、不擅自修**（除非使用者要求）；修好後刪除條目。
> 只在審查、重構、或要修改相關檔案時讀。

<!-- 範例格式（新條目加在最下方，編號遞增不重用）：
## D-001 BorrowBook 直接 import Prisma
- 位置：`src/lending/application/borrow-book.ts`
- 違反：硬規則 2（application 不 import ORM）
- 修法：抽出 LoanRepository port（skill：workflows/refactor-legacy.md Step 3）
-->

---
