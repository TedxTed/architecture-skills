---
name: clean-architecture
description: 以 Clean Architecture（乾淨架構）設計、實作、重構或審查程式碼。提供分層規則、逐步實作流程、語言中立 pseudocode、兩種資料夾結構（by-layer / by-feature）與審查清單。當使用者要求「用乾淨架構」「分層」「解耦框架」「新增 use case」或審查架構時使用。
---

# Clean Architecture Skill

本文件是**入口**。讀完這頁後，依「讀取策略」只讀當下任務需要的檔案。

## ⚡ 讀取策略（省 token，必讀）

1. **先找專案記憶**：使用者專案中是否有 `docs/architecture/card.md`（或 CLAUDE.md / AGENTS.md 中標示的位置）？
   - **有** → 讀 `card.md` + `conventions.md`，**不必再讀本頁其餘部分**，依卡上的任務對照讀一份文件。
     conventions.md 中的條目**直接照做，不重新推理**。
   - **沒有** → 讀完本頁，完成「首次設定」。
2. **一次任務最多讀 2 份子文件**（任務表中的「必讀」）。「選讀」只在卡住時才讀。
3. **只讀使用者語言的那一份 `languages/*.md`**，其他語言不讀。
4. **不讀**：`README.md`、其他架構的 skill、`structure/` 中未選用的那份。
5. **同一個對話中已讀過的檔案不重讀。**
6. **委派子代理時**，只傳架構卡路徑 + 一份對應的 workflow，不要叫子代理讀整個 skill。

## 五條硬規則（任何時候都不可違反）

1. **依賴只能由外往內**：`infrastructure → adapters → application → domain`。內層不得 import 外層。
2. **domain 與 application 不得 import 任何框架、ORM、HTTP、SDK。**
3. **業務規則放 Entity；流程編排放 Use Case；格式轉換放 Adapter。**
4. **內層需要外部能力時，內層定義 Port（介面），外層實作 Adapter。** 時間、ID、亂數也算外部能力。
5. **物件組裝只發生在 Composition Root。** 其他地方不得建立 adapter 實例。

## 四層速覽

| 層 | 佔位符 | 放什麼 | 一句話判斷 |
|---|---|---|---|
| Entities | `<domain>` | Entity、Value、領域錯誤 | 沒有電腦，櫃台人員也照做的規則 |
| Use Cases | `<application>` | Use case、Port 介面、DTO | 「這套系統」執行一個操作的步驟 |
| Interface Adapters | `<adapters>` | Controller、Repository 實作、Mapper | 外部格式 ⇄ 內部格式 |
| Frameworks & Drivers | `<infrastructure>`、`<main>` | DB 連線、Server、設定、組裝 | 換掉它，業務不應該知道 |

## 兩種資料夾結構（擇一）

| | A. by-layer（專案整體分層） | B. by-feature（功能模組內分層） |
|---|---|---|
| 頂層看到 | `domain/ application/ adapters/` | `modules/lending/ modules/catalog/` |
| 適合 | 單一業務領域、小型專案 | 多個業務領域、多人協作、未來可能拆服務 |
| 詳細 | [structure/by-layer.md](structure/by-layer.md) | [structure/by-feature.md](structure/by-feature.md) |

不確定時讀 [structure/choosing.md](structure/choosing.md)。**使用者有偏好或既有結構時，照使用者的。**

### 路徑佔位符

所有範例的 `// FILE:` 都用佔位符撰寫，依選用的結構替換：

| 佔位符 | A. by-layer | B. by-feature |
|---|---|---|
| `<domain>` | `src/domain` | `src/modules/<module>/domain` |
| `<application>` | `src/application` | `src/modules/<module>/application` |
| `<adapters>` | `src/adapters` | `src/modules/<module>/adapters` |
| `<infrastructure>` | `src/infrastructure` | `src/infrastructure`（跨模組共用） |
| `<main>` | `src/main.x` | `src/modules/<module>/module.x`（模組組裝）+ `src/main.x` |
| `<tests>` | `tests` | `tests/modules/<module>` |
| 通用 port（Clock、UnitOfWork） | `src/application/ports` | `src/shared_kernel` |

`<module>` 是**業務領域**（lending、catalog），不是單一操作（borrow-book）。原因見 [choosing.md](structure/choosing.md#功能要切多大)。
`modules/` 只是預設名稱；專案若用 `features/` 或直接放 `src/<module>/`，照專案的，並記錄為慣例。

## 首次設定（專案沒有 `docs/architecture/` 時）

1. 偵測：使用者的語言、框架、既有資料夾結構
2. 決定結構 A / B（有疑問就問使用者，一次問完）
3. 依 [templates/project/](templates/project/card.md) 建立 `docs/architecture/`：
   - `card.md`：架構卡（結構、路徑表、讀取順序）
   - `conventions.md`：與 skill 預設不同之處（偵測時發現的差異直接寫入）
   - `debt.md`：違反硬規則的既有程式碼
4. 在 CLAUDE.md / AGENTS.md 加一行：`架構相關任務先讀 docs/architecture/card.md 與 conventions.md。`
5. 之後的任務都從 card.md 開始，不再重讀本頁

## 發現專案與預設不同時

任何任務中，只要發現專案做法與本 skill 不同（路徑、命名、錯誤處理、測試位置…），或使用者糾正你，
依 [workflows/record-convention.md](workflows/record-convention.md) 記錄：不違反硬規則的寫入 `conventions.md`，違反的寫入 `debt.md`。

## 任務表

| 任務 | 必讀 | 選讀（卡住時） |
|---|---|---|
| 新增功能 / API | [workflows/new-feature.md](workflows/new-feature.md) | `languages/<你的語言>.md` |
| 只加一個 use case | [workflows/new-use-case.md](workflows/new-use-case.md) | — |
| 換 DB / 加第三方 / 加 CLI、MQ 入口 | [workflows/add-adapter.md](workflows/add-adapter.md) | — |
| 重構舊程式 | [workflows/refactor-legacy.md](workflows/refactor-legacy.md) | [concepts/placement-guide.md](concepts/placement-guide.md) |
| 不確定程式碼放哪 | [concepts/placement-guide.md](concepts/placement-guide.md) | [concepts/layers.md](concepts/layers.md) |
| DTO / 錯誤 / 交易怎麼跨層 | [concepts/crossing-boundaries.md](concepts/crossing-boundaries.md) | — |
| 審查程式碼 | [review/checklist.md](review/checklist.md) + 專案的 `debt.md` | [review/anti-patterns.md](review/anti-patterns.md) |
| 記錄專案慣例 / 偏差 | [workflows/record-convention.md](workflows/record-convention.md) | — |
| 第一次產生實際程式碼 | `languages/<你的語言>.md` | [pseudocode/borrow-book-end-to-end.md](pseudocode/borrow-book-end-to-end.md) |

## 範例領域（摘要）

所有範例都是圖書借閱：`Member` 借 `Book` 產生 `Loan`，規則編號 R1–R7（停權不能借、借閱上限、逾期不能借、借期 14 天、逾期罰金、續借 1 次）。
看不懂某個規則編號時才讀 [shared/example-domain.md](../../shared/example-domain.md)。把使用者的名詞對應過來，結構照抄。

## 工作方式

- **由內往外**：domain → application → 測試 → adapters → 組裝
- **沿用使用者的命名**，不強迫改名
- **務實**：可省略只有單一實作的輸入 port、presenter；**不可**違反依賴方向
- **告知取捨**：做了簡化就跟使用者說
- Pseudocode 語法見 [shared/pseudocode-syntax.md](../../shared/pseudocode-syntax.md)，看得懂範例就不必讀
