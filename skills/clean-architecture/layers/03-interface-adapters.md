# 第 3 層：Interface Adapters（介面轉接）

> 位置：`<adapters>`｜依賴：Use Cases、Entities、外部函式庫｜被誰使用：Frameworks & Drivers（組裝、路由）

## 職責

**翻譯**。把外面的格式（HTTP、CLI 參數、CSV、DB row、第三方 API）轉成內層看得懂的，
把內層的結果轉成外面要的。**Adapter 不做業務判斷。**

## 兩個方向

| | Driving（輸入端 / Primary） | Driven（輸出端 / Secondary） |
|---|---|---|
| 誰呼叫誰 | 外界 → adapter → use case | use case → port → adapter → 外界 |
| 範例 | HTTP controller、CLI handler、MQ consumer | CSV / SQL repository、SMTP notifier、SystemClock |
| 依賴 | use case 類別（或 input port） | 實作 `<application>` 定義的 port |

## 遵循規則

| 必須 | 禁止 |
|---|---|
| 只做三件事：**轉換、委派、翻譯錯誤** | 業務判斷（`if member.tier == VIP`） |
| Controller 一個請求只呼叫**一個** use case | 在 controller 編排多個 use case（那是新的 use case） |
| Repository 進出的是 **entity** | 回傳 ORM model / DB row 給 use case |
| 外部錯誤翻譯成內層錯誤；內層錯誤翻譯成外部格式 | 讓 `SQLException`、SDK 錯誤型別往內洩漏 |
| 錯誤對應（領域錯誤 → HTTP 狀態碼）集中在一處 | 每個 controller 各寫一套錯誤對應 |
| 第三方 SDK、ORM、框架註解只出現在本層 | adapter 之間直接互相呼叫內部細節 |

## 放什麼 / 不放什麼

| 種類 | 方向 | 範例 | 做什麼 |
|---|---|---|---|
| Controller / Handler | Driving | `HttpLoanController`、`AdminCli` | 解析輸入 → Input DTO → 呼叫 use case → 轉回應 |
| Presenter（選用） | Driving | `BorrowBookJsonPresenter` | Output DTO → JSON / HTML |
| Error Mapping | Driving | `error_mapping` | 領域 / 應用錯誤 → HTTP 狀態碼 |
| Repository 實作 | Driven | `CsvLoanRepository`、`SqlLoanRepository` | Entity ⇄ 儲存格式 |
| Gateway | Driven | `SmtpNotifier`、`SystemClock` | 呼叫外部服務 |
| Mapper | 雙向 | `loan_mapper` | 純轉換函式 |
| Request / Response Model | Driving | `BorrowRequest`、`BorrowResponse` | API 格式與傳輸驗證（見 [dto.md](../concepts/dto.md)） |
| ORM model / Row | Driven | `LoanRow` | 資料表結構（也可放 infrastructure） |
| ❌ 不放 | — | — | 業務規則、流程編排、組裝（`new` 其他 adapter） |

## 怎麼寫（步驟）

**Driven（先做，見 [csv-first.md](../concepts/csv-first.md)）**
1. 找到要實作的 port，不修改它
2. 寫 mapper：entity ⇄ row（`reconstitute` 還原，讀欄位寫出）
3. 先實作 CSV 版本 → 通過 contract test
4. 使用者確認功能後，才實作 SQL 版本 → 通過同一套 contract test

**Driving**
1. 解析並驗證**傳輸格式**（欄位存在、型別正確）→ 不合法回 400
2. 組成 Input DTO，呼叫 use case
3. 成功：Output DTO → 回應格式；失敗：交給 error mapping

## Pseudocode

