# Java 補充（JDK 1.7 / 1.8 / 新版）

本 skill 的範例**本身就是 Java，且相容 JDK 1.7**（約定見 [code-conventions.md](../../../shared/code-conventions.md)）。
本文件只補充：依 JDK 版本可以放寬的寫法、Spring 整合細節、怎麼強制依賴規則。

## 依 JDK 版本調整

| 項目 | JDK 1.7（範例預設） | JDK 1.8 | JDK 17+ |
|---|---|---|---|
| 日期時間 | ThreeTen Backport：`org.threeten.bp.LocalDate`（API 與 java.time 相同） | `java.time` | `java.time` |
| 找不到資料 | 回傳 `null` | 可改 `Optional<T>` | `Optional<T>` |
| 回呼（UnitOfWork 等） | 匿名類別，捕獲的變數要宣告 `final` | lambda | lambda |
| 集合處理 | for 迴圈 | 可用 stream | stream |
| DTO / Value | `final class` + `final` 欄位 + getter | 同左 | `record` |
| 測試 | JUnit 4、Mockito 1.x / 2.x | JUnit 4 / 5 | JUnit 5 |
| 依賴檢查工具 | Maven 多模組、[CheckArch.java](../templates/scripts/CheckArch.java) | + ArchUnit | + ArchUnit、Spring Modulith |

切換方式：在專案 `conventions.md` 記一條 `[其他]`，例如「本專案 JDK 1.8：找不到回傳 `Optional`、回呼用 lambda」。
**架構規則不因版本改變**，改變的只有語法。

ThreeTen Backport 的 Maven 依賴（Java 7）：

```xml
<dependency>
    <groupId>org.threeten</groupId>
    <artifactId>threetenbp</artifactId>
    <version>1.3.8</version>   <!-- 請選用支援 Java 7 的版本 -->
</dependency>
```

升級到 Java 8 時，把 `import org.threeten.bp.*` 改成 `import java.time.*` 即可，程式碼不用改。

## Spring 整合

**原則：Spring 註解只出現在 `adapter` 與 `config`（`<main>`）。**

| ❌ 不要 | ✅ 改為 |
|---|---|
| use case 上加 `@Service`、`@Component` | 在 `config` 的 `@Configuration` 用 `@Bean` 建立 |
| use case 上加 `@Transactional` | `UnitOfWork` port（實作用 `TransactionTemplate`），或在 config 用 decorator 包裝 |
| use case / entity 欄位上 `@Autowired` | 建構子注入 |
| domain entity 上加 `@Entity` | `adapter/persistence/sql/LoanJpaEntity` + mapper |
| use case 回傳 `ResponseEntity`、`ModelAndView` | controller 中轉換 |
| controller 用欄位 `@Autowired` | 建構子注入（Spring 4.3 起單一建構子可省略 `@Autowired`，之前的版本要加在建構子上） |

### 版本對照

| Spring 版本 | 最低 JDK | 備註 |
|---|---|---|
| Spring 3.2 | 1.5 | `@ControllerAdvice`、`TransactionTemplate`、`@Profile`（3.1+）都可用 |
| Spring 4.x | 1.6（4.3 建議 1.7+） | `@RestController`（4.0+）；Spring Boot 1.x 對應此版 |
| Spring 5.x / Boot 2.x | 1.8 | 可用 lambda 版的 API |
| Spring 6.x / Boot 3.x | 17 | `javax.*` 改為 `jakarta.*` |

### Java Config（Spring 3.1+）

