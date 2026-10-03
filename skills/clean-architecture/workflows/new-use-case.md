# Workflow：新增一個 Use Case（快速版）

適用：domain 已經存在，只需要加一個操作。完整流程見 [new-feature.md](new-feature.md)。

## 樣板

Java，相容 JDK 1.7。複製後替換 `<>` 內容：

```java
// FILE: <application>/usecase/<verbnoun>/<VerbNoun>Input.java
public final class <VerbNoun>Input {
    private final String <field>;                       // 用基本型別
    public <VerbNoun>Input(String <field>) { this.<field> = <field>; }
    public String get<Field>() { return <field>; }
}

// FILE: <application>/usecase/<verbnoun>/<VerbNoun>Output.java
public final class <VerbNoun>Output {
    // final 欄位 + 建構子 + getter，同上
}

// FILE: <application>/usecase/<verbnoun>/<VerbNoun>.java
// 禁止 import：adapter、infrastructure、Spring、JPA、Servlet
public class <VerbNoun> {
    private final <Entity>Repository <repo>;
    private final Clock clock;                          // 需要時間才加
    private final UnitOfWork uow;                       // 改超過一個 aggregate 才加

    public <VerbNoun>(<Entity>Repository <repo>, Clock clock, UnitOfWork uow) {
        this.<repo> = <repo>;
        this.clock = clock;
        this.uow = uow;
    }

    public <VerbNoun>Output execute(<VerbNoun>Input input) {
        // 1. 轉換輸入
        <Entity>Id id = new <Entity>Id(input.getId());

        // 2. 載入
        final <Entity> entity = <repo>.findById(id);
        if (entity == null) {
            throw new AppException("<ENTITY>_NOT_FOUND");
        }

        // 3. 業務規則：只呼叫 entity 方法
        entity.<businessVerb>(<args>, clock.now());

        // 4. 儲存
        <repo>.save(entity);

        // 5. 副作用（若有，交易成功之後）

        // 6. 回傳 DTO
        return new <VerbNoun>Output(/* ... */);
    }
}
```

然後在 `<main>` 的 `@Configuration` 加一個 `@Bean`，並在 controller 呼叫它。

## 命名規則

| 項目 | 規則 | ✅ | ❌ |
|---|---|---|---|
| Use case 類別 | 動詞 + 名詞，使用者意圖 | `BorrowBook`、`RenewLoan` | `LoanService`、`UpdateLoan`、`LoanManager` |
| Package | 同 use case，全小寫 | `usecase.borrowbook` | `usecase.loan` |
| 方法 | 一個 use case 一個公開方法 | `execute` / `handle` | 一個類別多個公開方法 |

> `LoanService` 這種「一個類別裝所有操作」的寫法，會隨時間膨脹成上帝類別。一個 use case 一個類別。

## 查詢型 Use Case（Read）

不修改狀態、只讀資料時，可以跳過 entity，直接用 Query port 取得 read model：

```java
// FILE: <application>/port/LendingQueries.java
public interface LendingQueries {
    List<LoanSummary> listOpenLoansByMember(MemberId memberId);   // LoanSummary：read model DTO
}

// FILE: <application>/usecase/LendingQueryService.java
public class LendingQueryService {
    private final LendingQueries queries;
    public LendingQueryService(LendingQueries queries) { this.queries = queries; }

    public List<LoanSummary> listMemberLoans(String memberId) {
        return queries.listOpenLoansByMember(new MemberId(memberId));
    }
}
```

`SqlLendingQueries` 可以直接 join 多張表、回傳 DTO，不必經過 entity。

多個純查詢（沒有規則、只有一行委派）可以合併成**一個查詢類別、多個方法**；寫入型 use case 仍維持一個操作一個類別。

## 完成檢查

- [ ] 一個 package、一個 use case 類別、一個公開方法
- [ ] 沒有業務規則寫在 use case 裡；類別上沒有 `@Service`
- [ ] 有 use case 測試（成功 + 每種失敗；專案不寫測試時改為手動驗證）
- [ ] 已在 `<main>` 用 `@Bean` 組裝
- [ ] 已接到某個 driving adapter（否則它不會被執行）
