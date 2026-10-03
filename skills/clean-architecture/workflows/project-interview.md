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
| `pom.xml` / `build.gradle` / `package.json` / `pyproject.toml` / `go.mod` | 語言、框架、ORM、測試框架 |
| Java：`pom.xml` 的 `maven.compiler.source` / `java.version`、`project.build.sourceEncoding`，以及 Web 框架與 DI 方式（Servlet、Spring、JAX-RS…） | **JDK 版本**（決定能不能用 lambda、`Optional`、`java.time`）、原始碼編碼 |
| Java：`src/main/java` 往下到 base package 再兩層的 package 名稱 | base package、是否已分層、模組名稱 |
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
5. 工具支援結構化提問（例如選單式問答）時就用它；但若選單一次能問的題數有限（例如最多 4 題）而題目更多，**改用下方文字模板**，維持一輪問完

### 提問模板（依偵測結果刪改後送出）

```
開始之前，我想先確認幾件事，答案會記在 docs/architecture/，之後不會再問。
可以直接回「全部預設」，或只回要改的題號（例如「3B、6：handle」）。

【已偵測，請確認】
- 語言 / 框架：Java 1.7 / Servlet 3.0（未偵測到 DI 框架）
- 資料存取：JdbcTemplate；建置：Maven；base package：com.example.library
- 原始碼編碼：pom.xml 設定為 MS950

【專案】
1. 專案狀態？
   A. 新專案（預設）  B. 既有專案，已分層  C. 既有專案，尚未分層，需要重構
2. 專案形態？（有多個入口請一併說明，例如「A，另有 CLI」）
   A. 純後端 / API 服務（預設）
   B. 全端：前端 + 後端 API（架構套用在後端；前端只呼叫 API）
   C. 純前端：業務邏輯在瀏覽器內執行
   D. CLI / 批次工具 / Message Queue 消費者

【結構】
3. 資料夾結構？
   A. by-layer：src/domain、src/application…
   B. by-feature：模組內分層（預設）
4. （選 B 時）模組放在哪？
   A. 直接在 base package 下：com.example.library.<模組>（Java 預設）
   B. 多一層：com.example.library.modules.<模組>
   C. 其他：___（非 Java 專案：src/modules/<模組>/、src/<模組>/ 等）
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
11. 測試？
   A. 寫測試，tests/ 鏡像 src/（預設）  B. 寫測試，與原始碼同資料夾
   C. 不寫自動化測試（改用型別檢查 + 依賴方向檢查 + 手動驗證）
12. 有沒有其他團隊慣例、命名規則、或「不要這樣做」的事？（自由回答，沒有就跳過）
```

### 選用題（只在相關時才加）

| 題目 | 何時問 |
|---|---|
| Use case 方法名稱？`execute`（預設）/ `handle` / `invoke` | 既有專案 |
| 找不到資料的表示？回傳 `null`（預設，Java 7 相容）/ `Optional` | Java 8 以上 |
| 回呼寫法？匿名類別（預設，Java 7 相容）/ lambda | Java 8 以上 |
| 是否使用 Lombok？不使用（預設）/ 使用（DTO 用 `@Value`，但 domain 不加） | Java 專案 |
| Web 框架與組裝方式？無框架 / Servlet + 普通組裝類別（預設）/ Spring Java Config / Spring XML / JAX-RS / 其他 | 偵測不到，或偵測到多種時 |
| 交易方式？UnitOfWork port（預設）/ decorator | 一個操作會改多個 entity |
| 是否保留輸入 port 介面？省略（預設）/ 保留 | 團隊規模大、需要替換 use case 實作 |
| 舊程式先重構哪個功能？ | 題 1 選 C |

---

## Step 3 — 寫入專案記憶

依 [templates/project/](../templates/project/card.md) 建立 `docs/architecture/` 的五個檔案：
card、conventions、map、decisions、debt。

- **常用指令**：Step 1 讀到的建置設定（Maven：`mvn compile`、`mvn test`、啟動指令；npm scripts；Makefile 等），直接填進 card 的「常用指令」
- **map.md**：只依資料夾名稱列出模組標題，內容留空；**不要為了填 map 掃描原始碼**，之後每次任務收尾時逐步補上
- **decisions.md**：只放標頭

### 3a. 路徑表：依題 3、4 的答案填入 card.md

Java（Maven / Gradle）以 package 表示，`{base}` = `src/main/java/<base package 路徑>`（例：`src/main/java/com/example/library`）：

