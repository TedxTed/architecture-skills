# DTO（Data Transfer Object）

> 位置：分散在各層，**每種 DTO 屬於定義它的那一層**｜特性：純資料、沒有行為、不可變

## 職責

**在邊界上搬運資料**。讓每一層只看到自己認得的形狀：
use case 不知道 JSON 長什麼樣，controller 不知道 entity 內部有什麼，entity 不知道資料表有哪些欄位。

## 常見疑問：DTO 不是只屬於 controller 那一層嗎？

一般說的「DTO」常指 **API 收送的格式**（Request / Response），那些確實只屬於 controller 所在的 `<adapters>/web/`。
但 use case 的 **Input / Output** 必須放在 `<application>`，原因是依賴方向：

```java
// BorrowBook.java 的方法簽章
public BorrowBookOutput execute(BorrowBookInput input)
// → use case 必須 import 這兩個型別
// → 若它們放在 adapter.web 套件，就變成 application import adapter ── 違反硬規則 1
```

所以「誰使用這個型別的方法簽章，型別就屬於誰」：use case 的參數屬於 application，API 的 JSON 形狀屬於 adapter。
Robert C. Martin 原書把前者叫 Request / Response Model，與 HTTP 無關，本 skill 改稱 Input / Output 以免混淆。

| 想只維護一套 DTO？ | 結果 |
|---|---|
| 只留 application 的 Input / Output，controller 直接用 | ✅ 可以（見下方「務實取捨」） |
| 只留 adapter 的 Request / Response，use case 去 import | ❌ 違反依賴方向 |

## 一條請求中的所有 DTO

```
 JSON ──▶ BorrowRequest ──▶ BorrowBookInput ──▶ (Value / Entity) ──▶ BorrowBookOutput ──▶ BorrowResponse ──▶ JSON
         [adapter/web]      [application]        [domain]           [application]        [adapter/web]

                                                 Entity ⇄ LoanRow            [adapter/persistence]
                                                 LoanRow ──▶ LoanSummary     （查詢：不經過 entity）
```

## 種類與位置

| 種類 | 命名 | 定義在 | 形狀 | 誰產生 → 誰使用 |
|---|---|---|---|---|
| Request Model | `XxxRequest` | `<adapters>/web/` | 對應外部格式，可有驗證註解 | 框架解析 → controller |
| **Input DTO** | `XxxInput` | `<application>/usecase/xxx/` | 基本型別 | controller → use case |
| **Output DTO** | `XxxOutput` | `<application>/usecase/xxx/` | 基本型別、Date、列舉字串 | use case → controller |
| Response Model | `XxxResponse` | `<adapters>/web/` | 對應外部格式（ISO 日期字串等） | controller / presenter → 框架 |
| Read Model | `XxxSummary`、`XxxView` | `<application>/port/`（查詢 port） | 為畫面需求扁平化 | query adapter → use case → controller |
| Row / Document | `XxxRow` | `<adapters>/persistence/` | 對應儲存格式（CSV 欄、DB 欄） | mapper ⇄ entity |
| Public API DTO | `XxxDto` | `modules/<m>/public_api`（by-feature） | 模組對外契約 | 模組 A → 模組 B 的 integration adapter |
| External Payload | `XxxPayload` | `<adapters>/<gateway>/` | 第三方 API 的格式 | gateway 內部使用，不往內傳 |

**最重要的兩種是 Input / Output DTO**：它們是 use case 的契約，定義在 application 層。其他種類都屬於 adapter 的細節。

## 遵循規則

| 必須 | 禁止 |
|---|---|
| 只有欄位，沒有業務方法 | DTO 上有 `canBorrow()`、`calculateFine()` |
| 建立後不可變 | 傳遞途中被修改 |
| Input / Output 只用基本型別、日期、列舉字串 | Input / Output 裡包著 entity 或 ORM model |
| **每個 use case 有自己的** Input / Output | 一個 `LoanDto` 給建立、更新、查詢、回應共用 |
| application 層的 DTO 不帶框架註解 | Input DTO 上有 `@IsString()`、`Field(...)`、`@JsonProperty` |
| Use case 回傳 Output DTO | 回傳 entity 讓 controller 直接序列化 |
| 轉換在**邊界**做，且每個邊界只轉一次 | 同一筆資料在同一層內轉來轉去 |

## 誰負責轉換

| 轉換 | 誰做 | 在哪 |
|---|---|---|
| JSON → Request Model | 框架 / 解析器 | `<adapters>/web/` |
| Request Model → Input DTO | **controller** | `<adapters>/web/` |
| Input DTO → Value / Entity | **use case** 第 1 步 | `<application>/` |
| Entity → Output DTO | **use case** 第 6 步（可抽成 `XxxOutput.from(entity)` 靜態函式） | `<application>/` |
| Output DTO → Response Model | **controller / presenter** | `<adapters>/web/` |
| Entity ⇄ Row | **mapper** | `<adapters>/persistence/` |
| Row → Read Model | **query adapter** | `<adapters>/persistence/` |
| External Payload → 內部型別 | **gateway** | `<adapters>/<gateway>/` |

