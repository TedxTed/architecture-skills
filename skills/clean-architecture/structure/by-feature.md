# 結構 B：依功能模組分（by-feature / modular）

適用：多個業務子領域、多人協作、未來可能拆服務。
做法：**先按模組切，模組內再按層切。** 每個模組內部就是一個小型的 [by-layer](by-layer.md)。

## 模組劃分（圖書範例）

| 模組 | 負責 | 主要 entity |
|---|---|---|
| `lending` 借閱 | 借書、還書、續借、罰金 | Loan |
| `catalog` 館藏 | 新增書籍、搜尋、書況 | Book |
| `membership` 會員 | 註冊、等級、停權 | Member |

## 目錄樹

```
project/
├── src/
│   ├── modules/
│   │   ├── lending/
│   │   │   ├── domain/
│   │   │   │   ├── loan.x
│   │   │   │   ├── borrower.x               # lending 眼中的「會員」：只有借閱需要的欄位
│   │   │   │   └── errors.x
│   │   │   ├── application/
│   │   │   │   ├── ports/
│   │   │   │   │   ├── loan_repository.x
│   │   │   │   │   ├── borrower_lookup.x    # 向 membership 取資料的 port
│   │   │   │   │   └── book_availability.x  # 向 catalog 操作的 port
│   │   │   │   └── use_cases/
│   │   │   │       ├── borrow_book/
│   │   │   │       ├── return_book/
│   │   │   │       └── renew_loan/
│   │   │   ├── adapters/
│   │   │   │   ├── http/
│   │   │   │   ├── persistence/
│   │   │   │   └── integration/             # 實作 borrower_lookup 等，呼叫其他模組
│   │   │   ├── public_api.x                 # 本模組對外公開的唯一入口
│   │   │   └── module.x                     # 本模組的組裝（子 composition root）
│   │   │
│   │   ├── catalog/
│   │   │   ├── domain/ ├── application/ ├── adapters/
│   │   │   ├── public_api.x
│   │   │   └── module.x
│   │   │
│   │   └── membership/
│   │       └── ...（同上）
│   │
│   ├── shared_kernel/                       # 所有模組共用、極少變動的東西
│   │   ├── money.x
│   │   ├── clock.x                          # Clock port
│   │   └── result.x
│   │
│   ├── infrastructure/                      # 跨模組共用的技術：DB 連線、web server、config
│   └── main.x                               # 呼叫各 module.x 並啟動
│
└── tests/
    └── modules/lending/ ...                 # 鏡像 src 結構
```

## 模組之間的規則（比層與層之間更重要）

1. **模組只能透過對方的 `public_api` 互動**，不得 import 對方的 `domain/`、`application/`、`adapters/`。
2. **呼叫其他模組 = 呼叫外部系統**：在自己的 `application/ports/` 定義需要的能力，在自己的 `adapters/integration/` 實作，內部呼叫對方的 `public_api`。
3. **各模組有自己的模型**：`lending` 的 `Borrower` 只有 `id, tier, suspended`，不共用 `membership` 的 `Member` entity。
4. **`shared_kernel` 保持極小**：只放真正通用且穩定的 Value 與 port。不確定就不要放。
5. **不跨模組 join 資料表**。每個模組擁有自己的表。

```
// FILE: src/modules/lending/application/ports/borrower_lookup.x
PORT BorrowerLookup
  FUNCTION find(memberId: MemberId) -> Borrower | Nothing

// FILE: src/modules/lending/adapters/integration/membership_borrower_lookup.x
ADAPTER MembershipBorrowerLookup IMPLEMENTS BorrowerLookup
  DEPENDS ON membership: MembershipPublicApi        // 對方的 public_api
  FUNCTION find(memberId)
    dto ← membership.getMemberSummary(memberId.value)
    IF dto == Nothing RETURN Nothing
    RETURN Borrower(MemberId(dto.id), tier: dto.tier, suspended: dto.suspended)

// FILE: src/modules/membership/public_api.x
PORT MembershipPublicApi                            // 介面 + DTO，只暴露這些
  FUNCTION getMemberSummary(id: String) -> MemberSummaryDto | Nothing
```

這樣 `lending` 未來要把 `membership` 換成遠端 HTTP 服務，只需要換掉 `MembershipBorrowerLookup` 這一個 adapter。

## 模組的 import 白名單

| 位置 | 可 import |
|---|---|
| `modules/X/domain/` | `modules/X/domain/`、`shared_kernel/` |
| `modules/X/application/` | `modules/X/domain/`、`modules/X/application/`、`shared_kernel/` |
| `modules/X/adapters/` | `modules/X/**`、`modules/Y/public_api`、外部函式庫 |
| `modules/X/module.x` | `modules/X/**`、`infrastructure/` |
| `main` | 全部 |
