# 模板：架構卡（card.md）

> AI 指引：首次設定時在使用者專案建立 `docs/architecture/`，放入 `card.md`（本模板 `---` 之間）、
> [conventions.md](conventions.md)、[map.md](map.md)、[decisions.md](decisions.md)、[debt.md](debt.md)。
> 這些檔案**一律存成 UTF-8**（即使專案是 Big5）。
> 填入實際值後刪除所有 `<>` 提示。**目標 70 行以內**：這張卡是日常任務唯一必讀的檔案，
> 要讓 AI 不查 skill 也能完成一般功能開發。

---

# 架構卡

Clean Architecture｜Skill：`<docs/architecture-skills/skills/clean-architecture/>`
形態：`<純後端 / 全端（後端在 server/）/ 純前端 / CLI>`
語言 / 框架：`<Java 1.7 / Servlet 3.0 / Maven>`｜DB：`<Oracle + JdbcTemplate>`｜base package：`<com.example.library>`
編碼：原始碼 `<UTF-8 / CP950>`｜CSV `<UTF-8 / UTF-8 BOM / CP950>`｜換行 `<LF / CRLF>`
`<非 UTF-8 才保留此行：讀寫檔案前先讀 skill 的 concepts/file-encoding.md；不使用 emoji>`

**讀取**：本檔 + conventions.md（照做，不重新推理）。
改程式前讀 map.md（**取代掃描原始碼**）。做設計判斷前搜尋 decisions.md 標題 `^## DEC-.*\[關鍵字\]`，命中就採用結論。
debt.md 只在審查 / 重構時讀。
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
| `<domain>` | `<src/main/java/com/example/library/{module}/domain>` |
| `<application>` | `<...>` |
| `<adapters>` | `<...>` |
| `<infrastructure>` | `<...>` |
| `<main>` | `<...>` |
| `<tests>` | `<...（不寫測試時寫「不寫，見 C-00N」）>` |
| 共用 port | `<...>` |
| 共用 adapter | `<...>` |

模組：`<lending 借閱、catalog 館藏>`

## 語言寫法（首次設定時濃縮）
- Port：`<interface，放 <application>/port/>`｜Use case：`<class + execute(XxxInput)，建構子注入，不加 @Service>`
- 錯誤：`<throw new XxxException()，繼承 DomainException / AppException（unchecked）>`
- DTO：`<final class + final 欄位 + getter>`｜找不到資料：`<回傳 null（JDK 1.7）>`｜回呼：`<匿名類別>`
- 框架與組裝：`<無 DI 框架：組裝類別 LendingModule；Web adapter = LoanWebAdapter + Servlet 綁定；儲存依 storage=csv|sql>`

## 常用指令（試出可用的指令就補上）
- 編譯：`<mvn compile>`｜測試：`<mvn test>`｜啟動：`<mvn jetty:run（config/application.properties：storage=csv）>`｜依賴檢查：`<java -cp target/check CheckArch src/main/java com.example.library>`

## 新功能步驟（每步通過檢查才進下一步）
0. 拆規則表（Entity 規則 / Use case 流程 / I/O / 需要的 Port）；不清楚的**一次問完**，附預設值
1. Domain：規則寫成 entity 方法，時間用參數傳入 → 不需 mock 的單元測試
2. Port：沿用既有，必要時才加方法；用業務語言命名
3. Use case：載入 → 呼叫 entity → 儲存 → 副作用 → 回傳 DTO；不寫業務 `if`
4. Use case 測試：in-memory fakes；此時還沒有任何 adapter
5. CSV adapter（`<adapters>/persistence/csv/`）+ contract test
6. Driving adapter（controller / CLI）+ 錯誤對應
7. 組裝：在 `<main>` 的組裝類別建立；儲存依設定 `<storage=csv | sql>` 切換
8. 用 CSV 跑通 → **交付給使用者確認**
9. 使用者同意後才做 SQL adapter；contract test 須同時通過，domain / application 零修改
10. 收尾記錄（見下）

目前儲存：`<csv>`｜資料夾：`<data/>`

## 收尾（每個任務結束前必做，沒有新東西就跳過）
- 新增 / 改了 entity、port 方法、use case、adapter → `map.md` 每項一行
- 推理過的判斷、調查結論、試過失敗的做法 → `decisions.md`：`## DEC-N [模組][主題] 結論` + 理由 / 否決
- 試出可用的指令 → 本檔「常用指令」
- 與預設不同的慣例（使用者明說或 ≥ 2 處一致）→ `conventions.md`：`## C-N [標籤] 標題` + 預設 / 本專案 / 依據
- 違反硬規則的既有程式碼 → `debt.md`；兩種寫法矛盾 → 先問使用者
- 只記結論，不記推理過程；與程式碼不符時以程式碼為準並修正；最後一行告知使用者記了什麼

## 卡上沒寫到時才查 skill（任務 → 文件對照見 skill 的 SKILL.md 任務表）
各層 `layers/0N-*.md`｜步驟 `workflows/new-feature.md`｜CSV `concepts/csv-first.md`｜換 DB `workflows/add-adapter.md`
放哪 `concepts/placement-guide.md`｜DTO `concepts/dto.md`｜錯誤 / 交易 / 競態 `concepts/crossing-boundaries.md`｜審查 `review/checklist.md`

---
