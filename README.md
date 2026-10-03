# architecture-skills

給 AI 程式助理使用的**軟體架構實作指南**。不只告訴 AI「要遵守什麼」，還告訴它「下一步該寫什麼、放在哪、長什麼樣」。

目前收錄：**Clean Architecture**。規劃中：Hexagonal、Onion、DDD 戰術設計、Vertical Slice。

## 跟其他架構 skill 有什麼不同

| 常見問題 | 本專案做法 |
|---|---|
| 只有規則，沒有實作引導 | `workflows/`：新增功能、新增 adapter、重構舊程式的**逐步流程**，每一步有產出與檢查點 |
| 名詞解釋太抽象 | 全專案共用**同一個範例領域**（圖書借閱），每個概念都附「放什麼 / 不放什麼」與判斷流程 |
| 範例只有片段、或依賴新版語法 | **完整的 Java 範例**，刻意只用 **JDK 1.7 可編譯**的語法（舊專案可直接照抄）；另有 TypeScript / Python / Go 對照 |
| 沒有資料夾結構 | `structure/`：完整目錄樹、每個資料夾的 import 白名單、by-layer 與 by-feature 兩種選擇 |
| 一開始就被資料庫綁住 | **CSV 優先**：每個新功能先用 CSV 儲存跑通，使用者確認行為後才接資料庫，同一套 contract test 保證兩者一致 |
| 寫完不知道對不對 | `review/`：分級審查清單 + 反模式前後對照 + 自動化依賴檢查設定 |

## 目錄

```
architecture-skills/
├── dist/
│   └── clean-architecture/SKILL.md  # 單檔版（約 26k 字元），給只能放一個檔案的工具
├── shared/                          # 跨架構共用
│   ├── example-domain.md            # 範例領域：圖書借閱（規則 R1–R7）
│   ├── code-conventions.md          # 範例程式碼約定（Java，相容 JDK 1.7 / 1.8）
│   ├── dependency-rule.md           # 依賴規則
│   └── ports-and-adapters.md        # Port / Adapter 定義
└── skills/
    └── clean-architecture/
        ├── SKILL.md                 # ★ 入口：讀取策略 + 硬規則 + 路徑對照 + 任務表
        ├── templates/               # 專案記憶模板（card / conventions / map / decisions / debt）、依賴檢查程式
        ├── layers/                  # 每層一個檔案：遵循規則、範例程式碼、與其他層的配合
        ├── concepts/                # DTO、放置判斷、跨邊界、事件、前端、CSV 優先、檔案編碼
        ├── workflows/               # 新功能、新 use case、新 adapter、重構
        ├── structure/               # 選擇結構、A. by-layer、B. by-feature
        ├── examples/                # 端到端完整範例（含測試替身）
        ├── languages/               # TS / Python / Go / Java 對照
        └── review/                  # 審查清單、反模式
```

## 如何接到你的 AI 工具

所有內容都是一般 Markdown，任何能讀檔的 AI 工具都可以使用。
**請保留整個資料夾結構**（`skills/` 會以相對路徑引用 `shared/`）。

先把本專案放進你的專案中，例如：

```bash
git submodule add <本專案 repo URL> docs/architecture-skills
# 或直接複製到 docs/architecture-skills/
```

再依你使用的工具，加入一行指引：

| 工具 | 放在哪 | 內容 |
|---|---|---|
| Claude Code | `CLAUDE.md` | `架構相關任務：若 docs/architecture/card.md 存在就讀它，否則讀 docs/architecture-skills/skills/clean-architecture/SKILL.md。` |
| Cursor | `.cursor/rules/architecture.mdc` | 同上 |
| GitHub Copilot | `.github/copilot-instructions.md` | 同上 |
| Codex / 其他支援 AGENTS.md 的工具 | `AGENTS.md` | 同上 |
| ChatGPT / 網頁版 | 對話開頭 | 貼上單檔版 `dist/clean-architecture/SKILL.md` |

> `SKILL.md` 開頭的 frontmatter（`name` / `description`）是選用的中繼資料，不支援的工具會當作一般文字略過。

### 單檔版（只能放一個 skill 檔案時）

有些工具或平台的 skill 只能是一個檔案。這時使用 [dist/clean-architecture/SKILL.md](dist/clean-architecture/SKILL.md)：

| | 多檔版（`skills/`） | 單檔版（`dist/`） |
|---|---|---|
| 內容 | 完整：每層的完整範例、各語言對照、檢查程式樣板 | 濃縮：規則、流程、模板、範例核心片段，Java 範例 |
| 大小 | 全部約 95k 字元，但每次只讀需要的 1–2 份 | 約 26k 字元，載入時整份讀取 |
| 適合 | Claude Code、Cursor 等可讀取資料夾的工具 | 只能放單一檔案的平台、貼進網頁對話 |

兩者的規則一致；**多檔版是正本**，修改時先改多檔版，再同步濃縮到單檔版。
單檔版同樣支援專案記憶：首次設定後，日常任務只讀專案的 `docs/architecture/card.md`，不會每次重讀這份單檔。

## 兩種資料夾結構

每個架構 skill 都支援兩種結構，範例用路徑佔位符（`<domain>/loan.x`）撰寫，一套範例同時適用：

