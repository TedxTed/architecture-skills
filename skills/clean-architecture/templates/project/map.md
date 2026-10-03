# 模板：專案地圖（map.md）

> AI 指引：把下方 `---` 之間的內容存為使用者專案的 `docs/architecture/map.md`（UTF-8）。
> 首次設定時**不要掃描原始碼來填**：只依資料夾名稱列出模組，其餘留空，之後每次任務收尾時把碰過的東西補上。
> 超過 150 行時，拆成 `docs/architecture/map/<module>.md`，本檔只留模組索引。

---

# 專案地圖

> 開始修改程式前先讀本檔，**用它取代掃描原始碼**。每行一項，只寫名稱、位置、要點。
> 任務收尾時更新；發現與程式碼不符時以程式碼為準並修正本檔。

## <模組名稱>
（尚未記錄，任務收尾時補上）

<!-- 範例格式：
## lending 借閱
- Entity：`Loan`（`src/lending/domain/loan.ts`）— open / close / renew / isOverdue；規則 R5 R6 R7
- Port：`LoanRepository` — findById / findOpenByMember / save / nextId
- Port（共用）：`Clock`、`UnitOfWork`（`src/shared_kernel/`）
- Use case：`BorrowBook`、`ReturnBook`、`RenewLoan`（`src/lending/application/use-cases/`）
- Adapter：CsvLoanRepository、SqlLoanRepository（未完成）、HttpLoanController（`POST /loans`、`POST /loans/{id}/renew`）
- 依賴其他模組：membership（`BorrowerLookup` → `MembershipPublicApi`）
-->

---
