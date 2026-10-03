# Workflow：新增一個功能（從需求到可運行）

適用：使用者說「幫我做一個 X 功能 / API」。
每一步都有**產出**與**檢查點**。檢查點沒過，不進下一步。

---

## Step 0 — 理解需求，產出規則清單

**動作**：把需求拆成表格，並向使用者確認不清楚的地方。

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

## Step 5 — Adapters

**動作**：
1. **Driving**：controller / CLI handler —— 解析輸入 → Input DTO → 呼叫 use case → 轉換輸出
2. **錯誤對應**：新的錯誤加進 `error_mapping`
3. **Driven**：若有新 port 或新 port 方法，實作它（含 mapper）

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
- [ ] repository 的 SQL / ORM 程式碼沒有洩漏到 adapter 以外

---

## Step 6 — 組裝

**動作**：在 `main`（或模組的 `module.x`）建立 use case、注入 port 實作、接到 controller、註冊路由。

✅ **檢查點**：只有 `main` 中出現 `new SqlXxx(...)` / `SystemClock()`。

---

## Step 7 — 整合測試與最終審查

**動作**：
1. 一個端到端或 adapter 整合測試（HTTP → DB），只測主要成功路徑 + 一個錯誤
2. 跑過 [review/checklist.md](../review/checklist.md)
3. 向使用者摘要：新增 / 修改的檔案、做了哪些取捨

## 給使用者的摘要模板

```
已完成「<功能名稱>」：
- 規則：R7a/R7b/R7c → Loan.renew()
- Use case：RenewLoan（application/use_cases/renew_loan/）
- API：POST /loans/{id}/renew → 200 { dueDate }；錯誤 404/422
- 測試：domain 3 個、use case 4 個、整合 1 個
- 取捨：<例：沿用既有 LoanRepository，未新增 port>
```
