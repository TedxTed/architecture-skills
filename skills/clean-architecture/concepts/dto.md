# DTO（Data Transfer Object）

> 位置：分散在各層，**每種 DTO 屬於定義它的那一層**｜特性：純資料、沒有行為、不可變

## 職責

**在邊界上搬運資料**。讓每一層只看到自己認得的形狀：
use case 不知道 JSON 長什麼樣，controller 不知道 entity 內部有什麼，entity 不知道資料表有哪些欄位。

## 常見疑問：DTO 不是只屬於 controller 那一層嗎？

一般說的「DTO」常指 **API 收送的格式**（Request / Response），那些確實只屬於 controller 所在的 `<adapters>/http/`。
但 use case 的 **Input / Output** 必須放在 `<application>`，原因是依賴方向：

```
borrow_book.x 的方法簽章：execute(input: BorrowBookInput) -> BorrowBookOutput
→ use case 必須 import 這兩個型別
→ 若它們放在 <adapters>/http/，就變成 application import adapters ── 違反硬規則 1
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
         [adapters/http]    [application]        [domain]           [application]        [adapters/http]

                                                 Entity ⇄ LoanRow            [adapters/persistence]
                                                 LoanRow ──▶ LoanSummary     （查詢：不經過 entity）
```

## 種類與位置

| 種類 | 命名 | 定義在 | 形狀 | 誰產生 → 誰使用 |
|---|---|---|---|---|
| Request Model | `XxxRequest` | `<adapters>/http/` | 對應外部格式，可有驗證註解 | 框架解析 → controller |
| **Input DTO** | `XxxInput` | `<application>/use_cases/xxx/` | 基本型別 | controller → use case |
| **Output DTO** | `XxxOutput` | `<application>/use_cases/xxx/` | 基本型別、Date、列舉字串 | use case → controller |
| Response Model | `XxxResponse` | `<adapters>/http/` | 對應外部格式（ISO 日期字串等） | controller / presenter → 框架 |
| Read Model | `XxxSummary`、`XxxView` | `<application>/ports/`（查詢 port） | 為畫面需求扁平化 | query adapter → use case → controller |
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
| JSON → Request Model | 框架 / 解析器 | `<adapters>/http/` |
| Request Model → Input DTO | **controller** | `<adapters>/http/` |
| Input DTO → Value / Entity | **use case** 第 1 步 | `<application>/` |
| Entity → Output DTO | **use case** 第 6 步（可抽成 `XxxOutput.from(entity)` 靜態函式） | `<application>/` |
| Output DTO → Response Model | **controller / presenter** | `<adapters>/http/` |
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

## Pseudocode

```
// FILE: <application>/use_cases/borrow_book/borrow_book_dto.x
DTO BorrowBookInput
  memberId: String
  bookId:   String

DTO BorrowBookOutput
  loanId:  String
  dueDate: Date

  STATIC FUNCTION from(loan: Loan) -> BorrowBookOutput      // 純轉換，不是業務方法
    RETURN BorrowBookOutput(loanId: loan.id.value, dueDate: loan.dueDate)

// FILE: <application>/use_cases/borrow_book/borrow_book.x   （節錄）
FUNCTION execute(input: BorrowBookInput) -> BorrowBookOutput
  memberId ← TRY MemberId(input.memberId)                    // Input → Value
  ...
  RETURN BorrowBookOutput.from(loan)                         // Entity → Output

// FILE: <adapters>/http/loan_schemas.x
DTO BorrowRequest                                           // 可使用框架驗證註解
  memberId: String  @required @maxLength(64)
  bookId:   String  @required @maxLength(64)

DTO BorrowResponse
  loanId:  String
  dueDate: String                                           // ISO 8601："2026-10-17"

// FILE: <adapters>/http/loan_controller.x   （節錄）
FUNCTION handleBorrow(request)
  req ← TRY parseAndValidate(request.body, BorrowRequest)    // 失敗 → 400
  out ← borrowBook.execute(BorrowBookInput(req.memberId, req.bookId))
  RETURN HttpResponse(201, BorrowResponse(out.loanId, formatIsoDate(out.dueDate)))
```

查詢：Read Model 不經過 entity。

```
// FILE: <application>/ports/loan_queries.x
DTO LoanSummary
  loanId: String, bookTitle: String, dueDate: Date, isOverdue: Bool

PORT LoanQueries
  FUNCTION listByMember(memberId: MemberId, now: DateTime) -> List<LoanSummary>
```

### 局部更新（PATCH）：分辨「沒傳」與「傳了 null」

```
// FILE: <application>/use_cases/update_member_profile/update_member_profile_dto.x
DTO UpdateMemberProfileInput
  memberId: String
  nickname: Optional<String | Nothing>     // 沒傳 = 不改；傳 Nothing = 清空；傳字串 = 改成該值
  phone:    Optional<String | Nothing>

// use case 中
IF input.nickname.isPresent
  member.changeNickname(input.nickname.value)              // value 可能是 Nothing
```

各語言表示「沒傳」的方式：TS 用 `undefined` 與 `null` 區分；Python 用 sentinel（`UNSET = object()`）；
Go 用指標加旗標或 `Optional[T]` 自訂型別；Java 用 `Optional<Optional<T>>` 或自訂 `Patch<T>`。
在專案中選定一種後記入 `conventions.md`。

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
| 小專案 | DTO 與 use case 寫在同一檔 | 多個 use case 共用一個 DTO |

## 語言對照

| 語言 | Input / Output | Request / Response 驗證 |
|---|---|---|
| TypeScript | `type` / `interface`（`readonly` 欄位） | zod、class-validator（只用在 adapter） |
| Python | `@dataclass(frozen=True)` | Pydantic（只用在 adapter） |
| Go | 純 struct | struct tag `json:"..."` + validator（只用在 adapter 的 struct） |
| Java | `record` | Bean Validation `@NotNull` 等（只用在 adapter 的 record） |

## 測試

```
// FILE: <tests>/adapters/http/loan_controller_test.x
TEST "BorrowRequest 轉成 BorrowBookInput，Output 的日期轉成 ISO 字串"
  fake ← FakeBorrowBook(returns: BorrowBookOutput("loan-1", Date(2026,10,17)))
  res  ← HttpLoanController(fake).handleBorrow(request(body: { memberId: "m1", bookId: "b1" }))
  EXPECT fake.receivedInput == BorrowBookInput("m1", "b1")
  EXPECT res.body == { loanId: "loan-1", dueDate: "2026-10-17" }
```

## 自我檢查

- [ ] 每個 use case 有自己的 Input / Output，沒有萬用 DTO
- [ ] Input / Output 沒有框架註解、沒有 entity、沒有業務方法
- [ ] Use case 回傳 Output DTO，不是 entity
- [ ] Row / ORM model / External Payload 沒有離開 adapter 層
- [ ] PATCH 類輸入能分辨「沒傳」與「傳 null」
