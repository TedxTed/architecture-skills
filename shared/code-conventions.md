# 範例程式碼約定（Java，相容 JDK 1.7 / 1.8）

本專案所有範例使用 **Java**，並刻意只用 **JDK 1.7 可編譯**的語法，讓仍在使用 Java 7 / 8 的專案可以直接照抄。
其他語言的寫法見 `skills/<架構>/languages/`。

## 語法限制

| 不使用（Java 8+ 或更新） | 改用 |
|---|---|
| lambda、method reference、stream | 匿名類別、for 迴圈 |
| `Optional` | 找不到時回傳 `null`，呼叫端檢查 |
| `record`、`var`、switch 表達式、text block | 一般 class + `final` 欄位 + 建構子 + getter |
| `java.time`（Java 8 才有） | 寫法照 `java.time`；**Java 7 改 import ThreeTen Backport**（`org.threeten.bp.*`，API 相同） |
| JUnit 5 | JUnit 4（`@Test`、`assertEquals`） |
| Lombok | 不假設有，範例手寫建構子與 getter |

> Java 8 專案可以把匿名類別換成 lambda、把 `null` 換成 `Optional`。選定後記入專案的 `conventions.md`。

## 檔案標頭

每段範例第一行寫出**它應該放在哪個檔案**，路徑用層級佔位符：

```java
// FILE: <domain>/Loan.java
```

`<domain>`、`<application>`、`<adapters>`、`<infrastructure>`、`<main>`、`<tests>` 是**層級佔位符**，
實際路徑（例如 `src/main/java/com/example/library/lending/domain`）記在使用者專案的 `docs/architecture/card.md`。

範例中省略 `package` 與 `import` 宣告，只在需要強調依賴時寫出來。

## 對應關係

| 概念 | Java 寫法 |
|---|---|
| Entity | `class`，`private` 欄位，以業務方法改變狀態 |
| Value | `final class`，`final` 欄位，建構子檢查，覆寫 `equals` / `hashCode` |
| Port | `interface` |
| Adapter | `class Xxx implements Port` |
| Use case | `class` + `execute(XxxInput)`，建構子注入 port |
| DTO | `final class`，`final` 欄位 + getter，沒有業務方法 |
| 領域錯誤 | `class Xxx extends DomainException`（`RuntimeException`，帶錯誤碼） |
| 禁止的依賴 | 註解 `// 禁止 import：...`，由 review 或依賴檢查工具把關 |

## 範例

```java
// FILE: <domain>/Book.java
// 禁止 import：application、adapter、infrastructure、任何框架
public class Book {
    private final BookId id;
    private final String title;
    private BookStatus status;

    public Book(BookId id, String title, BookStatus status) {
        this.id = id;
        this.title = title;
        this.status = status;
    }

    public void markAsLent() {
        if (status != BookStatus.AVAILABLE) {
            throw new BookNotAvailableException();   // R4
        }
        status = BookStatus.ON_LOAN;
    }

    public BookId getId() { return id; }
    public String getTitle() { return title; }
    public BookStatus getStatus() { return status; }
}
```
