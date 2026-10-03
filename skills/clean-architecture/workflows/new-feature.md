# Workflow：新增一個功能（從需求到可運行）

適用：使用者說「幫我做一個 X 功能 / API」。
每一步都有**產出**與**檢查點**。檢查點沒過，不進下一步。

---

## Step 0 — 理解需求，產出規則清單

**動作**：
1. 把需求拆成下方表格
2. 不清楚的規則**一次問完**，每題附選項與預設值，使用者可回「全部預設」
3. **先問再寫程式**：寧可多問一輪，也不要寫完才發現規則理解錯誤而重做
4. 業務規則的答案寫進程式碼與測試，**不寫入 conventions.md**（那裡只放架構慣例）

```
開始做「續借」之前，確認幾點（可回「全部預設」）：
1. 續借次數上限？ A. 1 次（預設）  B. 2 次  C. 不限
2. 已逾期可以續借嗎？ A. 不行（預設）  B. 可以，但要先繳罰金
3. 續借成功要通知會員嗎？ A. 不用（預設）  B. Email
```

| 編號 | 規則 / 行為 | 分類 |
|---|---|---|
| R? | ... | 企業規則 → Entity |
| A? | ... | 應用規則 → Use Case |
| I/O | 輸入有哪些、輸出要什麼 | DTO |
| EXT | 需要哪些外部能力（存、查、寄、時間） | Port |

分類方式見 [placement-guide.md](../concepts/placement-guide.md)。

**範例**（續借）：
| 編號 | 規則 | 分類 |
|---|---|---|
| R7a | 已逾期不可續借 | Entity: Loan |
| R7b | 最多續借 1 次 | Entity: Loan |
| R7c | 續借延長 14 天 | Entity: Loan |
| I/O | 輸入 loanId；輸出 newDueDate | DTO |
| EXT | 讀寫 Loan、取得現在時間 | LoanRepository、Clock |

✅ **檢查點**：每一條規則都有分類；使用者確認過不明確的規則。

---

## Step 1 — Domain：寫 Entity / Value / 錯誤

**動作**：
1. 找出受影響的 entity；沒有就新建
2. 把每條 R 規則寫成 entity 方法；**時間等外部值用參數傳入**
3. 每種違規定義一個領域錯誤
4. 寫 domain 單元測試：每條規則至少一個成功、一個失敗案例

```
// FILE: <domain>/loan.x   （新增方法）
FUNCTION renew(now: DateTime)
  IF isOverdue(now)           FAIL CannotRenewOverdueLoan     // R7a
  IF renewCount >= MAX_RENEW  FAIL RenewLimitExceeded         // R7b
  dueDate    ← dueDate + LOAN_DAYS                            // R7c
  renewCount ← renewCount + 1
```

✅ **檢查點**：
- [ ] `domain/` 沒有 import 任何 domain 以外的東西
- [ ] 方法名稱是業務動詞（`renew`、`markAsLent`），不是 `setDueDate`
- [ ] 測試不需要任何 mock 就能跑

---

## Step 2 — Application：Ports

**動作**：列出 use case 需要的外部能力。已存在的 port 直接用；需要新方法就加到既有 port；全新能力才新增 port。

```
// FILE: <application>/ports/loan_repository.x   （已存在，確認有 findById / save）
```

✅ **檢查點**：
- [ ] port 方法名是業務語言，參數 / 回傳是 domain 型別
- [ ] 沒有為了「可能以後用到」而加方法

---

## Step 3 — Application：DTO + Use Case

