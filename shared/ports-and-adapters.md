# Ports & Adapters（埠與轉接器）

> Clean Architecture 的 "Interface Adapters" 層、Hexagonal 的 Ports/Adapters、Onion 的 Infrastructure，
> 講的都是同一件事。這份文件是共用定義。

## 兩種 Port

| | 輸入 Port（Driving / Primary） | 輸出 Port（Driven / Secondary） |
|---|---|---|
| 誰呼叫誰 | 外面呼叫應用程式 | 應用程式呼叫外面 |
| 誰定義 | application 層 | application 層 |
| 誰實作 | **use case 本身** | **adapter** |
| 誰使用 | adapter（controller、CLI） | use case |
| 範例 | `BorrowBookUseCase` 介面 | `LoanRepository`、`Notifier`、`Clock` |

```
 [HTTP Controller] ──▶ «input port» BorrowBook ◀── BorrowBookInteractor ──▶ «output port» LoanRepository ◀── [SqlLoanRepository]
   driving adapter                                    use case                                             driven adapter
```

> 實務簡化：許多專案**省略輸入 port 介面**，讓 controller 直接依賴 use case 類別。
> 這不違反依賴規則（controller 在外、use case 在內）。只有在需要替換 use case 實作（例如 decorator、測試替身）時才值得加介面。

## Port 設計原則

1. **用業務語言命名，不用技術語言**
   - ✅ `LoanRepository.findOpenLoansByMember(memberId)`
   - ❌ `LoanDao.executeQuery(sql)`、`LoanRepository.findBy(where: object)`
2. **由使用者（use case）的需求決定介面形狀**，不是由資料庫能力決定
3. **參數與回傳只用內層型別**（entity、value、DTO、基本型別）
4. **一個 port 一個職責**：`Notifier` 不要同時負責存資料
5. **時間、亂數、UUID 也是 port**：`Clock.now()`、`IdGenerator.next()` —— 這樣測試才能控制
6. **Port 通常放 application**；只有當抽象本身就是業務概念、且 domain service 需要它時，才放 domain（例：`ExchangeRateProvider` 被匯率計算的 domain service 使用）
7. **adapter 需要的資料由 use case 傳入**：adapter 不注入別的 port 自己查資料（見 anti-patterns #11）

## Adapter 的職責（只有三件事）

1. **轉換**：外部格式 ⇄ 內層型別（JSON ⇄ DTO、DB row ⇄ Entity）
2. **委派**：呼叫 use case 或外部系統
3. **翻譯錯誤**：外部錯誤 → 內層錯誤（DB unique violation → `DuplicateLoan`）；內層錯誤 → 外部格式（`BookNotAvailable` → HTTP 409）

**Adapter 不做業務判斷。** 如果你在 controller 或 repository 裡寫 `if member.tier == VIP`，那段程式放錯地方了。

## 常見 Port 清單（圖書範例）

```java
// FILE: <application>/port/LoanRepository.java
public interface LoanRepository {
    Loan findById(LoanId id);                       // 找不到回傳 null
    List<Loan> findOpenByMember(MemberId memberId);
    void save(Loan loan);
    LoanId nextId();
}

// FILE: <application>/port/Clock.java
public interface Clock {
    LocalDateTime now();
}

// FILE: <application>/port/Notifier.java
// 需要的資料由 use case 備齊傳入，adapter 不自己查
public interface Notifier {
    void notifyBookBorrowed(Recipient to, String bookTitle, LocalDate dueDate);
}

// FILE: <application>/port/Recipient.java
public final class Recipient {
    private final String email;
    private final String name;
    public Recipient(String email, String name) { this.email = email; this.name = name; }
    public String getEmail() { return email; }
    public String getName() { return name; }
}

// FILE: <application>/port/UnitOfWork.java
// work 中的寫入在同一交易完成；work 丟出例外時全部還原
public interface UnitOfWork {
    void run(Runnable work);
}
```