完整範例見 [04-frameworks-drivers.md](../layers/04-frameworks-drivers.md#範例程式碼)（`LendingConfig`、`CsvStorageConfig`、`SqlStorageConfig`）。

### XML 設定（舊專案常見）

還在用 XML 設定的專案，原則一樣：use case 類別是 POJO，在 XML 中用建構子注入組裝。

```xml
<!-- FILE: src/main/resources/applicationContext-lending.xml -->
<beans profile="csv">
    <bean id="csvStore" class="com.example.library.lending.adapter.persistence.csv.CsvStore">
        <constructor-arg><bean class="java.io.File"><constructor-arg value="${data.dir}"/></bean></constructor-arg>
        <constructor-arg><bean class="java.nio.charset.Charset" factory-method="forName">
            <constructor-arg value="${csv.encoding}"/></bean></constructor-arg>
    </bean>
    <bean id="loanRepository" class="com.example.library.lending.adapter.persistence.csv.CsvLoanRepository">
        <constructor-arg ref="csvStore"/>
    </bean>
</beans>

<bean id="clock" class="com.example.library.lending.adapter.time.SystemClock"/>

<bean id="borrowBook" class="com.example.library.lending.application.usecase.borrowbook.BorrowBook">
    <constructor-arg ref="memberRepository"/>
    <constructor-arg ref="bookRepository"/>
    <constructor-arg ref="loanRepository"/>
    <constructor-arg ref="clock"/>
    <constructor-arg ref="notifier"/>
    <constructor-arg ref="unitOfWork"/>
</bean>
```

### 交易

`UnitOfWork` 的 Spring 實作（`SpringUnitOfWork`，用 `TransactionTemplate`）見 [crossing-boundaries.md](../concepts/crossing-boundaries.md#3-交易transaction怎麼處理)。

## 強制依賴規則

依 JDK 版本與團隊習慣選一種（可以並用）：

### 方法 1：Maven 多模組（Java 7 可用，最強制）

由**編譯器**保證依賴方向：內層模組的 `pom.xml` 根本不宣告 Spring、JDBC，也不依賴外層模組，違反時直接編譯失敗。

```
library/
├── pom.xml                       # parent，<packaging>pom</packaging>
├── library-domain/               # 不依賴任何模組；只有 threetenbp（Java 7）
├── library-application/          # 依賴 library-domain
├── library-adapter/              # 依賴 library-application、Spring MVC、JDBC、commons-csv
└── library-boot/                 # 依賴全部，放 config 與啟動類別
```

```xml
<!-- FILE: library-application/pom.xml（節錄）-->
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>library-domain</artifactId>
        <version>${project.version}</version>
    </dependency>
    <!-- 沒有 spring、沒有 jdbc：use case import 它們會編譯失敗 -->
</dependencies>
```

缺點：模組數多、建置較慢；by-feature 時每個業務模組 × 每層都拆，模組會很多。可只在「domain + application」與「其他」之間切一刀。

### 方法 2：零依賴檢查程式（Java 7 可用）

不想拆 Maven 模組時，用 [templates/scripts/CheckArch.java](../templates/scripts/CheckArch.java)：掃描原始碼的 `import`，
檢查內層有沒有 import 外層或框架，以及模組之間是否只透過 `api` package。

```bash
javac -encoding UTF-8 -d target/check scripts/CheckArch.java
java -cp target/check CheckArch src/main/java com.example.library
```

可以加進 CI，或在 Maven 用 `exec-maven-plugin` 綁到 `verify` 階段。
原始碼是 Big5 時第三個參數傳 `MS950`。輸出的中文在終端機變亂碼時，JDK 18+ 加 `-Dstdout.encoding=UTF-8`，舊版加 `-Dfile.encoding=UTF-8`。

### 方法 3：ArchUnit（需要 JDK 8+）

```java
@RunWith(ArchUnitRunner.class)                                    // JUnit 4
@AnalyzeClasses(packages = "com.example.library")
public class ArchitectureTest {
    @ArchTest
    public static final ArchRule layers = layeredArchitecture()
        .layer("Domain").definedBy("..domain..")
        .layer("Application").definedBy("..application..")
        .layer("Adapter").definedBy("..adapter..")
        .layer("Config").definedBy("..config..")
        .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Config")
        .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Config")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Config");

    @ArchTest
    public static final ArchRule noFrameworkInside = noClasses()
        .that().resideInAnyPackage("..domain..", "..application..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "javax.persistence..", "jakarta.persistence..", "javax.servlet..");
}
```

規則用 `..domain..` 等萬用字元，**by-layer 與 by-feature 都適用**。
by-feature 的模組隔離：加一條規則禁止 `X.domain` / `X.application` / `X.adapter` 被其他模組存取，只開放 `X.api`。
