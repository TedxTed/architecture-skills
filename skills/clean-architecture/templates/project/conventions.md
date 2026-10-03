# 模板：慣例紀錄（conventions.md）

> AI 指引：複製下方 `---` 之間的內容到使用者專案的 `docs/architecture/conventions.md`。
> 初始時只保留標頭，**不要預先填範例條目**；範例僅供參考格式。
> 記錄方式見 [workflows/record-convention.md](../../workflows/record-convention.md)。

---

# 本專案慣例

> 本專案與 Clean Architecture skill 預設**不同**之處。AI 每次任務都讀，**直接照做，不重新推理**。
> 與 skill 預設相同的事不記。每條 5 行以內。總長超過 80 行時合併或刪除過時條目。

<!-- 新條目加在最下方，編號遞增，不重用 -->

---

## 條目範例（不要複製到使用者專案）

```markdown
## C-001 [路徑] 模組直接放在 src/ 下，沒有 modules/ 層
- 預設：`src/modules/<module>/domain/`
- 本專案：`src/<module>/domain/`（例：`src/lending/domain/loan.ts`）
- 依據：既有 lending、catalog、membership 皆如此（2026-10-03 偵測）

## C-002 [命名] use case 方法名稱為 handle()
- 預設：`execute()`
- 本專案：`handle(input)`
- 依據：使用者指示（2026-10-05）

## C-003 [錯誤] 使用 Result 型別，不 throw
- 預設：依語言慣例，pseudocode 的 FAIL 可 throw
- 本專案：回傳 `Result<T, AppError>`（定義於 `src/shared/result.ts`）
- 依據：既有 12 個 use case 皆如此

## C-004 [測試] 測試檔與原始碼放在同一資料夾
- 預設：`tests/` 鏡像 `src/`
- 本專案：`loan.ts` 旁邊放 `loan.spec.ts`
- 依據：既有慣例 + jest 設定 testMatch
```

### 分類標籤

`[路徑]` `[命名]` `[錯誤]` `[測試]` `[DI]` `[交易]` `[DTO]` `[其他]`

標籤讓 AI 只需掃描相關類別。例如新增 use case 時，主要看 `[路徑]` `[命名]` `[錯誤]`。
