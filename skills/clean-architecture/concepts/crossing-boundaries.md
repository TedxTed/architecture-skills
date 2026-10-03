# 跨越邊界：資料、錯誤、交易

這三件事是實作時最常出錯的地方。範例為 Java，相容 JDK 1.7。

---

## 1. 資料怎麼跨層

DTO 的種類、位置、轉換責任、驗證、PATCH 處理，見 **[dto.md](dto.md)**。本節只補充 Entity 與儲存格式之間的轉換。

| 規則 | 原因 |
|---|---|
| Repository 進出的是 **Entity**，不是 DB row | Port 用內層語言 |
| ORM model（JPA `@Entity`）≠ Domain Entity，兩者之間用 mapper 轉 | Entity 不該被資料表結構綁架 |

### 要不要 Domain Entity 直接當 JPA Entity？

| 情境 | 建議 |
|---|---|
| 正式專案、業務規則多 | **分開**。Domain entity 在 domain；`LoanJpaEntity` 在 adapter/persistence；寫 mapper |
| 原型、CRUD 為主、規則很少 | 可以考慮不用 Clean Architecture（見 [structure/choosing.md](../structure/choosing.md)） |

Mapper 範例（JPA 版；CSV 版見 [03-interface-adapters.md](../layers/03-interface-adapters.md#範例程式碼)）：

```java
// FILE: <adapters>/persistence/sql/LoanJpaMapper.java
public final class LoanJpaMapper {
    public static Loan toEntity(LoanJpaEntity row) {
        return Loan.reconstitute(                       // 還原：不檢查規則
                new LoanId(row.getId()), new MemberId(row.getMemberId()), new BookId(row.getBookId()),
                row.getBorrowedAt(), row.getDueDate(),
                row.getReturnedAt(),                      // 資料庫 NULL ⇄ Java null
                row.getRenewCount());
    }

    public static LoanJpaEntity toRow(Loan loan) {
        LoanJpaEntity row = new LoanJpaEntity();
        row.setId(loan.getId().getValue());
        row.setMemberId(loan.getMemberId().getValue());
        // 其餘欄位略
        return row;
    }
}
```

> Entity 欄位是 private 時，提供 `Loan.reconstitute(...)`（不檢查規則、僅用於還原）給 mapper 用，
> 與用於「新建」的 `Loan.open(...)` 區分。

---

## 2. 錯誤怎麼跨層

**錯誤分三類，各在不同層定義：**

| 類別 | 定義在 | 範例 |
|---|---|---|
| 領域錯誤 | `<domain>`，繼承 `DomainException` | `BookNotAvailableException`、`LoanLimitExceededException` |
| 應用錯誤 | `<application>`，`AppException` | `MEMBER_NOT_FOUND`、`BOOK_NOT_FOUND` |
| 技術錯誤 | 不定義，由 adapter 包裝或往上丟 | `SQLException`、`IOException`、逾時 |

都用 **unchecked**（繼承 `RuntimeException`），避免 use case 的方法簽章被 `throws` 汙染。

**翻譯只在最外層的 adapter 做一次**：預設在 `ErrorMapping`（普通 Java 類別）裡對應，完整範例見 [03-interface-adapters.md](../layers/03-interface-adapters.md#範例程式碼)；用 Spring 的專案也可改用 `@ControllerAdvice`。

| ❌ 錯誤做法 | 原因 |
|---|---|
| Entity 丟 `ResponseStatusException(409)` 或 `HttpServerErrorException` | domain 知道了 HTTP |
| Repository 宣告 `throws SQLException`，讓 controller 處理 | 技術錯誤滲進內層的方法簽章 |
| Use case 回傳 `ResponseEntity` 或狀態碼 | 應用層知道了 HTTP |

Repository 遇到技術錯誤時，包成 unchecked 例外往上丟（例如 `new DataAccessFailure(e)`），由最外層的 adapter（框架綁定）統一記 log、回 500。
遇到有業務意義的錯誤（例如 unique key 重複），翻譯成領域 / 應用錯誤。

---

## 3. 交易（Transaction）怎麼處理

問題：借書要同時改 `Book.status` 和新增 `Loan`，必須在同一個交易。但 use case 不能 import JDBC 或任何框架。

**解法：`UnitOfWork` port**

```java
// FILE: <application>/port/UnitOfWork.java
public interface UnitOfWork {
    void run(Runnable work);          // work 丟出例外時全部 rollback
}

// FILE: <application>/usecase/borrowbook/BorrowBook.java   （片段）
uow.run(new Runnable() {
    @Override
    public void run() {
        books.save(book);
        loans.save(loan);
    }
});

// FILE: <adapters>/persistence/sql/JdbcUnitOfWork.java   （純 JDBC，不需任何框架）
public class JdbcUnitOfWork implements UnitOfWork {
    // 同一個執行緒共用同一個連線，repository 透過 currentConnection() 取得
    private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<Connection>();
    private final DataSource dataSource;

    public JdbcUnitOfWork(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public void run(Runnable work) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            CURRENT.set(conn);
            work.run();
            conn.commit();
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new DataAccessFailure(e);           // 包成 unchecked，不讓 SQLException 往內洩漏
        } catch (RuntimeException e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            CURRENT.remove();
            closeQuietly(conn);
        }
    }

    public static Connection currentConnection() { return CURRENT.get(); }

    // rollbackQuietly、closeQuietly 省略
}
```

使用 Spring 的專案：改用 `TransactionTemplate` 實作同一個 `UnitOfWork`，見 [languages/java.md 的「使用 Spring 時」](../languages/java.md#使用-spring-時)。

**副作用（寄信）放在交易之外、成功之後**，避免「信寄了但交易 rollback」。
若需要保證一致性，用 Outbox pattern（見 [domain-events.md](domain-events.md#可靠性outbox)）。

### 替代方案

| 方案 | 適用 |
|---|---|
| UnitOfWork port（上面） | 預設推薦 |
| Use case decorator：`TransactionalBorrowBook` 放在 adapter，包住整個 use case | Use case 一律是一個交易時，最乾淨 |
| `@Transactional` | **只能**加在 adapter 或 decorator 上，不能加在 use case 上 |

### 讀取在交易外的競態

`BorrowBook` 先讀 `book`、檢查可借，再進 `uow.run` 寫入。讀與寫之間若有另一個請求也借了同一本書，**兩個請求都會通過檢查**：

```
請求 A：讀 book（AVAILABLE）── 檢查通過 ─────────────── 寫入 ON_LOAN
請求 B：        讀 book（AVAILABLE）── 檢查通過 ── 寫入 ON_LOAN   ← 同一本書借出兩次
```

| 解法 | 做在哪 | 說明 |
|---|---|---|
| **條件式更新**（推薦） | SQL adapter | `UPDATE books SET status='ON_LOAN' WHERE id=? AND status='AVAILABLE'`；影響 0 列 → 丟 `BookNotAvailableException` |
| **樂觀鎖** | entity 加 `version`，adapter 比對（JPA 用 `@Version`，放在 `LoanJpaEntity` 上） | 版本不符 → 回報 `CONCURRENT_MODIFICATION`，由使用者重試 |
| 悲觀鎖 | SQL adapter | `SELECT ... FOR UPDATE`，讀取也要放進交易（UnitOfWork 包住整個 use case） |
| 序列化整個 use case | decorator / CSV | 同一時間只執行一個寫入型 use case（`synchronized`）；簡單，但只適合單一程序、低流量 |

這些都是 **adapter 的細節**，use case 與 entity 不需要知道。選了哪一種，記入專案的 `decisions.md`。
