# 選擇資料夾結構

兩種結構**都符合 Clean Architecture**。依賴規則管的是 import 方向，不是資料夾擺法。

| | A. by-layer | B. by-feature |
|---|---|---|
| 做法 | 專案頂層按層切 | 頂層按業務模組切，模組內再按層切 |
| 頂層長相 | `domain/ application/ adapters/` | `modules/lending/ modules/catalog/` |
| 優點 | 簡單、檔案少、一眼看出分層 | 一個功能集中在一個資料夾；邊界清楚；容易拆服務 |
| 缺點 | 一個功能散落在 4 個頂層資料夾；功能多時難找 | 多一層目錄；需要處理模組之間的互動 |
| 詳細 | [by-layer.md](by-layer.md) | [by-feature.md](by-feature.md) |

## 決策順序

1. **使用者已表達偏好** → 照使用者的
2. **專案已有結構** → 偵測後沿用（見下表）
3. **都沒有** → 依下表判斷；仍不確定就**問使用者**

| 問題 | 傾向 A | 傾向 B |
|---|---|---|
| 有幾個明顯的業務子領域？ | 1 個 | 2 個以上 |
| 團隊規模？ | 1–3 人 | 多人 / 多團隊 |
| 未來會拆服務？ | 不會 | 可能 |
| 預估 use case 數量？ | 15 個以下 | 更多 |

從 B 合併回 A 很容易，從 A 拆成 B 比較痛苦。

### 偵測既有結構

| 看到 | 判定 |
|---|---|
| `src/domain/`、`src/usecases/`、`src/infrastructure/` 在頂層 | A |
| `src/modules/*/`、`src/features/*/`、`src/contexts/*/`，裡面又有分層 | B |
| `src/<領域>/domain/` 直接在 src 下，沒有 `modules/` 這層 | B（變體）→ 記錄為 `[路徑]` 慣例 |
| `src/features/*/` 裡沒有分層，每個 feature 是平的 | 可能是 Vertical Slice，先問使用者 |
| `controllers/ services/ models/` | 尚未分層，見 [refactor-legacy](../workflows/refactor-legacy.md) |

## 功能要切多大

B 結構中的「模組」必須是**業務領域**，不是**單一操作**。

```
✅ 業務領域級                        ❌ 單一操作級
modules/                            features/
├── lending/                        ├── borrow-book/
│   ├── domain/   ← Loan 只有一份     │   ├── domain/   ← Loan？
│   ├── application/                │   └── ...
│   │   └── use_cases/              ├── return-book/
│   │       ├── borrow_book/        │   ├── domain/   ← Loan 又一份？
│   │       ├── return_book/        │   └── ...
│   │       └── renew_loan/         └── renew-loan/
│   └── adapters/
└── catalog/
```

單一操作級的問題：`Loan` 同時被借書、還書、續借使用。複製多份會讓規則分散；抽到共用資料夾又變回全域分層。

**判斷方法**：列出所有 use case，把**共用同一組 entity** 的 use case 歸成一組，每一組就是一個模組。

| Use case | 主要 entity | 模組 |
|---|---|---|
| BorrowBook、ReturnBook、RenewLoan | Loan | lending |
| AddBook、SearchBooks | Book | catalog |
| RegisterMember、SuspendMember | Member | membership |

> 如果每個操作真的幾乎不共用業務規則，那更適合 Vertical Slice Architecture，而不是 Clean Architecture 的 B 結構。

## 小專案精簡版（A 或 B 都適用）

保留依賴方向，減少檔案數量：

```
<domain>/           所有 entity 平放，不分子資料夾
<application>/
  ports.x           所有 port 放同一檔
  <use_case>.x      一個 use case 一檔，DTO 寫在同一檔
<adapters>/
  http.x
  persistence.x
<main>
```

可省略：輸入 port 介面、Presenter、獨立 mapper 檔。
**不可省略**：domain / application 不 import 框架；output port 介面。

## 是否需要 Clean Architecture

| 情境 | 建議 |
|---|---|
| 業務規則多、生命週期長、多個入口、可能換技術 | ✅ 完整版 |
| 規則中等、單一 API | ✅ 精簡版 |
| 純 CRUD、原型、一次性腳本 | ❌ 先告知使用者取捨，由使用者決定 |

## 命名對照

**沿用使用者既有命名**，不強迫改名。

| 本 skill | 其他常見名稱 |
|---|---|
| `domain/` | `entities/`、`core/`、`model/` |
| `application/` | `usecases/`、`app/` |
| `application/ports/` | `interfaces/`、`gateways/`、`contracts/` |
| `adapters/` | `interface_adapters/`、`infrastructure/`（Onion 風格）、`delivery/` + `repository/`（Go） |
| `infrastructure/` | `frameworks/`、`drivers/`、`platform/`、`config/` |
| `main` | `bootstrap/`、`cmd/`、`app.module`、`container` |
| `modules/`（B） | `features/`、`contexts/`、`components/` |
