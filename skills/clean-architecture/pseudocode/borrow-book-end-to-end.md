# 端到端範例：借書（BorrowBook）

從 HTTP 請求一路到資料庫，每個檔案都列出來。這是**所有寫入型功能的樣板**，換掉名詞即可套用。
語法見 [pseudocode-syntax.md](../../../shared/pseudocode-syntax.md)。

## 檔案總覽

路徑使用層級佔位符，實際位置依 by-layer / by-feature 而定，依專案 card.md 的路徑表替換（尚未設定時見 [project-interview.md 3a](../workflows/project-interview.md#3a-路徑表依題-34-的答案填入-cardmd)）。

```
├── <domain>/
│   ├── ids.x                         ① Value：MemberId, BookId, LoanId
│   ├── errors.x                      ② 領域錯誤
│   ├── member.x                      ③ Entity
│   ├── book.x                        ③ Entity
│   └── loan.x                        ③ Entity
├── <application>/
│   ├── errors.x                      ④ 應用錯誤
│   ├── ports/
│   │   ├── member_repository.x       ⑤ Output port
│   │   ├── book_repository.x
│   │   ├── loan_repository.x
│   │   ├── clock.x
│   │   ├── notifier.x
│   │   └── unit_of_work.x
│   └── use_cases/borrow_book/
│       ├── borrow_book_dto.x         ⑥ Input / Output DTO
│       └── borrow_book.x             ⑦ Use case
├── <adapters>/
│   ├── http/
│   │   ├── loan_controller.x         ⑧ Driving adapter
│   │   └── error_mapping.x
│   ├── persistence/
│   │   ├── loan_mapper.x
│   │   ├── csv/csv_loan_repository.x ⑨ Driven adapter（先做，見 concepts/csv-first.md）
│   │   └── sql/sql_loan_repository.x ⑨ 使用者確認後才做
│   ├── notification/smtp_notifier.x  ⑨
│   └── time/system_clock.x           ⑨
├── <main>                            ⑩ Composition root
└── <tests>/
    ├── domain/member_test.x          ⑪
    ├── application/borrow_book_test.x ⑫
    └── fakes/                        ⑫ in-memory 假實作
```

**實作順序 = 編號順序**（由內往外）。

---

## ① ② Domain：Value 與錯誤

```
// FILE: <domain>/ids.x
VALUE MemberId { value: String }      // 建構時檢查非空
VALUE BookId   { value: String }
VALUE LoanId   { value: String }

// FILE: <domain>/errors.x
DOMAIN_ERROR BookNotAvailable     code "BOOK_NOT_AVAILABLE"
DOMAIN_ERROR MemberSuspended      code "MEMBER_SUSPENDED"
DOMAIN_ERROR LoanLimitExceeded    code "LOAN_LIMIT_EXCEEDED"
DOMAIN_ERROR HasOverdueLoans      code "HAS_OVERDUE_LOANS"
```

## ③ Domain：Entities

`Member`、`Loan` 的完整內容見 [layers.md](../concepts/layers.md#1-entitiesdomain)。

```
// FILE: <domain>/book.x
ENUM BookStatus { AVAILABLE, ON_LOAN }

ENTITY Book
  id: BookId
  title: String
  status: BookStatus

  FUNCTION markAsLent()
    IF status != AVAILABLE  FAIL BookNotAvailable        // R4
    status ← ON_LOAN

  MUST NOT import anything outside domain/
```

## ④ ⑤ Application：錯誤與 Ports

```
// FILE: <application>/errors.x
APP_ERROR MemberNotFound  code "MEMBER_NOT_FOUND"
APP_ERROR BookNotFound    code "BOOK_NOT_FOUND"

// FILE: <application>/ports/member_repository.x
PORT MemberRepository
  FUNCTION findById(id: MemberId) -> Member | Nothing

// FILE: <application>/ports/book_repository.x
PORT BookRepository
  FUNCTION findById(id: BookId) -> Book | Nothing
  FUNCTION save(book: Book)

// loan_repository / clock / notifier / unit_of_work 見 shared/ports-and-adapters.md
```

## ⑥ Application：DTO

```
// FILE: <application>/use_cases/borrow_book/borrow_book_dto.x
DTO BorrowBookInput
  memberId: String          // 用基本型別，controller 不必認識 domain 的 Value
  bookId:   String

DTO BorrowBookOutput
  loanId:  String
  dueDate: Date             // A2
```

## ⑦ Application：Use Case（重點）

```
// FILE: <application>/use_cases/borrow_book/borrow_book.x
USE_CASE BorrowBook
  DEPENDS ON
    members:  MemberRepository
    books:    BookRepository
    loans:    LoanRepository
    clock:    Clock
    notifier: Notifier
    uow:      UnitOfWork

  FUNCTION execute(input: BorrowBookInput) -> BorrowBookOutput
    // 1. 輸入轉為 domain 型別
    memberId ← TRY MemberId(input.memberId)
    bookId   ← TRY BookId(input.bookId)
    now      ← clock.now()

    // 2. 載入
    member ← members.findById(memberId)
    IF member == Nothing  FAIL MemberNotFound
    book ← books.findById(bookId)
    IF book == Nothing    FAIL BookNotFound
    openLoans ← loans.findOpenByMember(memberId)

    // 3. 業務規則 —— 全部委派給 entity，這裡沒有 if 判斷規則
    TRY member.assertCanBorrow(openLoans, now)          // R1 R2 R3
    TRY book.markAsLent()                               // R4
    loan ← Loan.open(loans.nextId(), memberId, bookId, now)   // R5

    // 4. 儲存（同一交易）
    TRY uow.run(() ->
      books.save(book)
      loans.save(loan)
    )

    // 5. 副作用（交易成功後）
    notifier.notifyBookBorrowed(memberId, book.title, loan.dueDate)   // A1

    // 6. 回傳 DTO，不回傳 entity
    RETURN BorrowBookOutput(loanId: loan.id.value, dueDate: loan.dueDate)

  MUST NOT import adapters, infrastructure, http, orm, framework
```

> 檢查重點：use case 中的 `IF` 只做「找不到」這類流程判斷。
> 若你在這裡寫出 `IF openLoans.count >= 3`，代表規則漏進了 use case，應移回 `Member`。

## ⑧ Adapter：HTTP Controller

```
// FILE: <adapters>/http/loan_controller.x
ADAPTER HttpLoanController
  DEPENDS ON borrowBook: BorrowBook

  // POST /loans   body: { "memberId": "...", "bookId": "..." }
  FUNCTION handleBorrow(request: HttpRequest) -> HttpResponse
    body ← request.json()
    IF body.memberId is not String OR body.bookId is not String
      RETURN HttpResponse(400, { error: "INVALID_INPUT" })

    input ← BorrowBookInput(memberId: body.memberId, bookId: body.bookId)
    result ← borrowBook.execute(input)
    ON ERROR e
      (status, code) ← toHttpStatus(e)                 // error_mapping.x
      RETURN HttpResponse(status, { error: code })

    RETURN HttpResponse(201, {
      loanId:  result.loanId,
      dueDate: formatIsoDate(result.dueDate)
    })

  FUNCTION registerRoutes(server)
    server.post("/loans", handleBorrow)
```

## ⑨ Adapters：Driven

```
// FILE: <adapters>/persistence/csv/csv_loan_repository.x   ← 先做這個
// 完整樣板見 concepts/csv-first.md
ADAPTER CsvLoanRepository IMPLEMENTS LoanRepository
  DEPENDS ON store: CsvStore
  FUNCTION findOpenByMember(memberId)
    RETURN store.readAll("loans.csv")
      .filter(r -> r.member_id == memberId.value AND r.returned_at == "")
      .map(toEntity)                                   // loan_mapper.x

// FILE: <adapters>/persistence/sql/sql_loan_repository.x   ← 使用者確認功能後才做
ADAPTER SqlLoanRepository IMPLEMENTS LoanRepository
  DEPENDS ON db: DatabaseConnection

  FUNCTION findOpenByMember(memberId)
    rows ← db.query("SELECT * FROM loans WHERE member_id = ? AND returned_at IS NULL", memberId.value)
    RETURN rows.map(toEntity)                           // loan_mapper.x

  FUNCTION save(loan)
    db.upsert("loans", toRow(loan))

  FUNCTION nextId()
    RETURN LoanId(generateUuid())

// FILE: <adapters>/time/system_clock.x
ADAPTER SystemClock IMPLEMENTS Clock
  FUNCTION now() -> RETURN os.currentTime()

// FILE: <adapters>/notification/smtp_notifier.x
ADAPTER SmtpNotifier IMPLEMENTS Notifier
  DEPENDS ON smtp: SmtpClient, members: MemberRepository
  FUNCTION notifyBookBorrowed(memberId, bookTitle, dueDate)
    member ← members.findById(memberId)
    smtp.send(to: member.email, subject: "借書成功", body: renderTemplate("borrowed", bookTitle, dueDate))
```

## ⑩ Composition Root

見 [layers.md 第 4 節](../concepts/layers.md#4-frameworks--driversinfrastructure--main)。

## ⑪ Domain 測試（不需要任何假物件）

```
// FILE: <tests>/domain/member_test.x
TEST "一般會員已借 3 本時不能再借"
  member ← Member(id, email, tier: REGULAR, suspended: false)
  loans  ← [openLoan(), openLoan(), openLoan()]
  EXPECT member.assertCanBorrow(loans, now) FAILS WITH LoanLimitExceeded

TEST "VIP 已借 3 本仍可借"
  member ← Member(..., tier: VIP, ...)
  EXPECT member.assertCanBorrow([openLoan(), openLoan(), openLoan()], now) SUCCEEDS
```

## ⑫ Use Case 測試（用 in-memory fakes）

```
// FILE: <tests>/fakes/in_memory_loan_repository.x
ADAPTER InMemoryLoanRepository IMPLEMENTS LoanRepository
  items: Map<LoanId, Loan>
  FUNCTION findOpenByMember(id) -> RETURN items.values.filter(l -> l.memberId == id AND l.returnedAt == Nothing)
  FUNCTION save(loan)           -> items[loan.id] ← loan
  FUNCTION nextId()             -> RETURN LoanId("loan-" + items.size + 1)

// FILE: <tests>/fakes/fixed_clock.x
ADAPTER FixedClock IMPLEMENTS Clock
  time: DateTime
  FUNCTION now() -> RETURN time

// FILE: <tests>/application/borrow_book_test.x
TEST "借書成功：書變為借出、產生借閱、到期日為 14 天後、寄出通知"
  members  ← InMemoryMemberRepository([member("m1")])
  books    ← InMemoryBookRepository([availableBook("b1")])
  loans    ← InMemoryLoanRepository([])
  clock    ← FixedClock(2026-01-01T10:00)
  notifier ← SpyNotifier()
  useCase  ← BorrowBook(members, books, loans, clock, notifier, NoopUnitOfWork())

  out ← useCase.execute(BorrowBookInput("m1", "b1"))

  EXPECT out.dueDate == 2026-01-15
  EXPECT books.findById(BookId("b1")).status == ON_LOAN
  EXPECT notifier.calls.count == 1

TEST "會員不存在時回報 MemberNotFound，且不寄信"
  ...
  EXPECT useCase.execute(BorrowBookInput("nobody", "b1")) FAILS WITH MemberNotFound
  EXPECT notifier.calls.count == 0
```