## 各種 DTO 的驗證

| DTO | 驗證什麼 | 失敗時 |
|---|---|---|
| Request Model | 傳輸格式：欄位存在、型別、長度 | controller 回 400 |
| Input DTO | 不驗證（由下一步的 Value 驗證） | — |
| Value（`MemberId(str)`、`Email(str)`） | 值的合法性 | 領域錯誤 → 400 / 422 |
| Entity 方法 | 業務規則 | 領域錯誤 → 409 / 422 |

## 怎麼寫（步驟）

1. 先定義 use case 的 **Input / Output**：只放這個操作需要的欄位
2. 寫 use case，第 1 步把 Input 轉成 Value，第 6 步把 entity 轉成 Output
3. 在 controller 定義 Request / Response（使用框架驗證工具時），寫兩個轉換
4. Repository 的 Row 由 mapper 處理，與上面的 DTO 無關
5. 測試轉換：Request → Input、Output → Response、Entity → Row → Entity

## 範例程式碼

Java，相容 JDK 1.7：DTO 是 `final class` + `final` 欄位 + getter，沒有 setter、沒有業務方法。

```java
// FILE: <application>/usecase/borrowbook/BorrowBookInput.java
public final class BorrowBookInput {
    private final String memberId;
    private final String bookId;
    public BorrowBookInput(String memberId, String bookId) { this.memberId = memberId; this.bookId = bookId; }
    public String getMemberId() { return memberId; }
    public String getBookId() { return bookId; }
}

// FILE: <application>/usecase/borrowbook/BorrowBookOutput.java
public final class BorrowBookOutput {
    private final String loanId;
    private final LocalDate dueDate;

    public BorrowBookOutput(String loanId, LocalDate dueDate) { this.loanId = loanId; this.dueDate = dueDate; }

    public static BorrowBookOutput from(Loan loan) {              // 純轉換，不是業務方法
        return new BorrowBookOutput(loan.getId().getValue(), loan.getDueDate());
    }

    public String getLoanId() { return loanId; }
    public LocalDate getDueDate() { return dueDate; }
}

// FILE: <application>/usecase/borrowbook/BorrowBook.java   （節錄）
public BorrowBookOutput execute(BorrowBookInput input) {
    MemberId memberId = new MemberId(input.getMemberId());      // Input → Value
    // ...
    return BorrowBookOutput.from(loan);                         // Entity → Output
}

// FILE: <adapters>/web/BorrowRequest.java        （Jackson 反序列化需要無參建構子與 setter）
public class BorrowRequest {
    @NotNull @Size(max = 64) private String memberId;          // Bean Validation 註解只能出現在 adapter
    @NotNull @Size(max = 64) private String bookId;
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }
}

// FILE: <adapters>/web/BorrowResponse.java
public class BorrowResponse {
    private final String loanId;
    private final String dueDate;                               // ISO 8601 字串："2026-10-17"
    public BorrowResponse(String loanId, String dueDate) { this.loanId = loanId; this.dueDate = dueDate; }
    public String getLoanId() { return loanId; }
    public String getDueDate() { return dueDate; }
}

// FILE: <adapters>/web/LoanController.java   （節錄）
@RequestMapping(method = RequestMethod.POST)
public ResponseEntity<BorrowResponse> borrow(@Valid @RequestBody BorrowRequest req) {   // 驗證失敗 → 400
    BorrowBookOutput out = borrowBook.execute(new BorrowBookInput(req.getMemberId(), req.getBookId()));
    return new ResponseEntity<BorrowResponse>(
            new BorrowResponse(out.getLoanId(), out.getDueDate().toString()), HttpStatus.CREATED);
}
```

查詢：Read Model 不經過 entity。

```java
// FILE: <application>/port/LoanSummary.java
public final class LoanSummary {
    private final String loanId;
    private final String bookTitle;
    private final LocalDate dueDate;
    public LoanSummary(String loanId, String bookTitle, LocalDate dueDate) {
        this.loanId = loanId; this.bookTitle = bookTitle; this.dueDate = dueDate;
    }
    public String getLoanId() { return loanId; }
    public String getBookTitle() { return bookTitle; }
    public LocalDate getDueDate() { return dueDate; }
}

// FILE: <application>/port/LendingQueries.java
public interface LendingQueries {
    List<LoanSummary> listOpenLoansByMember(MemberId memberId);
}
```

### 局部更新（PATCH）：分辨「沒傳」與「傳了 null」