```
// FILE: <adapters>/http/loan_controller.x            （Driving）
ADAPTER HttpLoanController
  DEPENDS ON borrowBook: BorrowBook

  // POST /loans   body: { "memberId": "...", "bookId": "..." }
  FUNCTION handleBorrow(request: HttpRequest) -> HttpResponse
    body ← request.json()
    IF body.memberId is not String OR body.bookId is not String
      RETURN HttpResponse(400, { error: "INVALID_INPUT" })      // 傳輸格式驗證

    result ← borrowBook.execute(BorrowBookInput(body.memberId, body.bookId))
    ON ERROR e → RETURN errorResponse(e)

    RETURN HttpResponse(201, { loanId: result.loanId, dueDate: formatIsoDate(result.dueDate) })

  FUNCTION registerRoutes(server)
    server.post("/loans", handleBorrow)

// FILE: <adapters>/http/error_mapping.x
FUNCTION errorResponse(error) -> HttpResponse
  MATCH error
    MemberNotFound, BookNotFound              → HttpResponse(404, { error: error.code })
    BookNotAvailable, HasOverdueLoans         → HttpResponse(409, { error: error.code })
    MemberSuspended, LoanLimitExceeded        → HttpResponse(422, { error: error.code })
    otherwise                                 → log(error); HttpResponse(500, { error: "INTERNAL_ERROR" })

// FILE: <adapters>/persistence/loan_mapper.x          （CSV 與 SQL 共用）
FUNCTION toEntity(row) -> Loan
  RETURN Loan.reconstitute(LoanId(row.id), MemberId(row.member_id), BookId(row.book_id),
                           parseDateTime(row.borrowed_at), parseDate(row.due_date),
                           row.returned_at == "" ? Nothing : parseDateTime(row.returned_at),
                           parseInt(row.renew_count))

FUNCTION toRow(loan: Loan) -> Row
  RETURN { id: loan.id.value, member_id: loan.memberId.value, ... }

// FILE: <adapters>/persistence/csv/csv_loan_repository.x     （Driven，先做）
ADAPTER CsvLoanRepository IMPLEMENTS LoanRepository
  DEPENDS ON store: CsvStore
  FUNCTION findOpenByMember(memberId)
    RETURN store.readAll("loans.csv")
      .filter(r -> r.member_id == memberId.value AND r.returned_at == "")
      .map(toEntity)
  FUNCTION save(loan)
    rows ← store.readAll("loans.csv").filter(r -> r.id != loan.id.value)
    store.writeAll("loans.csv", rows + [toRow(loan)], COLUMNS)

// FILE: <adapters>/notification/smtp_notifier.x       （Driven，Gateway）
ADAPTER SmtpNotifier IMPLEMENTS Notifier
  DEPENDS ON smtp: SmtpClient, members: MemberRepository
  FUNCTION notifyBookBorrowed(memberId, bookTitle, dueDate)
    member ← members.findById(memberId)
    TRY smtp.send(to: member.email, subject: "借書成功", body: render("borrowed", bookTitle, dueDate))
    ON ERROR SmtpError e → log.warn(e)                 // 外部錯誤不往內洩漏

// FILE: <adapters>/time/system_clock.x
ADAPTER SystemClock IMPLEMENTS Clock
  FUNCTION now() -> RETURN os.currentTime()
```

## 與其他層的配合

```
  外界                     Interface Adapters（本層）                    內層
 ───────                  ─────────────────────────                   ──────
 HTTP 請求 ──▶ Controller ──解析/驗證格式──▶ InputDTO ──execute──▶ Use Case
 HTTP 回應 ◀── Controller ◀──轉換格式／error mapping── OutputDTO / 錯誤 ◀──┘
                                                                        │
                                                  呼叫 port（介面在內層）│
                                                                        ▼
 CSV / DB  ◀──▶ CsvLoanRepository / SqlLoanRepository ◀── mapper ──▶ Entity
 SMTP      ◀──  SmtpNotifier
 OS 時間   ──▶  SystemClock
```

| 對象 | 關係 | 跨邊界傳什麼 |
|---|---|---|
| Use Cases | Driving：**我呼叫它**；Driven：**我實作它定義的 port** | Input / Output DTO；port 的參數與回傳（entity、domain 型別） |
| Entities | mapper **建構與讀取**它（`reconstitute`、讀欄位） | Entity ⇄ row |
| Frameworks & Drivers | **組裝我**、把連線 / 設定注入給我、把路由接到我 | DB 連線、SMTP client、設定值 |
| 外界 | **我直接面對**：HTTP、檔案、DB、第三方 API | 外部格式 |
| 其他 adapter | 原則上**不直接互相呼叫**；需要時透過 port（例如 SmtpNotifier 透過 MemberRepository port） | — |

**關鍵**：所有「外部格式 ⇄ 內部型別」的轉換都在這一層完成，而且**只在這一層**。

## 測試

| 對象 | 方式 |
|---|---|
| Controller | 用假的 use case，驗證「request → InputDTO」與「OutputDTO / 錯誤 → response」 |
| Repository | **Contract test**：InMemory、CSV、SQL 跑同一套測試（見 [add-adapter.md](../workflows/add-adapter.md#repository-contract-test強烈建議)） |
| Gateway | 用假的外部 client，驗證呼叫參數與錯誤翻譯 |
| Mapper | 單元測試：entity → row → entity 結果相同 |

## 自我檢查

- [ ] 沒有業務判斷；controller 只呼叫一個 use case
- [ ] Repository 回傳 entity，不是 row / ORM model
- [ ] 外部錯誤型別沒有往內洩漏；錯誤對應集中一處
- [ ] 所有 repository 實作通過同一套 contract test

常見錯誤：肥胖 controller、框架滲透 → [anti-patterns.md](../review/anti-patterns.md)