- **A. by-layer**：專案頂層按層切（`src/domain/`、`src/application/`…）
- **B. by-feature**：頂層按業務模組切，模組內再分層（`src/modules/lending/domain/`…）

## 省 token 的設計

核心概念：**架構卡是「編譯過的專案速查表」**。首次設定時把路徑、開發步驟、語言寫法濃縮進 card，
之後的日常任務只讀 card + conventions，skill 文件只在卡上沒寫到時才查。

| 機制 | 作用 |
|---|---|
| **首次訪談** | AI 只讀套件設定檔與資料夾名稱，其餘**一輪問完**（≤ 12 題，附預設值），不掃描原始碼推理 |
| **架構卡自給自足** | card 內含路徑表、新功能 9 步驟、語言寫法、記錄規則。一般功能開發不需要再讀 skill |
| **指引切換** | 設定完成後，CLAUDE.md / AGENTS.md 的指引從 SKILL.md **換成** card.md，之後不再載入 SKILL.md |
| **語言文件只讀一次** | 首次設定時只讀對照表與框架章節，濃縮成 card 中的 6 行 |
| **章節式讀取** | 大檔先看標題列表，只讀需要的章節 |
| **子代理** | 只拿 card + conventions，不讀 skill |

讀取量（以字元數估算，實際 token 依模型而異）：

| 情境 | 讀取 | 約略字元 |
|---|---|---|
| 日常新增功能 | card + conventions | ~3k |
| 審查程式碼 | card + conventions + debt + checklist | ~5k |
| 此專案第一次做新功能（想看細節） | 上述 + new-feature.md | ~8k |
| 首次設定（只發生一次） | SKILL + 訪談 + 模板 + 語言文件兩章節 | ~13k |
| 參考：整個 skill 全讀 | — | ~95k |

## 專案記憶：想過的事不再想第二次

AI 每次任務結束前會做**收尾記錄**，把這次推理、搜尋、試錯得到的結論寫進你專案的 `docs/architecture/`。
下次任務直接拿來用，不必重新掃描程式碼或重新推理：

```
your-project/docs/architecture/
├── card.md          # 架構卡：路徑、步驟、語言寫法、常用指令（每次讀）
├── conventions.md   # 與 skill 預設不同的慣例，例如沒有 modules/ 層 → 照做（每次讀）
├── map.md           # 專案地圖：模組、entity、port 方法、use case 在哪 → 取代掃描原始碼（改程式前讀）
├── decisions.md     # 判斷紀錄：放哪一層、選 A 不選 B、調查結論、試過失敗的做法（只搜尋標題，命中才讀）
└── debt.md          # 違反硬規則的既有程式碼 → 不仿照、不擅自修（審查 / 重構時讀）
```

- **只記結論，不記推理過程**；一次搜尋就找得到的事不記
- 優先順序：**你當下的指示 > conventions.md > card.md > skill 預設**；硬規則不能被推翻
- 記錄與程式碼不符時以程式碼為準，AI 會修正記錄
- 每次記了什麼，AI 會在最後用一行告訴你；這些檔案是一般 Markdown，可以直接編輯或刪除

規則細節見 [wrap-up.md](skills/clean-architecture/workflows/wrap-up.md) 與 [record-convention.md](skills/clean-architecture/workflows/record-convention.md)。

首次設定完成後，CLAUDE.md / AGENTS.md 中的指引改為：
`架構相關任務先讀 docs/architecture/card.md 與 conventions.md。`

## 新增其他架構 skill 的規範

新增 `skills/<architecture>/` 時，沿用相同骨架，讓 AI 與讀者能以同樣方式使用：

```
skills/<architecture>/
├── SKILL.md         # 必要：純導覽，約 3k 字元：讀取策略 + 硬規則（≤ 7 條）+ 任務表
├── templates/project/ # 必要：card.md（日常任務只讀它就能做事）、conventions、map、decisions、debt
├── layers/          # 必要：該架構的每一層 / 每個元件一個檔案，固定章節：
│                    #   職責、遵循規則（必須/禁止）、放什麼、怎麼寫、範例程式碼、
│                    #   與其他層的配合、測試、自我檢查
├── concepts/        # 必要：跨層的主題，每個概念都有「放什麼 / 不放什麼 / 範例」
├── workflows/       # 必要：至少 project-interview、new-feature、record-convention、wrap-up
├── structure/       # 必要：完整目錄樹 + import 白名單
├── examples/        # 必要：用圖書借閱領域的 BorrowBook 端到端範例
├── languages/       # 選用：只寫與 Clean Architecture 不同之處
└── review/          # 必要：checklist.md、anti-patterns.md
```

原則：
- **範例一律使用 [圖書借閱領域](shared/example-domain.md)**，方便跨架構比較
- 與其他架構相同的概念，**連結到 `shared/`**，不要重寫
- 每份 workflow 的每個步驟都要有**產出**與**檢查點**
- 說明一律附 Java 範例（遵守 shared/code-conventions.md，JDK 1.7 可編譯），第一行標註 `// FILE: <佔位符>/...` 路徑
- 任務表中每種任務只列 1 份必讀；單一檔案盡量控制在 8KB 以內

## 授權

待定。
