# 模板：慣例紀錄（conventions.md）

> AI 指引：把下方 `---` 之間的內容存為使用者專案的 `docs/architecture/conventions.md`（UTF-8）。
> 初始只放標頭；訪談中與預設不同的答案才寫成條目。

---

# 本專案慣例

> 與 Clean Architecture skill 預設**不同**之處。AI 每次任務都讀，**直接照做，不重新推理**。
> 每條 5 行以內；超過 80 行時合併或刪除過時條目。
> 標籤：[路徑] [命名] [編碼] [錯誤] [測試] [DI] [交易] [DTO] [其他]

<!-- 範例格式（新條目加在最下方，編號遞增不重用）：
## C-001 [路徑] 模組直接放在 src/ 下，沒有 modules/ 層
- 預設：`src/modules/<module>/domain/`
- 本專案：`src/<module>/domain/`（例：`src/lending/domain/loan.ts`）
- 依據：使用者訪談（2026-10-03）
-->

---
