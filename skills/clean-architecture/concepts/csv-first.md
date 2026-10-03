# CSV 優先：新功能先不接資料庫

**預設規則：每個新功能的儲存都先用 CSV 實作並跑通，使用者確認行為正確後，才換成資料庫。**

> 出處：Robert C. Martin 在《Clean Architecture》提到 FitNesse 專案前期一直用檔案儲存，
> 資料庫的決定一路延後，最後發現根本不需要。好的架構讓你**延後**技術決定。

## 為什麼

| 好處 | 說明 |
|---|---|
| 驗證 port 是乾淨的 | CSV 沒有 JOIN、沒有 ORM。CSV 實作得出來，就代表 port 沒有偷偷依賴資料庫能力 |
| 先確認業務行為 | 先讓使用者看到功能跑起來，再花時間在 schema、migration、索引 |
| 資料看得見 | 直接打開檔案就能檢查、手動修改測試資料 |
| 換 DB 時有基準 | 同一套 contract test，CSV 通過後 DB 也必須通過 |

## 與 in-memory fake 的差別

| | in-memory fake | CSV adapter |
|---|---|---|
| 用途 | 單元測試 | 實際執行、手動驗證、展示 |
| 放哪 | `<tests>/fakes/` | `<adapters>/persistence/csv/` |
| 重啟後資料 | 消失 | 保留 |
| 會測到 mapper | 不會 | 會（entity ⇄ 文字欄位） |

兩者都要有。

## 檔案佈局

```
project/
├── data/                         # 執行時資料（.gitignore）
│   ├── members.csv
│   ├── books.csv
│   └── loans.csv
├── data-seed/                    # 範例資料（commit），首次啟動時複製到 data/
└── <adapters>/persistence/
    ├── LoanMapper.java           # entity ⇄ row（CSV 與 SQL 共用欄位名稱）
    └── csv/
        ├── CsvStore.java         # 共用：讀寫、原子寫入、upsert
        ├── CsvLoanRepository.java
        ├── CsvBookRepository.java
        └── CsvUnitOfWork.java
```

## 格式約定

| 項目 | 約定 |
|---|---|
| 編碼 | 依 card.md 的 CSV 編碼（預設 UTF-8）；要給 Excel 直接開用 UTF-8 with BOM；Big5 見 [file-encoding.md](file-encoding.md) |
| 第一列 | 欄位名稱 |
| 一個檔案 | 一種 entity（一張「表」） |
| 日期時間 | ISO 8601：`2026-10-03`、`2026-10-03T10:00:00`（`LocalDate` / `LocalDateTime` 的 `toString()` 與 `parse()`） |
| 空值 | 空字串 = `null` |
| 一對多 | 分開兩個檔案，用 id 關聯（不要在欄位中塞 JSON） |
| 金額 | 整數最小單位（元 / 分），用 `long`，不用 `double` |

```csv
id,member_id,book_id,borrowed_at,due_date,returned_at,renew_count
loan-1,m1,b1,2026-10-01T10:00:00,2026-10-15,,0
```

## 樣板

Java，相容 JDK 1.7。CSV 解析使用 Apache Commons CSV（Java 7 專案請選用支援 Java 7 的版本，例如 1.5）。

