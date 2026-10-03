# Workflow：記錄專案慣例與偏差

目的：AI 發現使用者專案的做法與本 skill 預設不同時，**記下來，下次直接照做**，不必每次重新推理。
記錄位置：使用者專案的 `docs/architecture/`（建立方式見 [templates/project/card.md](../templates/project/card.md)）。

---

## 何時觸發

| 觸發情境 | 例子 |
|---|---|
| 讀既有程式碼時發現與預設不同 | 模組直接在 `src/lending/`，沒有 `modules/` 層 |
| 使用者糾正 AI | 「我們的 use case 方法叫 `handle`，不叫 `execute`」 |
| 使用者明確說明偏好 | 「測試檔放在原始碼旁邊」 |
| 審查時發現違反硬規則的既有程式碼 | application 中 import 了 ORM |

**不觸發**：與 skill 預設相同的事、只出現一次的寫法、本次任務的臨時決定。

---

## 決策流程

```
發現差異
  │
  ├─ Q1. 違反五條硬規則嗎？
  │     是 → 寫入 debt.md（不要仿照、不擅自修）→ 結束
  │
  ├─ Q2. 是使用者明確說的嗎？
  │     是 → 寫入 conventions.md → 結束
  │
  ├─ Q3. 在既有程式碼中「一致地」出現至少 2 處嗎？
  │     否 → 不記錄（可能是意外，不是慣例）
  │
  ├─ Q4. 專案中同時存在兩種互相矛盾的寫法嗎？
  │     是 → 問使用者要遵循哪一種，依答案記錄
  │
  └─ 寫入 conventions.md
       └─ 若是 [路徑] 類 → 同時更新 card.md 的路徑表
```

---

## 寫法規則

1. **只記差異**：寫出「預設」與「本專案」兩行，讓下次讀的 AI 一眼看懂差在哪
2. **給具體例子**：至少一個實際檔案路徑或程式碼片段
3. **寫依據與日期**：「既有 3 個模組皆如此（2026-10-03 偵測）」或「使用者指示（日期）」
4. **加分類標籤**：`[路徑]` `[命名]` `[錯誤]` `[測試]` `[DI]` `[交易]` `[DTO]` `[其他]`
5. **每條 5 行以內**；編號遞增，不重用
6. **記錄後用一行告知使用者**：「已記錄 C-003：本專案使用 Result 型別，不 throw。」

---

## 範例：沒有 `modules/` 層的專案

**情境**：使用者要新增「續借」功能。AI 讀取專案時看到：

```
src/
├── lending/
│   ├── domain/loan.ts
│   ├── application/...
│   └── adapters/...
├── catalog/
│   ├── domain/book.ts
│   └── ...
└── main.ts
```

**推理（只做一次）**：
- 是 B. by-feature（模組內分層），但沒有 `modules/` 這一層
- Q1 不違反硬規則；Q3 lending、catalog 兩處一致 → 是慣例

**寫入 `docs/architecture/conventions.md`**：

```markdown
## C-001 [路徑] 模組直接放在 src/ 下，沒有 modules/ 層
- 預設：`src/modules/<module>/domain/`
- 本專案：`src/<module>/domain/`（例：`src/lending/domain/loan.ts`）
- 依據：既有 lending、catalog 皆如此（2026-10-03 偵測）
```

**同時更新 `card.md` 路徑表**：

```markdown
| `<domain>` | `src/{module}/domain` |
| `<application>` | `src/{module}/application` |
| `<adapters>` | `src/{module}/adapters` |
```

**告知使用者**：「已記錄 C-001：本專案模組直接放在 `src/` 下，沒有 `modules/` 層。」

**下次任務**：AI 讀 card.md + conventions.md，直接把 `<domain>` 對應到 `src/lending/domain/`，不再分析目錄結構。

---

## 維護

| 情況 | 處理 |
|---|---|
| conventions.md 超過 80 行 | 合併同類條目；刪除已過時的 |
| 使用者改變心意 | 修改原條目並更新日期，不新增矛盾條目 |
| debt 已修好 | 刪除該條目 |
| 條目與實際程式碼不符（專案已改） | 以實際程式碼為準，更新條目並告知使用者 |
