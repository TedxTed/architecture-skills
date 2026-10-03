# Workflow：專案訪談（首次設定前）

目的：**用問的，取代用推理的。** 先問使用者專案細節，答案直接寫入 `docs/architecture/`，之後的任務不必再掃描程式碼、不必重新推理。

| 做法 | 約略成本 |
|---|---|
| 讀原始碼推論結構與慣例 | 數千到數萬 token，且可能推論錯 |
| 低成本偵測 + 一輪訪談 | ~1–2k token，答案由使用者確認 |

**何時執行**：專案沒有 `docs/architecture/card.md` 時，在首次設定的第一步執行。只做一次。

---

## Step 1 — 低成本偵測（不讀原始碼）

只讀以下內容，能確定的答案就**不問**：

| 讀什麼 | 得知 |
|---|---|
| `package.json` / `pyproject.toml` / `go.mod` / `pom.xml` / `build.gradle` | 語言、框架、ORM、測試框架 |
| 根目錄與 `src/` 往下兩層的**資料夾名稱**（不開檔案） | 是否已分層、A / B 結構、模組名稱 |
| 既有的 CLAUDE.md / AGENTS.md / README（只看標題與架構相關段落） | 使用者已寫下的慣例 |
| `.editorconfig`（`charset`）、`.gitattributes`（`working-tree-encoding`）、`.vscode/settings.json`（`files.encoding`） | 檔案編碼線索 |

> 編碼**一定要問**（偵測到就改成「請確認」）。沒有設定檔不代表是 UTF-8，很多 Big5 專案沒有任何設定。

**不要做**：打開原始碼檔案逐一閱讀、搜尋整個 repo。

---

## Step 2 — 一次問完

規則：
1. **一輪問完**，不要一題一題來回問
2. **每題附選項與預設值**，使用者可以只回「全部預設」或「1A 2B 其餘預設」
3. **已偵測到的寫成「確認」**，不要當成新問題問
4. **最多 12 題**；不相關的題目刪掉（例如 A 結構不問模組名稱）
5. 工具支援結構化提問（例如選單式問答）時就用它；否則用下方文字模板

### 提問模板（依偵測結果刪改後送出）

```
開始之前，我想先確認幾件事，答案會記在 docs/architecture/，之後不會再問。
可以直接回「全部預設」，或只回要改的題號（例如「3B、6：handle」）。

【已偵測，請確認】
- 語言 / 框架：TypeScript / NestJS
- ORM：Prisma

【專案】
1. 專案狀態？
   A. 新專案（預設）  B. 既有專案，已分層  C. 既有專案，尚未分層，需要重構
2. 有哪些入口？（可多選）
   A. HTTP API（預設）  B. CLI  C. Message Queue  D. 其他：___

【結構】
3. 資料夾結構？
   A. by-layer：src/domain、src/application…
   B. by-feature：模組內分層（預設）
4. （選 B 時）模組放在哪？
   A. src/modules/<模組>/（預設）  B. src/features/<模組>/  C. 直接 src/<模組>/
5. （選 B 時）有哪些業務模組？例如：lending 借閱、catalog 館藏

【儲存】
6. 新功能儲存方式？
   A. 先 CSV，確認後再接資料庫（預設）  B. 直接接資料庫
7. 正式資料庫？ A. PostgreSQL  B. MySQL  C. MongoDB  D. 還沒決定（預設）

【編碼】
8. 原始碼檔案編碼？
   A. UTF-8（預設）  B. Big5 / CP950  C. 混用（請說明哪些資料夾是哪種）  D. 其他：___
9. CSV 資料檔編碼？
   A. 與原始碼相同（預設）  B. UTF-8 with BOM（Excel 直接開不亂碼）  C. Big5

【寫法】
10. 錯誤處理？ A. 依語言慣例 throw / return error（預設）  B. Result 型別
11. 測試檔位置？ A. tests/ 鏡像 src/（預設）  B. 與原始碼同資料夾
12. 有沒有其他團隊慣例、命名規則、或「不要這樣做」的事？（自由回答，沒有就跳過）
```

