# Workflow：新增一個 Use Case（快速版）

適用：domain 已經存在，只需要加一個操作。完整流程見 [new-feature.md](new-feature.md)。

## 樣板

複製後替換 `<>` 內容：

```
// FILE: <application>/use_cases/<verb_noun>/<verb_noun>_dto.x
DTO <VerbNoun>Input
  <field>: <基本型別>

DTO <VerbNoun>Output
  <field>: <基本型別>

// FILE: <application>/use_cases/<verb_noun>/<verb_noun>.x
USE_CASE <VerbNoun>
  DEPENDS ON
    <repo>:  <Entity>Repository
    clock:   Clock                     // 需要時間才加
    uow:     UnitOfWork                // 改超過一個 aggregate 才加

  FUNCTION execute(input: <VerbNoun>Input) -> <VerbNoun>Output
    // 1. 轉換輸入
    id ← TRY <Entity>Id(input.id)

    // 2. 載入
    entity ← <repo>.findById(id)
    IF entity == Nothing  FAIL <Entity>NotFound

    // 3. 業務規則：只呼叫 entity 方法
    TRY entity.<businessVerb>(<args>, clock.now())

    // 4. 儲存
    <repo>.save(entity)

    // 5. 副作用（若有）

    // 6. 回傳
    RETURN <VerbNoun>Output(...)

  MUST NOT import adapters, infrastructure, framework
```

## 命名規則

| 項目 | 規則 | ✅ | ❌ |
|---|---|---|---|
| Use case | 動詞 + 名詞，使用者意圖 | `BorrowBook`、`RenewLoan` | `LoanService`、`UpdateLoan`、`LoanManager` |
| 資料夾 | 同 use case，snake / kebab 依語言 | `borrow_book/` | `loan/` |
| 方法 | 一個 use case 一個公開方法 | `execute` / `handle` / `invoke` | 一個類別多個公開方法 |

> `LoanService` 這種「一個類別裝所有操作」的寫法，會隨時間膨脹成上帝類別。一個 use case 一個類別。

## 查詢型 Use Case（Read）

不修改狀態、只讀資料時，可以跳過 entity，直接用 Query port 取得 read model：

```
// FILE: <application>/ports/loan_queries.x
PORT LoanQueries
  FUNCTION listByMember(memberId: MemberId) -> List<LoanSummary>

DTO LoanSummary { loanId, bookTitle, dueDate, isOverdue }

// FILE: <application>/use_cases/list_member_loans/list_member_loans.x
USE_CASE ListMemberLoans
  DEPENDS ON queries: LoanQueries
  FUNCTION execute(input) -> List<LoanSummary>
    RETURN queries.listByMember(MemberId(input.memberId))
```

`SqlLoanQueries` 可以直接 join 多張表、回傳 DTO，不必經過 entity。

## 完成檢查

- [ ] 一個資料夾、一個 use case、一個公開方法
- [ ] 沒有業務規則寫在 use case 裡
- [ ] 有 use case 測試（成功 + 每種失敗）
- [ ] 已在 composition root 組裝
- [ ] 已接到某個 driving adapter（否則它不會被執行）
