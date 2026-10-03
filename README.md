# architecture-skills

給 AI 程式助理使用的**軟體架構實作指南**。不只告訴 AI「要遵守什麼」，還告訴它「下一步該寫什麼、放在哪、長什麼樣」。

目前收錄：**Clean Architecture**。規劃中：Hexagonal、Onion、DDD 戰術設計、Vertical Slice。

## 跟其他架構 skill 有什麼不同

| 常見問題 | 本專案做法 |
|---|---|
| 只有規則，沒有實作引導 | `workflows/`：新增功能、新增 adapter、重構舊程式的**逐步流程**，每一步有產出與檢查點 |
| 名詞解釋太抽象 | 全專案共用**同一個範例領域**（圖書借閱），每個概念都附「放什麼 / 不放什麼」與判斷流程 |
| 綁定特定語言或框架 | **語言中立的 pseudocode** 為主體，另有 TypeScript / Python / Go / Java 對照 |
| 沒有資料夾結構 | `structure/`：完整目錄樹、每個資料夾的 import 白名單、by-layer 與 by-feature 兩種選擇 |
| 寫完不知道對不對 | `review/`：分級審查清單 + 反模式前後對照 + 自動化依賴檢查設定 |

## 目錄

```
architecture-skills/
├── shared/                          # 跨架構共用
│   ├── example-domain.md            # 範例領域：圖書借閱（規則 R1–R7）
│   ├── pseudocode-syntax.md         # pseudocode 語法約定
│   ├── dependency-rule.md           # 依賴規則
│   └── ports-and-adapters.md        # Port / Adapter 定義
└── skills/
    └── clean-architecture/
        ├── SKILL.md                 # ★ 入口：讀取策略 + 硬規則 + 路徑對照 + 任務表
        ├── templates/project/       # 專案記憶模板：card / conventions / debt
        ├── concepts/                # 分層、放置判斷、跨邊界（DTO / 錯誤 / 交易）
        ├── workflows/               # 新功能、新 use case、新 adapter、重構
        ├── structure/               # 選擇結構、A. by-layer、B. by-feature
        ├── pseudocode/              # 端到端完整範例
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
| Claude Code | `CLAUDE.md` | `撰寫或審查程式碼架構時，先閱讀 docs/architecture-skills/skills/clean-architecture/SKILL.md 並遵循。` |
| Cursor | `.cursor/rules/architecture.mdc` | 同上 |
| GitHub Copilot | `.github/copilot-instructions.md` | 同上 |
| Codex / 其他支援 AGENTS.md 的工具 | `AGENTS.md` | 同上 |
| ChatGPT / 網頁版 | 對話開頭 | 貼上 `SKILL.md`，需要時再貼對應子文件 |

> `SKILL.md` 開頭的 frontmatter（`name` / `description`）是選用的中繼資料，不支援的工具會當作一般文字略過。

## 兩種資料夾結構

每個架構 skill 都支援兩種結構，範例用路徑佔位符（`<domain>/loan.x`）撰寫，一套範例同時適用：

- **A. by-layer**：專案頂層按層切（`src/domain/`、`src/application/`…）
- **B. by-feature**：頂層按業務模組切，模組內再分層（`src/modules/lending/domain/`…）

## 省 token 的設計

AI 不需要讀完整個專案（全部約 4 萬 token）。

| 機制 | 作用 |
|---|---|
| **專案記憶資料夾** | 首次使用時，AI 在你的專案建立 `docs/architecture/`（見下節）。之後的任務只讀 card + conventions + 一份 workflow |
| **任務表** | `SKILL.md` 中每種任務只列 1 份必讀、1 份選讀 |
| **語言隔離** | 只讀使用者語言的那份 `languages/*.md` |
| **佔位符** | 兩種結構共用一套範例，不必讀兩份 |

大約的讀取量：

| 情境 | 讀取 | 約略 token |
|---|---|---|
| 首次設定 | SKILL.md + choosing.md + 模板 | ~6k（只發生一次） |
| 之後新增功能 | card + conventions + new-feature.md | ~3.5k |
| 之後審查程式碼 | card + conventions + debt + checklist.md | ~2k |

## 專案記憶：讓 AI 記住你的專案跟預設哪裡不一樣

每個專案都有自己的習慣，例如模組直接放 `src/lending/`、沒有 `modules/` 這層，或 use case 方法叫 `handle()`。
AI 發現這類差異時會寫進你專案的 `docs/architecture/`，下次直接照做，不必重新推理：

```
your-project/docs/architecture/
├── card.md          # 架構卡：選了哪種結構、路徑對照表、讀取順序（每次讀）
├── conventions.md   # 與 skill 預設不同之處 → AI 照做（每次讀）
└── debt.md          # 違反硬規則的既有程式碼 → AI 不仿照、不擅自修（審查 / 重構時讀）
```

- 優先順序：**你當下的指示 > conventions.md > card.md > skill 預設**
- 硬規則（依賴方向等）不能被 conventions 推翻，違反的會記到 debt.md
- 只出現一次的寫法不會被記錄；兩種寫法互相矛盾時，AI 會先問你
- 這些檔案是一般 Markdown，你可以直接編輯或刪除條目

規則細節見 [record-convention.md](skills/clean-architecture/workflows/record-convention.md)。

首次設定完成後，CLAUDE.md / AGENTS.md 中的指引改為：
`架構相關任務先讀 docs/architecture/card.md 與 conventions.md。`

## 新增其他架構 skill 的規範

新增 `skills/<architecture>/` 時，沿用相同骨架，讓 AI 與讀者能以同樣方式使用：

```
skills/<architecture>/
├── SKILL.md         # 必要：讀取策略 + 硬規則（≤ 7 條）+ 路徑佔位符對照（A/B）+ 任務表
├── templates/project/ # 必要：card.md（60 行內）、conventions.md、debt.md
├── concepts/        # 必要：每個概念都有「放什麼 / 不放什麼 / 範例」
├── workflows/       # 必要：至少 new-feature.md、record-convention.md
├── structure/       # 必要：完整目錄樹 + import 白名單
├── pseudocode/      # 必要：用圖書借閱領域的 BorrowBook 端到端範例
├── languages/       # 選用：只寫與 Clean Architecture 不同之處
└── review/          # 必要：checklist.md、anti-patterns.md
```

原則：
- **範例一律使用 [圖書借閱領域](shared/example-domain.md)**，方便跨架構比較
- 與其他架構相同的概念，**連結到 `shared/`**，不要重寫
- 每份 workflow 的每個步驟都要有**產出**與**檢查點**
- 說明一律附 pseudocode，第一行標註 `// FILE: <佔位符>/...` 路徑，不寫死 `src/`
- 任務表中每種任務只列 1 份必讀；單一檔案盡量控制在 8KB 以內

## 授權

待定。