```java
// FILE: <adapters>/persistence/csv/CsvStore.java
public class CsvStore {
    private final File dataDir;
    private final Charset charset;              // UTF-8 / Big5（MS950）等，見 file-encoding.md

    public CsvStore(File dataDir, Charset charset) {
        this.dataDir = dataDir;
        this.charset = charset;
    }

    public List<Map<String, String>> readAll(String file) {
        File f = new File(dataDir, file);
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        if (!f.exists()) {
            return rows;
        }
        // 使用 CSV 函式庫，不要自己 split(",")（欄位內可能有逗號或換行）
        try (Reader reader = new InputStreamReader(new FileInputStream(f), charset);
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {
            for (CSVRecord record : parser) {
                rows.add(new LinkedHashMap<String, String>(record.toMap()));
            }
            return rows;
        } catch (IOException e) {
            throw new CsvStorageException("讀取失敗：" + file, e);   // unchecked，不讓 IOException 往內洩漏
        }
    }

    // 依 id 原地取代，找不到才加在最後（「先刪再 append」會打亂列順序，連帶改變畫面清單順序）
    public void upsert(String file, Map<String, String> row, String[] columns) {
        List<Map<String, String>> rows = readAll(file);
        boolean replaced = false;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).get("id").equals(row.get("id"))) {
                rows.set(i, row);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            rows.add(row);
        }
        writeAll(file, rows, columns);
    }

    public void writeAll(String file, List<Map<String, String>> rows, String[] columns) {
        Path target = new File(dataDir, file).toPath();
        Path tmp = new File(dataDir, file + ".tmp").toPath();
        // REPORT：遇到編碼無法表示的字元直接報錯，不可悄悄變成 ?
        CharsetEncoder encoder = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(tmp), encoder);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(columns))) {
            for (Map<String, String> row : rows) {
                List<String> values = new ArrayList<String>();
                for (String column : columns) {
                    values.add(row.get(column));
                }
                printer.printRecord(values);
            }
        } catch (IOException e) {
            throw new CsvStorageException("寫入失敗：" + file, e);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);  // 原子替換
        } catch (IOException e) {
            throw new CsvStorageException("取代檔案失敗：" + file, e);
        }
    }
}

// FILE: <adapters>/persistence/csv/CsvLoanRepository.java
public class CsvLoanRepository implements LoanRepository {
    private static final String FILE = "loans.csv";
    private final CsvStore store;

    public CsvLoanRepository(CsvStore store) { this.store = store; }

    @Override
    public Loan findById(LoanId id) {
        for (Map<String, String> row : store.readAll(FILE)) {
            if (row.get("id").equals(id.getValue())) {
                return LoanMapper.toEntity(row);
            }
        }
        return null;
    }

    @Override
    public List<Loan> findOpenByMember(MemberId memberId) {
        List<Loan> result = new ArrayList<Loan>();
        for (Map<String, String> row : store.readAll(FILE)) {
            if (row.get("member_id").equals(memberId.getValue()) && row.get("returned_at").isEmpty()) {
                result.add(LoanMapper.toEntity(row));
            }
        }
        return result;
    }

    @Override
    public void save(Loan loan) { store.upsert(FILE, LoanMapper.toRow(loan), LoanMapper.COLUMNS); }

    @Override
    public LoanId nextId() { return new LoanId(UUID.randomUUID().toString()); }
}

// FILE: <adapters>/persistence/csv/CsvUnitOfWork.java
// 簡化版交易：執行前快照所有 CSV，失敗時還原。
// synchronized：同一時間只跑一個 work，避免兩個寫入互相覆蓋檔案。僅適用單一程序。
public class CsvUnitOfWork implements UnitOfWork {
    private final File dataDir;

    public CsvUnitOfWork(File dataDir) { this.dataDir = dataDir; }

    @Override
    public synchronized void run(Runnable work) {
        Map<File, byte[]> snapshot = takeSnapshot();
        try {
            work.run();
        } catch (RuntimeException e) {
            restore(snapshot);
            throw e;
        }
    }

    private Map<File, byte[]> takeSnapshot() {
        Map<File, byte[]> snapshot = new HashMap<File, byte[]>();
        File[] files = dataDir.listFiles();
        if (files == null) {
            return snapshot;
        }
        try {
            for (File f : files) {
                if (f.getName().endsWith(".csv")) {
                    snapshot.put(f, Files.readAllBytes(f.toPath()));
                }
            }
            return snapshot;
        } catch (IOException e) {
            throw new CsvStorageException("建立快照失敗", e);
        }
    }

    private void restore(Map<File, byte[]> snapshot) {
        try {
            for (Map.Entry<File, byte[]> entry : snapshot.entrySet()) {
                Files.write(entry.getKey().toPath(), entry.getValue());
            }
        } catch (IOException e) {
            throw new CsvStorageException("還原快照失敗", e);
        }
    }
}
```

查詢型 port（`LendingQueries`）的 CSV 版本：分別讀取多個檔案，在記憶體中 join。

```java
// FILE: <adapters>/persistence/csv/CsvLendingQueries.java   （節錄）
public List<LoanSummary> listOpenLoansByMember(MemberId memberId) {
    Map<String, String> titles = new HashMap<String, String>();
    for (Map<String, String> book : store.readAll("books.csv")) {
        titles.put(book.get("id"), book.get("title"));
    }
    List<LoanSummary> result = new ArrayList<LoanSummary>();
    for (Map<String, String> row : store.readAll("loans.csv")) {
        if (row.get("member_id").equals(memberId.getValue()) && row.get("returned_at").isEmpty()) {
            result.add(new LoanSummary(row.get("id"), titles.get(row.get("book_id")),
                    LocalDate.parse(row.get("due_date"))));
        }
    }
    return result;
}
```

## 組裝：用設定切換

Spring：用 profile `csv` / `sql` 各一個 `@Configuration`，完整範例見 [04-frameworks-drivers.md](../layers/04-frameworks-drivers.md#範例程式碼)。

```properties
spring.profiles.active=csv        # 新功能預設；使用者確認後改成 sql
```

## 限制（要告知使用者）

| 限制 | 影響 | 何時該換 DB |
|---|---|---|
| 只支援單一程序 | 多個 instance 同時寫入會互相覆蓋 | 要部署多個 instance |
| 全檔讀寫 | 資料量大時變慢 | 單檔超過約一萬列 |
| 沒有唯一鍵約束 | 重複資料要靠 adapter 檢查 | 需要資料庫層級保證 |
| 交易是快照還原 | 不是真正的隔離 | 有併發寫入需求 |
| 讀取在交易外 | 兩人同時借同一本書，可能都通過檢查（見 [crossing-boundaries.md 競態](crossing-boundaries.md#讀取在交易外的競態)） | 多人同時操作同一筆資料 |

這些限制**只影響 adapter**，不影響 domain 與 application。這正是重點。

## 何時可以跳過 CSV

以下情況可以直接接資料庫，但**要先告知使用者**，並記錄到專案的 `conventions.md`：

- 功能本質依賴資料庫能力：全文搜尋、地理查詢、大量報表彙總
- 專案已有成熟的 DB adapter，且新功能只是在既有 port 加一個簡單方法
- 使用者明確要求

## 各語言 CSV 函式庫

| 語言 | 建議 |
|---|---|
| Java | Apache Commons CSV（Java 7 用 1.5 這類支援 Java 7 的版本） |
| TypeScript | `csv-parse` + `csv-stringify` |
| Python | 標準庫 `csv`（`DictReader` / `DictWriter`） |
| Go | 標準庫 `encoding/csv` |