### 選用題（只在相關時才加）

| 題目 | 何時問 |
|---|---|
| Use case 方法名稱？`execute`（預設）/ `handle` / `invoke` | 既有專案 |
| DI 方式？手動組裝（預設）/ 框架 DI 容器 | 使用 NestJS、Spring 等內建 DI 的框架 |
| 交易方式？UnitOfWork port（預設）/ decorator | 一個操作會改多個 entity |
| 是否保留輸入 port 介面？省略（預設）/ 保留 | 團隊規模大、需要替換 use case 實作 |
| 舊程式先重構哪個功能？ | 題 1 選 C |

---

## Step 3 — 寫入專案記憶

依 [templates/project/card.md](../templates/project/card.md) 建立 `docs/architecture/`。

### 3a. 路徑表：依題 3、4 的答案填入 card.md

| 佔位符 | A. by-layer | B. by-feature（`modules/`） | B 變體（直接 `src/<module>/`） |
|---|---|---|---|
| `<domain>` | `src/domain` | `src/modules/{module}/domain` | `src/{module}/domain` |
| `<application>` | `src/application` | `src/modules/{module}/application` | `src/{module}/application` |
| `<adapters>` | `src/adapters` | `src/modules/{module}/adapters` | `src/{module}/adapters` |
| `<infrastructure>` | `src/infrastructure` | `src/infrastructure` | `src/infrastructure` |
| `<main>` | `src/main.x` | `src/modules/{module}/module.x` + `src/main.x` | `src/{module}/module.x` + `src/main.x` |
| `<tests>` | `tests` | `tests/modules/{module}` | `tests/{module}` |
| 共用 port（Clock 等） | `src/application/ports` | `src/shared_kernel` | `src/shared_kernel` |

`{module}` 是**業務領域**（lending、catalog），不是單一操作（borrow-book），原因見 [choosing.md](../structure/choosing.md#功能要切多大)。
既有專案用其他名稱（`features/`、`core/`）時，照專案的填。

### 3b. 語言要點：讀一次 `languages/<語言>.md`，濃縮寫進 card.md

先搜尋 `^## ` 取得章節列表，只讀「Pseudocode → 語言」對照表與框架整合章節（Java 為「Spring Boot 整合」），把以下 5 項各寫成一行到 card 的「語言寫法」區塊：
Port 寫法、Use case 寫法、錯誤寫法、DTO 寫法、框架限制（例如「use case 不加 @Injectable」）。
**之後的任務不再讀語言文件。**

### 3c. 答案寫到哪

| 答案 | 寫入 |
|---|---|
| 語言、框架、DB、結構、模組、路徑、入口、儲存方式 | `card.md` 對應欄位 |
| 編碼（題 8、9） | `card.md` 的「編碼」欄位；非 UTF-8 時**另外**寫一條 `[編碼]` 到 conventions.md |
| 與 skill 預設**不同**的答案（例如 4C、10B、11B、題 12 的慣例） | `conventions.md`，依據寫「使用者訪談（日期）」 |
| 與預設相同的答案 | 只寫 card.md，**不寫** conventions.md |
| 1C 且使用者提到的已知問題 | `debt.md` |

條目格式見 [record-convention.md](record-convention.md#寫法規則)。例：回答 4C →

```markdown
## C-001 [路徑] 模組直接放在 src/ 下，沒有 modules/ 層
- 預設：`src/modules/<module>/`
- 本專案：`src/<module>/`
- 依據：使用者訪談（2026-10-03）
```

---

## Step 4 — 確認並繼續原本的任務

1. 用 3–5 行摘要寫入的內容，告訴使用者「可以直接編輯 docs/architecture/ 修改」
2. 確認 CLAUDE.md / AGENTS.md 的指引會**先讀 card.md**。若原本直接指向 SKILL.md，換成：
   `架構相關任務先讀 docs/architecture/card.md 與 conventions.md；卡上沒寫到才查 skill。`
   （不換的話，之後每次任務都會先載入 SKILL.md，白白多花 token）
3. **回到使用者原本要求的任務**，不要停在設定
