# 結構 A：依層分（by-layer）

適用：單一業務子領域、小到中型專案。

## 目錄樹

```
project/
├── src/
│   ├── domain/                              # ── Entities 層 ──
│   │   ├── member.x                         # Entity
│   │   ├── book.x                           # Entity
│   │   ├── loan.x                           # Entity
│   │   ├── ids.x                            # Value objects
│   │   ├── money.x                          # Value object
│   │   ├── errors.x                         # 領域錯誤
│   │   └── services/                        # （選用）跨 entity 的純規則
│   │
│   ├── application/                         # ── Use Cases 層 ──
│   │   ├── errors.x                         # 應用錯誤（NotFound 類）
│   │   ├── ports/                           # 輸出 port 介面（只有介面！）
│   │   │   ├── member_repository.x
│   │   │   ├── book_repository.x
│   │   │   ├── loan_repository.x
│   │   │   ├── loan_queries.x               # 查詢用 read model port
│   │   │   ├── clock.x
│   │   │   ├── notifier.x
│   │   │   └── unit_of_work.x
│   │   └── use_cases/
│   │       ├── borrow_book/
│   │       │   ├── borrow_book.x            # Interactor
│   │       │   └── borrow_book_dto.x        # Input / Output
│   │       ├── return_book/
│   │       ├── renew_loan/
│   │       └── list_member_loans/
│   │
│   ├── adapters/                            # ── Interface Adapters 層 ──
│   │   ├── http/                            # driving
│   │   │   ├── loan_controller.x
│   │   │   ├── error_mapping.x
│   │   │   └── schemas.x                    # request/response 格式定義
│   │   ├── cli/                             # driving
│   │   │   └── admin_commands.x
│   │   ├── persistence/                     # driven
│   │   │   ├── models.x                     # ORM model / table 定義
│   │   │   ├── sql_member_repository.x
│   │   │   ├── sql_book_repository.x
│   │   │   ├── sql_loan_repository.x
│   │   │   ├── sql_loan_queries.x
│   │   │   ├── sql_unit_of_work.x
│   │   │   └── mappers.x
│   │   ├── notification/                    # driven
│   │   │   └── smtp_notifier.x
│   │   └── time/
│   │       └── system_clock.x
│   │
│   ├── infrastructure/                      # ── Frameworks & Drivers 層 ──
│   │   ├── config.x                         # 讀 env
│   │   ├── database.x                       # 連線池
│   │   └── web_server.x                     # 框架初始化
│   │
│   └── main.x                               # Composition root
│
├── migrations/                              # DB schema migration
└── tests/
    ├── domain/                              # 純單元測試
    ├── application/                         # use case 測試 + fakes
    ├── fakes/                               # in-memory port 實作
    └── integration/                         # adapter 接真 DB / HTTP
```

## 每個資料夾的 import 白名單

| 資料夾 | 可 import |
|---|---|
| `domain/` | `domain/` |
| `application/` | `domain/`、`application/` |
| `adapters/` | `domain/`、`application/`、外部函式庫 |
| `infrastructure/` | 外部函式庫（通常不需要 import 內層） |
| `main` | 全部 |
| `tests/fakes/` | `domain/`、`application/` |

建議用工具強制執行，見各 [languages/](../languages/) 文件的「強制依賴規則」段落。

## 新增功能時要動的檔案（以「續借」為例）

```
+ src/domain/loan.x                          修改：加 renew()
+ src/domain/errors.x                        修改：加 RenewLimitExceeded
+ src/application/use_cases/renew_loan/      新增
+ src/adapters/http/loan_controller.x        修改：加 handleRenew
+ src/adapters/http/error_mapping.x          修改：加錯誤對應
+ src/main.x                                 修改：組裝 RenewLoan
+ tests/domain/loan_test.x                   修改
+ tests/application/renew_loan_test.x        新增
```

缺點也在這裡可見：一個功能散落在 4 個頂層資料夾。功能變多時改用 [by-feature](by-feature.md)。