| 佔位符 | A. by-layer | B. by-feature |
|---|---|---|
| `<domain>` | `{base}/domain` | `{base}/{module}/domain` |
| `<application>` | `{base}/application` | `{base}/{module}/application` |
| `<adapters>` | `{base}/adapter` | `{base}/{module}/adapter` |
| `<infrastructure>` | `{base}/infrastructure` | `{base}/infrastructure` |
| `<main>` | `{base}/config` | `{base}/{module}/config` |
| `<tests>` | `src/test/java/<base>`（鏡像 main） | `src/test/java/<base>/{module}` |
| 共用 port（Clock 等） | `{base}/application/port` | `{base}/sharedkernel` |
| 共用 adapter（SystemClock 等） | `{base}/adapter` | `{base}/infrastructure/adapter` |
| 模組對外 API（僅 B） | — | `{base}/{module}/api` |

其他語言（資料夾式）：A 為 `src/domain`、`src/application`、`src/adapters`…；B 為 `src/modules/{module}/domain`…（或直接 `src/{module}/domain`）。

`{module}` 是**業務領域**（lending、catalog），不是單一操作（borrowbook），原因見 [choosing.md](../structure/choosing.md#功能要切多大)。
既有專案用其他名稱時，照專案的填。
共用 adapter：只有一個模組時可以先放在該模組的 `adapter`，出現第二個模組再搬，並記入 `decisions.md`。

**依題 2 的形態調整**：

| 形態 | 路徑調整 |
|---|---|
| A. 純後端、D. CLI | 照上表 |
| B. 全端 | 上表路徑加上後端根目錄（例如 `server/src/...`）；前端在另一個資料夾，記一條 `[路徑]` 慣例說明前端不套用分層 |
| C. 純前端 | 照上表，但 adapter 的種類不同（UI 元件、API client、瀏覽器儲存），見 [concepts/frontend.md](../concepts/frontend.md) |

### 3b. 語言要點：讀一次 `languages/<語言>.md`，濃縮寫進 card.md

本 skill 的範例本身就是 Java（JDK 1.7 相容）。

- **Java 專案**：只讀 `languages/java.md` 的「依 JDK 版本調整」「強制依賴規則」；**有用 Spring 才讀**「使用 Spring 時」
- **其他語言**：先搜尋 `^## ` 取得章節列表，只讀「Java → 該語言」對照表與框架整合章節

把以下 6 項各寫成一行到 card 的「語言寫法」區塊：
Port 寫法、Use case 寫法、錯誤寫法、DTO 寫法、找不到資料的表示（null / Optional）、框架限制（例如「use case 不加 @Service」）。
**之後的任務不再讀語言文件。**

### 3c. 答案寫到哪

| 答案 | 寫入 |
|---|---|
| 語言、框架、DB、形態、結構、模組、路徑、入口、儲存方式 | `card.md` 對應欄位 |
| 11C 不寫測試 | `conventions.md` 寫 `[測試]` 條目；card 的新功能步驟把測試步驟換成「驗證：編譯 + 依賴方向檢查 + 手動打主要成功路徑與每種錯誤」 |
| JDK 版本與選用題（null / Optional、匿名類別 / lambda、Lombok） | `card.md` 的「語言寫法」；與 skill 預設（Java 7 寫法）不同時另寫 `conventions.md` |
| 編碼（題 8、9） | `card.md` 的「編碼」欄位；非 UTF-8 時**另外**寫一條 `[編碼]` 到 conventions.md |
| 與 skill 預設**不同**的答案（例如 2B、4C、10B、11B、題 12 的慣例） | `conventions.md`，依據寫「使用者訪談（日期）」 |
| 與預設相同的答案 | 只寫 card.md，**不寫** conventions.md |
| 1C 且使用者提到的已知問題 | `debt.md` |

條目格式見 [record-convention.md](record-convention.md#寫法規則)。例：回答 4B →

```markdown
## C-001 [路徑] 模組放在 modules package 下
- 預設：`com.example.library.<module>`
- 本專案：`com.example.library.modules.<module>`
- 依據：使用者訪談（2026-10-03）
```

---

## Step 4 — 確認並繼續原本的任務

1. 用 3–5 行摘要寫入的內容，告訴使用者「可以直接編輯 docs/architecture/ 修改」
2. 確認 CLAUDE.md / AGENTS.md 的指引會**先讀 card.md**。若原本直接指向 SKILL.md，換成：
   `架構相關任務先讀 docs/architecture/card.md 與 conventions.md；卡上沒寫到才查 skill。`
   （不換的話，之後每次任務都會先載入 SKILL.md，白白多花 token）
3. **回到使用者原本要求的任務**，不要停在設定
