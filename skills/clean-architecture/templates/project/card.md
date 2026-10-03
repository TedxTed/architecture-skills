# 模板：架構卡（card.md）

> AI 指引：首次設定時在使用者專案建立 `docs/architecture/`，放入 `card.md`（本模板 `---` 之間）、
> [conventions.md](conventions.md)、[debt.md](debt.md)。三個檔案**一律存成 UTF-8**（即使專案是 Big5）。
> 填入實際值後刪除所有 `<>` 提示。**目標 70 行以內**：這張卡是日常任務唯一必讀的檔案，
> 要讓 AI 不查 skill 也能完成一般功能開發。

---

# 架構卡

Clean Architecture｜Skill：`<docs/architecture-skills/skills/clean-architecture/>`
語言 / 框架：`<TypeScript / NestJS>`｜DB：`<PostgreSQL + Prisma>`
編碼：原始碼 `<UTF-8 / CP950>`｜CSV `<UTF-8 / UTF-8 BOM / CP950>`｜換行 `<LF / CRLF>`
`<非 UTF-8 才保留此行：讀寫檔案前先讀 skill 的 concepts/file-encoding.md；不使用 emoji>`

**讀取**：本檔 + conventions.md（照做，不重新推理）。debt.md 只在審查 / 重構時讀。
**優先**：使用者當下指示 > conventions.md > 本檔 > skill。硬規則不可被推翻。

## 硬規則
1. 依賴由外往內：infrastructure → adapters → application → domain
2. domain、application 不 import 框架 / ORM / HTTP / SDK
3. 規則放 Entity，流程放 Use Case，轉換放 Adapter
4. 外部能力（含時間、ID）用 Port；實作在 Adapter
5. 只在 composition root 組裝

## 路徑（結構：`<A. by-layer / B. by-feature>`）
| 佔位符 | 本專案 |
|---|---|
| `<domain>` | `<src/{module}/domain>` |
| `<application>` | `<...>` |
| `<adapters>` | `<...>` |
| `<main>` | `<...>` |
| `<tests>` | `<...>` |
| 共用 port | `<...>` |

模組：`<lending 借閱、catalog 館藏>`

## 語言寫法（首次設定時從 languages/<語言>.md 濃縮）
- Port：`<interface，放 <application>/ports/>`
- Use case：`<class + execute(input)，建構子注入>`
- 錯誤：`<throw DomainError(code)>`
- DTO：`<type 物件>`
- 框架限制：`<domain / application 不加 @Injectable；在 module 用 factory 組裝>`
- 依賴檢查：`<npm run lint:arch>`

## 新功能步驟（每步通過檢查才進下一步）
0. 拆規則表（Entity 規則 / Use case 流程 / I/O / 需要的 Port）；不清楚的**一次問完**，附預設值
1. Domain：規則寫成 entity 方法，時間用參數傳入 → 不需 mock 的單元測試
2. Port：沿用既有，必要時才加方法；用業務語言命名
3. Use case：載入 → 呼叫 entity → 儲存 → 副作用 → 回傳 DTO；不寫業務 `if`
4. Use case 測試：in-memory fakes；此時還沒有任何 adapter
5. CSV adapter（`<adapters>/persistence/csv/`）+ contract test
6. Driving adapter（controller / CLI）+ 錯誤對應
7. 組裝：`<main>` 依 `<STORAGE=csv|sql>` 切換
8. 用 CSV 跑通 → **交付給使用者確認**
9. 使用者同意後才做 SQL adapter；contract test 須同時通過，domain / application 零修改

目前儲存：`<csv>`｜資料夾：`<data/>`

## 發現與預設不同時（含使用者糾正）
- 不違反硬規則、使用者明說或 ≥ 2 處一致 → 寫入 conventions.md
- 違反硬規則 → 寫入 debt.md（不仿照、不擅自修）
- 兩種寫法矛盾 → 先問使用者
- 格式：`## C-00N [標籤] 標題` + 預設 / 本專案 / 依據（日期）三行；記錄後一行告知使用者

## 卡上沒寫到時才查 skill
步驟細節 `workflows/new-feature.md`｜CSV 樣板 `concepts/csv-first.md`｜換 DB `workflows/add-adapter.md`
放哪不確定 `concepts/placement-guide.md`｜審查 `review/checklist.md`｜跨層 `concepts/crossing-boundaries.md`

---
