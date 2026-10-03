---
name: clean-architecture
description: 以 Clean Architecture（乾淨架構）設計、實作、重構或審查程式碼。提供分層規則、逐步實作流程、Java 範例程式碼（相容 JDK 1.7）、兩種資料夾結構（by-layer / by-feature）與審查清單。當使用者要求「用乾淨架構」「分層」「解耦框架」「新增 use case」或審查架構時使用。
---

# Clean Architecture Skill

## 讀取策略（必讀）

1. **專案有 `docs/architecture/card.md`** → 你不該在讀這頁。改讀 card.md + conventions.md，照卡上的步驟做；
   改程式前讀 map.md 取代掃描原始碼；做判斷前先搜尋 decisions.md。
2. **專案沒有** → 先做首次設定：讀 [workflows/project-interview.md](workflows/project-interview.md)。**先問使用者，不要掃描原始碼。**
3. **卡上沒寫到才查 skill 文件**，一次只查一份。
4. **大檔先看標題再讀章節**：先搜尋 `^## ` 取得章節列表，只讀需要的那段（工具支援指定行數範圍時）。
5. `languages/*.md` **只在首次設定讀一次**，把要點寫進 card，之後不再讀。
6. **不讀**：README.md、其他架構的 skill、未選用的結構文件、同一對話中已讀過的檔案。
7. **委派子代理時**只給 card.md、conventions.md、map.md 路徑與任務描述，不要叫子代理讀 skill。
8. **每個任務結束前做收尾記錄**（[workflows/wrap-up.md](workflows/wrap-up.md)）：把這次推理出的結論寫進專案記憶，下次不必重想。

## 五條硬規則

1. **依賴只能由外往內**：`infrastructure → adapters → application → domain`。
2. **domain 與 application 不得 import 框架、ORM、HTTP、SDK。**
3. **業務規則放 Entity；流程編排放 Use Case；格式轉換放 Adapter。**
4. **內層需要外部能力時，內層定義 Port，外層實作 Adapter。** 時間、ID、亂數也算。
5. **物件組裝只發生在 Composition Root。**

## 四層

| 層 | 佔位符 | 放什麼 | 詳細（規則、範例程式碼、與其他層的配合） |
|---|---|---|---|
| Entities | `<domain>` | Entity、Value、領域錯誤（沒有電腦也成立的規則） | [layers/01-entities.md](layers/01-entities.md) |
| Use Cases | `<application>` | Use case、Port 介面、DTO（系統執行一個操作的步驟） | [layers/02-use-cases.md](layers/02-use-cases.md) |
| Interface Adapters | `<adapters>` | Controller、Repository 實作（CSV / SQL）、Mapper | [layers/03-interface-adapters.md](layers/03-interface-adapters.md) |
| Frameworks & Drivers | `<infrastructure>`、`<main>` | 連線、Server、設定、組裝 | [layers/04-frameworks-drivers.md](layers/04-frameworks-drivers.md) |

範例程式碼是 **Java，相容 JDK 1.7**（約定見 [shared/code-conventions.md](../../shared/code-conventions.md)）；其他語言對照 `languages/`。
範例中的 `<domain>` 等佔位符，依專案 card.md 的路徑表替換。**寫哪一層就只讀那一層的檔案。**

## 任務表（卡上沒寫到時才查）

| 任務 | 讀 |
|---|---|
| 首次設定 | [workflows/project-interview.md](workflows/project-interview.md) |
| 新增功能（第一次在此專案做） | [workflows/new-feature.md](workflows/new-feature.md) |
| 只加一個 use case | [workflows/new-use-case.md](workflows/new-use-case.md) |
| 寫 CSV adapter | [concepts/csv-first.md](concepts/csv-first.md) |
| CSV 換 DB / 加第三方 / 加入口 | [workflows/add-adapter.md](workflows/add-adapter.md) |
| 編碼不是 UTF-8 | [concepts/file-encoding.md](concepts/file-encoding.md) |
| 重構舊程式 | [workflows/refactor-legacy.md](workflows/refactor-legacy.md) |
| 寫某一層但不確定怎麼寫 | `layers/0N-<該層>.md`（見上方四層表） |
| 不確定程式碼放哪 | [concepts/placement-guide.md](concepts/placement-guide.md) |
| DTO：種類、放哪、誰轉換、PATCH | [concepts/dto.md](concepts/dto.md) |
| 錯誤 / 交易 / 競態 / Entity ⇄ Row 跨層 | [concepts/crossing-boundaries.md](concepts/crossing-boundaries.md) |
| 一件事發生後有多個後續動作、跨模組通知 | [concepts/domain-events.md](concepts/domain-events.md) |
| 前端專案（Vue / React） | [concepts/frontend.md](concepts/frontend.md) |
| 審查程式碼 | [review/checklist.md](review/checklist.md) + 專案 debt.md |
| 記錄慣例 / 偏差 | [workflows/record-convention.md](workflows/record-convention.md) |
| 任務收尾，該記什麼 | [workflows/wrap-up.md](workflows/wrap-up.md) |
| 想看完整範例 | [examples/borrow-book-end-to-end.md](examples/borrow-book-end-to-end.md) |

範例領域是圖書借閱（`Member` 借 `Book` 產生 `Loan`，規則 R1–R7），看不懂規則編號時才讀 [shared/example-domain.md](../../shared/example-domain.md)。