Java 7 沒有 `Optional`，用一個小型的 `Patch<T>` 表示「有沒有傳」：

```java
// FILE: <application>/Patch.java
public final class Patch<T> {
    private static final Patch<?> ABSENT = new Patch<Object>(false, null);
    private final boolean present;
    private final T value;                                      // present 時可以是 null（代表清空）

    private Patch(boolean present, T value) { this.present = present; this.value = value; }

    @SuppressWarnings("unchecked")
    public static <T> Patch<T> absent() { return (Patch<T>) ABSENT; }   // 沒傳 → 不改
    public static <T> Patch<T> of(T value) { return new Patch<T>(true, value); }   // 傳了（含 null）

    public boolean isPresent() { return present; }
    public T getValue() { return value; }
}

// FILE: <application>/usecase/updateprofile/UpdateMemberProfileInput.java
public final class UpdateMemberProfileInput {
    private final String memberId;
    private final Patch<String> nickname;
    private final Patch<String> phone;
    // 建構子與 getter 略
}

// use case 中
if (input.getNickname().isPresent()) {
    member.changeNickname(input.getNickname().getValue());      // 值可能是 null（清空）
}
```

Controller 判斷 JSON 裡**有沒有這個欄位**（例如用 Jackson 的 `JsonNode.has("nickname")`），再決定 `Patch.absent()` 或 `Patch.of(...)`。
選定做法後記入 `conventions.md`。

## 與各層的配合

| 層 | 定義哪些 DTO | 使用哪些 DTO | 不可以碰 |
|---|---|---|---|
| Entities | 無 | 無（只認 Value 與 entity） | 任何 DTO |
| Use Cases | Input、Output、Read Model、Port 用到的型別 | 自己定義的 | Request / Response / Row |
| Interface Adapters | Request、Response、Row、External Payload | 自己定義的 + use case 的 Input / Output | 把 Row 或 Payload 往內傳 |
| Frameworks & Drivers | 無 | 無 | — |

**判斷法**：某個 DTO 被哪一層「定義」，它的欄位設計就跟著那一層的需求走。
Input / Output 跟著 use case 的需求，Response 跟著 API 規格，Row 跟著儲存結構。三者可以長得不一樣，這正是重點。

## 務實取捨

| 情況 | 可以 | 仍然不可以 |
|---|---|---|
| Request 與 Input 欄位完全相同、且不用框架驗證工具 | 省略 Request，controller 手動檢查後直接建 Input | 讓 Input 帶框架註解 |
| Output 與 Response 只差日期格式 | 省略 Response 型別，controller 直接組回應物件 | 回傳 entity |
| 小專案 | DTO 寫成 use case 的巢狀類別（`BorrowBook.Input`、`BorrowBook.Output`，`public static final class`） | 多個 use case 共用一個 DTO |

## 語言對照

| 語言 | Input / Output | Request / Response 驗證 |
|---|---|---|
| TypeScript | `type` / `interface`（`readonly` 欄位） | zod、class-validator（只用在 adapter） |
| Python | `@dataclass(frozen=True)` | Pydantic（只用在 adapter） |
| Go | 純 struct | struct tag `json:"..."` + validator（只用在 adapter 的 struct） |
| Java 7 / 8 | `final class` + `final` 欄位 + getter（Java 16+ 可用 `record`） | Bean Validation `@NotNull` 等（只用在 adapter 的 Request 類別） |

## 測試

```java
// FILE: <tests>/adapters/web/LoanControllerTest.java     （JUnit 4 + Mockito）
public class LoanControllerTest {
    @Test
    public void BorrowRequest轉成BorrowBookInput_日期轉成ISO字串() {
        BorrowBook borrowBook = mock(BorrowBook.class);
        when(borrowBook.execute(any(BorrowBookInput.class)))
                .thenReturn(new BorrowBookOutput("loan-1", LocalDate.of(2026, 10, 17)));

        BorrowRequest req = new BorrowRequest();
        req.setMemberId("m1");
        req.setBookId("b1");
        ResponseEntity<BorrowResponse> res = new LoanController(borrowBook).borrow(req);

        ArgumentCaptor<BorrowBookInput> captor = ArgumentCaptor.forClass(BorrowBookInput.class);
        verify(borrowBook).execute(captor.capture());
        assertEquals("m1", captor.getValue().getMemberId());
        assertEquals("2026-10-17", res.getBody().getDueDate());
    }
}
```

## 自我檢查

- [ ] 每個 use case 有自己的 Input / Output，沒有萬用 DTO
- [ ] Input / Output 沒有框架註解、沒有 entity、沒有業務方法
- [ ] Use case 回傳 Output DTO，不是 entity
- [ ] Row / ORM model / External Payload 沒有離開 adapter 層
- [ ] PATCH 類輸入能分辨「沒傳」與「傳 null」
