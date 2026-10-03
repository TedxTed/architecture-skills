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
└── <adapters>/persistence/csv/
    ├── csv_store.x               # 共用：讀寫、原子寫入
    ├── csv_loan_repository.x
    ├── csv_book_repository.x
    ├── csv_unit_of_work.x
    └── mappers.x                 # entity ⇄ CSV row（可與 SQL 共用 row 結構）
```

## 格式約定

| 項目 | 約定 |
|---|---|
| 編碼 | 依 card.md 的 CSV 編碼（預設 UTF-8）；要給 Excel 直接開用 UTF-8 with BOM；Big5 見 [file-encoding.md](file-encoding.md) |
| 第一列 | 欄位名稱 |
| 一個檔案 | 一種 entity（一張「表」） |
| 日期時間 | ISO 8601：`2026-10-03`、`2026-10-03T10:00:00Z` |
| 空值 | 空字串 = Nothing |
| 一對多 | 分開兩個檔案，用 id 關聯（不要在欄位中塞 JSON） |
| 金額 | 整數最小單位（元 / 分），不用浮點數 |

```csv
id,member_id,book_id,borrowed_at,due_date,returned_at,renew_count
loan-1,m1,b1,2026-10-01T10:00:00Z,2026-10-15,,0
```

## 樣板

```
// FILE: <adapters>/persistence/csv/csv_store.x
ADAPTER CsvStore
  DEPENDS ON dataDir: Path, encoding: String          // "utf-8" | "utf-8-bom" | "cp950"

  FUNCTION readAll(file: String) -> List<Map<String, String>>
    IF NOT exists(dataDir / file)  RETURN []
    RETURN parseCsv(readText(dataDir / file, encoding))   // 使用語言的 CSV 函式庫，不要自己 split(",")

  FUNCTION writeAll(file: String, rows: List<Map>, columns: List<String>)
    tmp ← dataDir / (file + ".tmp")
    writeText(tmp, formatCsv(rows, columns), encoding)    // 無法表示的字元要報錯，不可變成 ?
    rename(tmp, dataDir / file)                        // 原子替換，避免寫到一半損毀

// FILE: <adapters>/persistence/csv/csv_loan_repository.x
ADAPTER CsvLoanRepository IMPLEMENTS LoanRepository
  DEPENDS ON store: CsvStore
  CONSTANT FILE = "loans.csv"
  CONSTANT COLUMNS = [id, member_id, book_id, borrowed_at, due_date, returned_at, renew_count]

  FUNCTION findById(id)
    row ← store.readAll(FILE).find(r -> r.id == id.value)
    RETURN row == Nothing ? Nothing : toEntity(row)

  FUNCTION findOpenByMember(memberId)
    RETURN store.readAll(FILE)
      .filter(r -> r.member_id == memberId.value AND r.returned_at == "")
      .map(toEntity)

  FUNCTION save(loan)
    rows ← store.readAll(FILE).filter(r -> r.id != loan.id.value)   // upsert
    rows.append(toRow(loan))
    store.writeAll(FILE, rows, COLUMNS)

  FUNCTION nextId()
    RETURN LoanId(generateUuid())

// FILE: <adapters>/persistence/csv/csv_unit_of_work.x
// 簡化版交易：執行前快照，失敗時還原。僅適用單一程序。
ADAPTER CsvUnitOfWork IMPLEMENTS UnitOfWork
  DEPENDS ON dataDir: Path
  FUNCTION run(work)
    snapshot ← copyAllFiles(dataDir)
    TRY
      work()
    ON ERROR e
      restoreFiles(dataDir, snapshot)
      FAIL e
```

查詢型 port（`LoanQueries`）的 CSV 版本：分別讀取多個檔案，在記憶體中 join。

```
// FILE: <adapters>/persistence/csv/csv_loan_queries.x
FUNCTION listByMember(memberId)
  books ← store.readAll("books.csv").indexBy(r -> r.id)
  RETURN store.readAll("loans.csv")
    .filter(r -> r.member_id == memberId.value)
    .map(r -> LoanSummary(r.id, books[r.book_id].title, parseDate(r.due_date), ...))
```

## 組裝：用設定切換

```
// FILE: <main>
IF config.storage == "csv"            // 預設值
  store ← CsvStore(config.dataDir, config.csvEncoding)
  loans ← CsvLoanRepository(store)
  uow   ← CsvUnitOfWork(config.dataDir)
ELSE IF config.storage == "sql"
  db    ← connectDatabase(config.dbUrl)
  loans ← SqlLoanRepository(db)
  uow   ← SqlUnitOfWork(db)
```

## 限制（要告知使用者）

| 限制 | 影響 | 何時該換 DB |
|---|---|---|
| 只支援單一程序 | 多個 instance 同時寫入會互相覆蓋 | 要部署多個 instance |
| 全檔讀寫 | 資料量大時變慢 | 單檔超過約一萬列 |
| 沒有唯一鍵約束 | 重複資料要靠 adapter 檢查 | 需要資料庫層級保證 |
| 交易是快照還原 | 不是真正的隔離 | 有併發寫入需求 |

這些限制**只影響 adapter**，不影響 domain 與 application。這正是重點。

## 何時可以跳過 CSV

以下情況可以直接接資料庫，但**要先告知使用者**，並記錄到專案的 `conventions.md`：

- 功能本質依賴資料庫能力：全文搜尋、地理查詢、大量報表彙總
- 專案已有成熟的 DB adapter，且新功能只是在既有 port 加一個簡單方法
- 使用者明確要求

## 各語言 CSV 函式庫

| 語言 | 建議 |
|---|---|
| TypeScript | `csv-parse` + `csv-stringify` |
| Python | 標準庫 `csv`（`DictReader` / `DictWriter`） |
| Go | 標準庫 `encoding/csv` |
| Java | Apache Commons CSV |
