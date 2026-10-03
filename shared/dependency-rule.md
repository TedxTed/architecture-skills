# 依賴規則（The Dependency Rule）

> 這是 Clean / Hexagonal / Onion 三種架構的**共同核心**。三者只是畫法與命名不同。

## 一句話

**原始碼的依賴（import / using / require）只能由外往內指。內層永遠不知道外層的存在。**

```
   ┌───────────────────────────────────────────┐
   │ Frameworks & Drivers（DB、Web 框架、SDK）   │
   │  ┌─────────────────────────────────────┐  │
   │  │ Interface Adapters（Controller、Repo） │  │
   │  │  ┌───────────────────────────────┐  │  │
   │  │  │ Use Cases（應用流程）           │  │  │
   │  │  │  ┌─────────────────────────┐  │  │  │
   │  │  │  │ Entities（業務規則）      │  │  │  │
   │  │  │  └─────────────────────────┘  │  │  │
   │  │  └───────────────────────────────┘  │  │
   │  └─────────────────────────────────────┘  │
   └───────────────────────────────────────────┘
              依賴方向：外 ──▶ 內
```

## 用 import 判斷（最實用的檢查法）

| 檔案位於 | 可以 import | 絕對不能 import |
|---|---|---|
| domain / entities | 只有 domain 自己、語言標準庫的基本型別 | application、adapters、infrastructure、任何框架 |
| application / use cases | domain、application 自己 | adapters、infrastructure、框架、ORM、HTTP |
| adapters | application、domain | 另一個 adapter 的內部細節（應該透過 port） |
| infrastructure / main | 全部 | — |

## 那內層要「呼叫」外層怎麼辦？—— 依賴反轉

Use case 需要存資料到 DB，但不能 import DB。解法：

1. **內層定義介面（Port）**：「我需要一個能 `save(loan)` 的東西」
2. **外層實作介面（Adapter）**：`SqlLoanRepository implements LoanRepository`
3. **組裝時注入（Composition Root）**：`main` 把 adapter 塞進 use case

```
控制流（執行時）：  UseCase ──呼叫──▶ SqlLoanRepository ──▶ DB
原始碼依賴：       UseCase ──▶ LoanRepository(port) ◀── SqlLoanRepository
                                    ▲
                         介面在內層，所以箭頭都指向內
```

控制流可以往外，**原始碼依賴**不行。這是整個架構唯一的魔法。

## 跨越邊界的資料

穿越邊界的資料必須是**內層認得的形狀**：
- 不能把 ORM model、HTTP Request 物件傳進 use case
- 要嘛傳基本型別 / DTO，要嘛傳 domain entity（由內往外）
- 轉換（mapping）一律在 **adapter** 做

## 判斷是否違反的速查

- 某個內層檔案出現 `import express / django / spring / gorm / prisma ...` → 違反
- 內層型別的欄位上有 `@Column`、`@JsonProperty` 這類框架註解 → 違反
- Use case 的參數型別是 `HttpRequest` / `ctx` / `req` → 違反
- 刪掉整個 adapters 資料夾後，domain + application 仍能編譯 → **正確**
