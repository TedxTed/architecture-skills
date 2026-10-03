# Pseudocode 語法約定

本專案所有範例使用**語言中立的 pseudocode**。目的是讓 AI 與讀者專注在「結構與依賴方向」，
而不是某個語言或框架的語法。要轉成實際語言時，請查 `skills/<架構>/languages/` 對照表。

## 關鍵字

| 關鍵字 | 意義 | 轉成實際語言時 |
|---|---|---|
| `ENTITY Name` | 有身分（id）、有行為的業務物件 | class / struct + methods |
| `VALUE Name` | 沒有身分、不可變、以值相等的物件 | record / dataclass(frozen) / struct |
| `ENUM Name` | 列舉 | enum / union of literals / const |
| `PORT Name` | 由內層定義、外層實作的介面 | interface / Protocol / abstract class |
| `ADAPTER Name IMPLEMENTS Port` | Port 的具體實作 | class implements interface |
| `USE_CASE Name` | 一個應用操作（Interactor） | class with `execute()` 或 function |
| `DTO Name` | 跨邊界傳遞的純資料，沒有行為 | record / interface / dataclass |
| `DEPENDS ON a: A, b: B` | 建構時注入的依賴（建構子注入） | constructor parameters |
| `INPUT { ... }` / `OUTPUT { ... }` | Use case 的輸入 / 輸出 DTO | 兩個資料型別 |
| `FUNCTION name(args) -> Type` | 函式 / 方法 | function / method |
| `x ← expr` | 指派 | `=` / `:=` |
| `FAIL ErrorName` | 回報業務錯誤 | throw / return Err / return error（依語言慣例） |
| `x ← TRY expr` | 呼叫可能 FAIL 的東西，失敗就往上傳 | 自動傳遞 / `?` / `if err != nil { return }` |
| `ON ERROR e` | 接住上一行的錯誤並處理 | catch / `if err != nil` / match Err |
| `MATCH x` + `A, B → ...` | 依型別或值分派 | switch / match / if-else 鏈 |
| `DOMAIN_ERROR Name code "X"` | 領域錯誤（定義在 domain） | 自訂 exception / error 值 |
| `APP_ERROR Name code "X"` | 應用錯誤（定義在 application） | 同上 |
| `CONSTANT X = v` | 常數 | const / static final |
| `STATIC FUNCTION` | 工廠或類別層級方法 | static method / 套件層級函式 |
| `TEST "描述"` / `EXPECT ...` | 測試案例與斷言 | 依測試框架 |
| `EXPECT expr FAILS WITH E` | 斷言會回報錯誤 E | `expect(...).toThrow` / `pytest.raises` / `errors.Is` |
| `// ...` | 註解 | 註解 |
| `MUST NOT import ...` | 此檔案禁止的依賴（給 AI 的硬性約束） | 不產生程式碼，靠 lint / review 檢查 |

## 檔案標頭

每段 pseudocode 第一行寫出**它應該放在哪個檔案**：

```
// FILE: <domain>/loan.x
```

`.x` 代表「你的語言副檔名」。

`<domain>`、`<application>`、`<adapters>`、`<infrastructure>`、`<main>`、`<tests>` 是**層級佔位符**，
依專案選用的資料夾結構（by-layer / by-feature）替換成實際路徑。對照表見各架構 skill 的 `SKILL.md`。

## 範例

```
// FILE: <domain>/book.x
ENUM BookStatus { AVAILABLE, ON_LOAN }

ENTITY Book
  id:     BookId
  title:  String
  status: BookStatus

  FUNCTION markAsLent()
    IF status != AVAILABLE
      FAIL BookNotAvailable            // R4
    status ← ON_LOAN

  FUNCTION markAsReturned()
    status ← AVAILABLE

  MUST NOT import database, http, framework
```
