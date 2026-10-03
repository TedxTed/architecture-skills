# 結構 A：依層分（by-layer）

適用：單一業務子領域、小到中型專案。以 Maven / Gradle 的 Java 專案為例，base package 為 `com.example.library`。

## 目錄樹

```
project/
├── pom.xml
├── src/main/java/com/example/library/
│   ├── domain/                              # ── Entities 層 ──
│   │   ├── Member.java, Book.java, Loan.java          # Entity
│   │   ├── MemberId.java, BookId.java, LoanId.java    # Value
│   │   ├── MemberTier.java, BookStatus.java           # enum
│   │   ├── DomainException.java + 各領域錯誤           # BookNotAvailableException …
│   │   └── service/                                   # （選用）跨 entity 的純規則
│   │
│   ├── application/                         # ── Use Cases 層 ──
│   │   ├── AppException.java                          # 應用錯誤（NOT_FOUND 類）
│   │   ├── port/                                      # 輸出 port 介面（只有介面與它用到的 DTO）
│   │   │   ├── MemberRepository.java, BookRepository.java, LoanRepository.java
│   │   │   ├── LendingQueries.java, LoanSummary.java  # 查詢用 read model port
│   │   │   ├── Clock.java, UnitOfWork.java
│   │   │   └── Notifier.java, Recipient.java
│   │   └── usecase/
│   │       ├── borrowbook/
│   │       │   ├── BorrowBook.java                    # Interactor
│   │       │   ├── BorrowBookInput.java
│   │       │   └── BorrowBookOutput.java
│   │       ├── returnbook/
│   │       ├── renewloan/
│   │       └── LendingQueryService.java               # 純查詢合併
│   │
│   ├── adapter/                             # ── Interface Adapters 層 ──
│   │   ├── web/                                       # driving
│   │   │   ├── LoanWebAdapter.java                # 轉換 + 呼叫 use case（不依賴框架）
│   │   │   ├── LoanServlet.java                   # 框架綁定（或 Spring 的 LoanController）
│   │   │   ├── ErrorMapping.java                      # 錯誤碼 → HTTP 狀態碼
│   │   │   └── BorrowRequest.java, BorrowResponse.java
│   │   ├── cli/                                       # driving
│   │   │   └── AdminCli.java
│   │   ├── persistence/                               # driven
│   │   │   ├── LoanMapper.java                        # entity ⇄ row（CSV 與 SQL 共用欄位名）
│   │   │   ├── csv/                                   # ① 新功能先做這個（見 concepts/csv-first.md）
│   │   │   │   ├── CsvStore.java
│   │   │   │   ├── CsvLoanRepository.java
│   │   │   │   └── CsvUnitOfWork.java
│   │   │   └── sql/                                   # ② 使用者確認後才做
│   │   │       ├── SqlLoanRepository.java
│   │   │       ├── SqlLendingQueries.java
│   │   │       └── JdbcUnitOfWork.java
│   │   ├── notification/SmtpNotifier.java             # driven
│   │   └── time/SystemClock.java
│   │
│   └── config/                              # ── Frameworks & Drivers 層：Composition root ──
│       ├── LendingModule.java                         # 組裝類別：依 storage 設定建立 CSV 或 SQL 版
│       └── Main.java / AppContextListener.java        # 啟動（Spring 專案改為 @Configuration）
│
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/                        # DB schema migration（Flyway 等）
├── data-seed/                               # CSV 範例資料
└── src/test/java/com/example/library/
    ├── domain/                              # 純單元測試
    ├── application/                         # use case 測試
    ├── fakes/                               # in-memory port 實作
    └── contract/                            # repository contract test
```

## 每個 package 的 import 白名單

| Package | 可 import |
|---|---|
| `domain` | `domain`、JDK（`java.util`、`java.time` / `org.threeten.bp`） |
| `application` | `domain`、`application`、JDK |
| `adapter` | `domain`、`application`、外部函式庫（Servlet、JDBC、Commons CSV…） |
| `config` | 全部 |
| `test.fakes` | `domain`、`application` |

強制方式見 [languages/java.md](../languages/java.md#強制依賴規則)（Maven 多模組、ArchUnit、或零依賴的檢查程式）。

## 新增功能時要動的檔案（以「續借」為例）

```
~ domain/Loan.java                                修改：加 renew()
+ domain/RenewLimitExceededException.java         新增
+ application/usecase/renewloan/                  新增：RenewLoan、RenewLoanInput、RenewLoanOutput
~ adapter/web/LoanController.java                 修改：加 renew()
~ adapter/web/ErrorMapping.java                   修改：加錯誤對應
~ config/LendingModule.java                       修改：建立 RenewLoan
~ test/.../domain/LoanTest.java                   修改
+ test/.../application/RenewLoanTest.java         新增
```

缺點也在這裡可見：一個功能散落在 4 個頂層 package。功能變多時改用 [by-feature](by-feature.md)。