**動作**：照 [use case 標準步驟](../concepts/layers.md#2-use-casesapplication) 寫：轉換輸入 → 載入 → 呼叫 entity → 儲存 → 副作用 → 回傳 DTO。

```
// FILE: <application>/use_cases/renew_loan/renew_loan_dto.x
DTO RenewLoanInput  { loanId: String }
DTO RenewLoanOutput { newDueDate: Date }

// FILE: <application>/use_cases/renew_loan/renew_loan.x
USE_CASE RenewLoan
  DEPENDS ON loans: LoanRepository, clock: Clock

  FUNCTION execute(input: RenewLoanInput) -> RenewLoanOutput
    loan ← loans.findById(LoanId(input.loanId))
    IF loan == Nothing  FAIL LoanNotFound
    TRY loan.renew(clock.now())
    loans.save(loan)
    RETURN RenewLoanOutput(newDueDate: loan.dueDate)
```

✅ **檢查點**：
- [ ] use case 裡沒有業務規則的 `IF`（只有「找不到」之類的流程判斷）
- [ ] 回傳 DTO，不是 entity
- [ ] 沒有 import adapters / 框架

---

## Step 4 — Use Case 測試

**動作**：用 in-memory fakes 測 use case。至少涵蓋：成功路徑、每種 FAIL、副作用是否在失敗時**不**發生。

```
TEST "已續借過一次的借閱不能再續借"
  loans ← InMemoryLoanRepository([loanWith(renewCount: 1)])
  useCase ← RenewLoan(loans, FixedClock(...))
  EXPECT useCase.execute(RenewLoanInput("l1")) FAILS WITH RenewLimitExceeded
```

若 fakes 尚不存在，新增到 `tests/fakes/`。

✅ **檢查點**：測試全過，且**此時還沒有寫任何 adapter**。這證明業務邏輯與技術細節已解耦。

---

## Step 5 — Driven Adapter：先用 CSV，不接資料庫

**動作**：新 port 或新 port 方法，**先實作 CSV 版本**。做法與樣板見 [concepts/csv-first.md](../concepts/csv-first.md)。

```
// FILE: <adapters>/persistence/csv/csv_loan_repository.x   （新增或補方法）
FUNCTION findById(id)
  RETURN readRows("loans.csv").find(r -> r.id == id.value).map(toEntity)
```

若專案已有 DB adapter，**新增的 port 方法仍先在 CSV 版本實作並跑通**，再補 DB 版本（Step 9）。
若使用者已在 `conventions.md` 記錄「跳過 CSV 階段」，照記錄執行。

✅ **檢查點**：
- [ ] port 介面中沒有任何 SQL / ORM 的概念（CSV 實作得出來，就代表 port 是乾淨的）
- [ ] CSV adapter 通過 repository contract test（見 [add-adapter.md](add-adapter.md#repository-contract-test強烈建議)）

---

## Step 6 — Driving Adapter

**動作**：
1. controller / CLI handler —— 解析輸入 → Input DTO → 呼叫 use case → 轉換輸出
2. 新的錯誤加進 `error_mapping`

```
// FILE: <adapters>/http/loan_controller.x   （新增）
// POST /loans/{id}/renew
FUNCTION handleRenew(request)
  result ← renewLoan.execute(RenewLoanInput(loanId: request.pathParam("id")))
  ON ERROR e  → RETURN errorResponse(e)
  RETURN HttpResponse(200, { dueDate: formatIsoDate(result.newDueDate) })

// FILE: <adapters>/http/error_mapping.x   （新增對應）
CannotRenewOverdueLoan, RenewLimitExceeded → (422, error.code)
LoanNotFound                               → (404, error.code)
```

✅ **檢查點**：
- [ ] controller 沒有業務判斷
- [ ] 新錯誤都有 HTTP 對應

---

## Step 7 — 組裝

**動作**：在 `main`（或模組的 `module.x`）建立 use case、依設定選擇儲存 adapter、接到 controller、註冊路由。

```
storage ← config.storage            // "csv"（預設）| "sql"
loans   ← storage == "csv" ? CsvLoanRepository(config.dataDir) : SqlLoanRepository(db)
```

✅ **檢查點**：只有 `main` 中出現 `CsvXxx(...)` / `SqlXxx(...)` / `SystemClock()`。

---

## Step 8 — 用 CSV 跑通端到端 ★ 交付點

**動作**：
1. 以 `storage=csv` 啟動，實際呼叫 API / CLI，確認主要成功路徑 + 一個錯誤
2. 打開 CSV 檔，確認資料寫入正確
3. 跑過 [review/checklist.md](../review/checklist.md)
4. 向使用者摘要，並詢問：**功能行為是否符合預期？要現在接資料庫，還是之後再接？**

✅ **檢查點**：使用者確認功能行為正確。**到此功能已完成**，資料庫只是換儲存方式。

---

## Step 9 — 換成資料庫（使用者確認後才做，可另開任務）

**動作**：依 [add-adapter.md 情境 A](add-adapter.md#情境-a替換-driven-adapter例csv--postgresql) 實作 DB adapter：
1. 實作 `SqlXxxRepository`（含 mapper、migration）
2. **跑同一套 contract test**，CSV 與 SQL 都要通過
3. 切換設定 `storage=sql`，重跑 Step 8 的端到端驗證

✅ **檢查點**：`domain/`、`application/` 沒有任何變動。若需要改，代表 port 設計洩漏了技術細節，先修 port。

## 給使用者的摘要模板

```
已完成「<功能名稱>」：
- 規則：R7a/R7b/R7c → Loan.renew()
- Use case：RenewLoan（application/use_cases/renew_loan/）
- API：POST /loans/{id}/renew → 200 { dueDate }；錯誤 404/422
- 測試：domain 3 個、use case 4 個、contract 1 組
- 儲存：目前為 CSV（data/loans.csv）；資料庫尚未接上，確認後再進行
- 取捨：<例：沿用既有 LoanRepository，未新增 port>
```
