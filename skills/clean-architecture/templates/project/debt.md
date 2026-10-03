# 模板：已知技術債（debt.md）

> AI 指引：複製下方 `---` 之間的內容到使用者專案的 `docs/architecture/debt.md`。
> 這裡記錄**違反硬規則**的既有程式碼。與 conventions.md 的差別：
> conventions 是「照做」，debt 是「**不要仿照**，也不要擅自修」。

---

# 已知技術債

> 違反架構硬規則的既有程式碼。AI 規則：
> 1. 新程式碼**不得仿照**這些寫法
> 2. 未經使用者要求**不主動修改**（避免任務範圍失控）
> 3. 修好後刪除該條目
> 只在審查、重構、或要修改到相關檔案時才讀本檔。

<!-- 新條目加在最下方，編號遞增，不重用 -->

---

## 條目範例（不要複製到使用者專案）

```markdown
## D-001 BorrowBook 直接 import Prisma
- 位置：`src/lending/application/borrow-book.ts`
- 違反：硬規則 2（application 不 import ORM）
- 建議修法：抽出 LoanRepository port，見 workflows/refactor-legacy.md Step 3
- 發現：2026-10-03

## D-002 Loan entity 上有 @Entity 註解
- 位置：`src/lending/domain/loan.ts`
- 違反：硬規則 2（框架滲透）
- 建議修法：分離 ORM model 與 entity，見 concepts/crossing-boundaries.md
- 發現：2026-10-03
```
