# 結構 B：依功能模組分（by-feature / modular）

適用：多個業務子領域、多人協作、未來可能拆服務。
做法：**先按模組切，模組內再按層切。** 每個模組內部就是一個小型的 [by-layer](by-layer.md)。

## 模組劃分（圖書範例）

| 模組 | 負責 | 主要 entity |
|---|---|---|
| `lending` 借閱 | 借書、還書、續借、罰金 | Loan |
| `catalog` 館藏 | 新增書籍、搜尋、書況 | Book |
| `membership` 會員 | 註冊、等級、停權 | Member |

## 與 DDD 名詞的對照

by-feature 的做法和 DDD（Domain-Driven Design）的戰略設計是同一件事，只是名稱不同：

| 本 skill | DDD 名詞 | 說明 |
|---|---|---|
| 模組（`com.example.library.lending`） | **Bounded Context** | 一個模型有效的範圍；同一個詞在不同模組可以有不同意思 |
| `lending` 的 `Borrower` vs `membership` 的 `Member` | 各 context 自己的模型 | 同一個「人」，在借閱眼中只需要等級與停權狀態 |
| `adapter/integration/` + 自己的模型 | **Anti-Corruption Layer** | 把對方模型翻譯成自己的，避免對方的變動滲透進來 |
| `api` package | **Open Host Service / Published Language** | 模組對外公開的契約 |
| `sharedkernel` | **Shared Kernel** | 兩個以上 context 共用、須共同維護的極小部分 |
| 模組間的事件 | **Integration Event** | 見 [domain-events.md](../concepts/domain-events.md) |
| 模組之間的關係圖 | **Context Map** | 模組多時建議畫出來，記入專案的 `decisions.md` |

## 目錄樹

Java 專案中，「模組」就是 base package 下的第一層 package：

```
project/
├── pom.xml
├── src/main/java/com/example/library/
│   ├── lending/                                  # 模組
│   │   ├── api/                                  # 本模組對外公開的唯一入口（介面 + DTO）
│   │   │   └── LendingApi.java
│   │   ├── domain/
│   │   │   ├── Loan.java
│   │   │   ├── Borrower.java                     # lending 眼中的「會員」：只有借閱需要的欄位
│   │   │   └── DomainException.java + 各領域錯誤
│   │   ├── application/
│   │   │   ├── port/
│   │   │   │   ├── LoanRepository.java
│   │   │   │   ├── BorrowerLookup.java           # 向 membership 取資料的 port
│   │   │   │   └── BookAvailability.java         # 向 catalog 操作的 port
│   │   │   └── usecase/
│   │   │       ├── borrowbook/
│   │   │       ├── returnbook/
│   │   │       └── renewloan/
│   │   ├── adapter/
│   │   │   ├── web/
│   │   │   ├── persistence/csv/                  # ① 新功能先做
│   │   │   ├── persistence/sql/                  # ② 使用者確認後才做
│   │   │   ├── integration/                      # 實作 BorrowerLookup 等，呼叫其他模組的 api
│   │   │   └── api/LendingApiImpl.java           # 實作本模組的 LendingApi（呼叫本模組的 use case）
│   │   └── config/
│   │       └── LendingConfig.java                # 本模組的組裝（子 composition root）
│   │
│   ├── catalog/            # 結構同上：api / domain / application / adapter / config
│   ├── membership/         # 結構同上
│   │
│   ├── sharedkernel/                             # 所有模組共用、極少變動的東西
│   │   ├── Clock.java                            # 共用 port
│   │   └── UnitOfWork.java
│   │
│   └── infrastructure/                           # 跨模組共用的技術
│       ├── adapter/SystemClock.java              # 共用 port 的實作
│       └── LibraryApplication.java               # 啟動：建立各模組的組裝類別並接起來
│
└── src/test/java/com/example/library/lending/ ... # 鏡像 main 結構
```

## 模組之間的規則（比層與層之間更重要）

1. **模組只能透過對方的 `api` package 互動**，不得 import 對方的 `domain`、`application`、`adapter`。
2. **呼叫其他模組 = 呼叫外部系統**：在自己的 `application/port/` 定義需要的能力，在自己的 `adapter/integration/` 實作，內部呼叫對方的 `api`。
3. **各模組有自己的模型**：`lending` 的 `Borrower` 只有 `id, tier, suspended`，不共用 `membership` 的 `Member` entity。
4. **`sharedkernel` 保持極小**：只放真正通用且穩定的 Value 與 port。不確定就不要放。
5. **不跨模組 join 資料表**。每個模組擁有自己的表。

```java
// FILE: lending/application/port/BorrowerLookup.java
public interface BorrowerLookup {
    Borrower find(MemberId memberId);                     // 找不到回傳 null
}

// FILE: lending/adapter/integration/MembershipBorrowerLookup.java
public class MembershipBorrowerLookup implements BorrowerLookup {
    private final MembershipApi membership;               // 對方模組的 api

    public MembershipBorrowerLookup(MembershipApi membership) { this.membership = membership; }

    @Override
    public Borrower find(MemberId memberId) {
        MemberSummary dto = membership.getMemberSummary(memberId.getValue());
        if (dto == null) {
            return null;
        }
        return new Borrower(memberId, "VIP".equals(dto.getTier()), dto.isSuspended());   // 翻譯成自己的模型
    }
}

// FILE: membership/api/MembershipApi.java                （只暴露介面與基本型別的 DTO）
public interface MembershipApi {
    MemberSummary getMemberSummary(String memberId);      // 找不到回傳 null
}

// FILE: membership/api/MemberSummary.java
public final class MemberSummary {
    private final String id;
    private final String tier;                            // 用字串，不暴露 membership 的 enum
    private final boolean suspended;
    // 建構子與 getter 略
}
```

這樣 `lending` 未來要把 `membership` 換成遠端 HTTP 服務，只需要換掉 `MembershipBorrowerLookup` 這一個 adapter。

## 模組的 import 白名單

| 位置 | 可 import |
|---|---|
| `X.domain` | `X.domain`、`sharedkernel` |
| `X.application` | `X.domain`、`X.application`、`sharedkernel` |
| `X.adapter` | `X.*`、`Y.api`、外部函式庫 |
| `X.api` | 只有 JDK 型別（它是對外契約） |
| `X.config` | `X.*`、`Y.api`、`infrastructure` |
| `infrastructure` | 全部 |

> 也可以採用 [Spring Modulith](https://spring.io/projects/spring-modulith) 的慣例：模組根 package（`com.example.library.lending`）本身就是公開 API，子 package 都是內部實作。
> Spring Modulith 需要 Java 17；Java 7 / 8 專案請用上面的 `api` package 做法。
