# 模板：架構卡（card.md）

> AI 指引：首次設定時，在使用者專案建立 `docs/architecture/` 資料夾，放入三個檔案：
> - `card.md`：本模板（下方 `---` 之間）
> - `conventions.md`：[conventions.md 模板](conventions.md)
> - `debt.md`：[debt.md 模板](debt.md)
>
> 填入實際值後刪除所有 `<>` 提示。card.md **目標 60 行以內**，每次任務都會讀，越短越省 token。
> 並在專案的 CLAUDE.md / AGENTS.md / copilot-instructions.md 加一行：
> `架構相關任務先讀 docs/architecture/card.md 與 conventions.md。`

---

# 架構卡

架構：Clean Architecture｜Skill：`<docs/architecture-skills/skills/clean-architecture/>`
語言 / 框架：`<TypeScript / NestJS>`｜DB：`<PostgreSQL + Prisma>`

## 讀取順序
1. 本檔 → 2. `conventions.md`（本專案與 skill 預設不同之處，**照做，不重新推理**）
3. 依任務讀 skill 中的一份文件（見最下方）
4. `debt.md` 只在審查或重構時讀

## 優先順序
使用者當下指示 > conventions.md > 本檔 > skill 預設。
硬規則不可被 conventions 推翻；違反硬規則的既有程式碼記在 debt.md。

## 硬規則
1. 依賴由外往內：infrastructure → adapters → application → domain
2. domain、application 不 import 框架 / ORM / HTTP / SDK
3. 規則放 Entity，流程放 Use Case，轉換放 Adapter
4. 外部能力（含時間、ID）用 Port；實作在 Adapter
5. 只在 composition root 組裝

## 開發順序（每個新功能）
domain → application → use case 測試 → **CSV adapter** → driving adapter → 組裝
→ 用 CSV 跑通並請使用者確認 → 使用者同意後才接資料庫（contract test 須同時通過）
目前儲存：`<csv / sql>`｜切換方式：`<STORAGE=csv 環境變數>`｜資料夾：`<data/>`

## 結構：`<A. by-layer / B. by-feature>`

| 佔位符 | 本專案路徑 |
|---|---|
| `<domain>` | `<src/{module}/domain>` |
| `<application>` | `<...>` |
| `<adapters>` | `<...>` |
| `<infrastructure>` | `<...>` |
| `<main>` | `<...>` |
| `<tests>` | `<...>` |
| 共用 port | `<...>` |

模組（僅 B）：`<lending — 借閱>`、`<catalog — 館藏>`

## 維護規則（給 AI）
發現專案做法與 skill 預設不同時，依 skill 的 `workflows/record-convention.md` 記錄：
- 不違反硬規則、且已穩定出現 → 寫入 `conventions.md`
- 違反硬規則 → 寫入 `debt.md`
- 路徑類差異 → 同時更新上方路徑表
記錄後用一行告知使用者。

## 任務 → 讀哪份（skill 內相對路徑）
- 新功能：`workflows/new-feature.md`
- 新 use case：`workflows/new-use-case.md`
- CSV adapter：`concepts/csv-first.md`
- CSV 換 DB / 新 adapter：`workflows/add-adapter.md`
- 審查：`review/checklist.md`
- 放哪不確定：`concepts/placement-guide.md`

---
