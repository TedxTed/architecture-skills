# Go 對照

## Pseudocode → Go

| Pseudocode | Go 慣用寫法 |
|---|---|
| `ENTITY` | `struct`（未匯出欄位）+ 指標接收者方法 |
| `VALUE` | 具名型別：`type LoanID string`，或小型 struct（值接收者） |
| `ENUM` | `type BookStatus string` + `const` |
| `PORT` | `interface`，**定義在使用它的 package（application）** |
| `ADAPTER ... IMPLEMENTS` | 有對應方法的 struct（隱式實作），可用 `var _ app.LoanRepository = (*SQLLoanRepository)(nil)` 編譯期檢查 |
| `USE_CASE` | struct + `Execute(ctx, input)` 方法 |
| `DTO` | 純資料 struct |
| `FAIL` | `return ..., ErrXxx`（sentinel error 或自訂 error 型別） |
| `TRY` | `if err != nil { return ..., err }` |
| `Nothing` | `nil`，或回傳 `ErrNotFound` |

> **`context.Context` 可以進入 application 層嗎？** 可以。它是標準庫，用於取消與逾時，不是框架。但**不要**把 `*gin.Context` / `echo.Context` 傳進去。Domain 層通常不需要 ctx。

## 資料夾（Go 社群慣例）

```
A. by-layer                          B. by-feature
project/                             project/
├── cmd/server/main.go               ├── cmd/server/main.go
├── internal/                        ├── internal/
│   ├── domain/   # package domain   │   ├── lending/
│   │   ├── loan.go                  │   │   ├── domain/        # package domain
│   │   └── errors.go                │   │   ├── app/           # package app
│   ├── app/      # package app      │   │   ├── adapter/http/  # package httpadapter
│   │   ├── ports.go                 │   │   ├── adapter/postgres/
│   │   └── borrowbook.go            │   │   ├── api.go         # package lending：對外公開
│   └── adapter/                     │   │   └── module.go      # package lending：模組組裝
│       ├── http/                    │   ├── catalog/ ...
│       └── postgres/                │   └── sharedkernel/
└── go.mod                           └── go.mod
```

使用 `internal/` 防止外部專案 import。Go 套件名稱避免底線，所以用 `borrowbook` 而不是 `borrow_book`。
B 中模組根目錄的 package（`lending`）就是該模組的 public API；其他模組只 import `internal/lending`，不 import 其子 package。

## 範例

```go
// internal/domain/errors.go
package domain

import "errors"

var (
	ErrBookNotAvailable  = errors.New("BOOK_NOT_AVAILABLE")
	ErrMemberSuspended   = errors.New("MEMBER_SUSPENDED")
	ErrLoanLimitExceeded = errors.New("LOAN_LIMIT_EXCEEDED")
)

// internal/domain/book.go
package domain

type BookStatus string

const (
	BookAvailable BookStatus = "AVAILABLE"
	BookOnLoan    BookStatus = "ON_LOAN"
)

type Book struct {
	ID     BookID
	Title  string
	status BookStatus
}

func NewBook(id BookID, title string, status BookStatus) *Book {
	return &Book{ID: id, Title: title, status: status}
}

func (b *Book) Status() BookStatus { return b.status }

func (b *Book) MarkAsLent() error {
	if b.status != BookAvailable {
		return ErrBookNotAvailable // R4
	}
	b.status = BookOnLoan
	return nil
}

// internal/app/ports.go
package app

import (
	"context"
	"time"

	"example.com/library/internal/domain"
)

type LoanRepository interface {
	FindOpenByMember(ctx context.Context, id domain.MemberID) ([]*domain.Loan, error)
	Save(ctx context.Context, loan *domain.Loan) error
	NextID() domain.LoanID
}

type Clock interface{ Now() time.Time }

type UnitOfWork interface {
	Run(ctx context.Context, fn func(ctx context.Context) error) error
}

// internal/app/borrowbook.go
package app

type BorrowBookInput struct{ MemberID, BookID string }
type BorrowBookOutput struct {
	LoanID  string
	DueDate time.Time
}

type BorrowBook struct {
	Members  MemberRepository
	Books    BookRepository
	Loans    LoanRepository
	Clock    Clock
	Notifier Notifier
	UoW      UnitOfWork
}

func (uc *BorrowBook) Execute(ctx context.Context, in BorrowBookInput) (BorrowBookOutput, error) {
	now := uc.Clock.Now()

	member, err := uc.Members.FindByID(ctx, domain.MemberID(in.MemberID))
	if err != nil {
		return BorrowBookOutput{}, err // 找不到時 repository 回傳 app.ErrMemberNotFound
	}
	book, err := uc.Books.FindByID(ctx, domain.BookID(in.BookID))
	if err != nil {
		return BorrowBookOutput{}, err
	}
	openLoans, err := uc.Loans.FindOpenByMember(ctx, member.ID)
	if err != nil {
		return BorrowBookOutput{}, err
	}

	if err := member.AssertCanBorrow(openLoans, now); err != nil {
		return BorrowBookOutput{}, err
	}
	if err := book.MarkAsLent(); err != nil {
		return BorrowBookOutput{}, err
	}
	loan := domain.OpenLoan(uc.Loans.NextID(), member.ID, book.ID, now)

	err = uc.UoW.Run(ctx, func(ctx context.Context) error {
		if err := uc.Books.Save(ctx, book); err != nil {
			return err
		}
		return uc.Loans.Save(ctx, loan)
	})
	if err != nil {
		return BorrowBookOutput{}, err
	}

	_ = uc.Notifier.NotifyBookBorrowed(ctx, member.ID, book.Title, loan.DueDate())
	return BorrowBookOutput{LoanID: string(loan.ID), DueDate: loan.DueDate()}, nil
}
```

錯誤對應在 HTTP adapter 中用 `errors.Is`：

```go
// internal/adapter/http/errors.go
func statusFor(err error) int {
	switch {
	case errors.Is(err, app.ErrMemberNotFound), errors.Is(err, app.ErrBookNotFound):
		return http.StatusNotFound
	case errors.Is(err, domain.ErrBookNotAvailable):
		return http.StatusConflict
	case errors.Is(err, domain.ErrMemberSuspended), errors.Is(err, domain.ErrLoanLimitExceeded):
		return http.StatusUnprocessableEntity
	default:
		return http.StatusInternalServerError
	}
}
```

## 交易的實作

`UnitOfWork.Run` 開啟 `*sql.Tx` 並放進 `ctx`；repository 先從 ctx 取 tx，沒有就用 `*sql.DB`：

```go
// internal/adapter/postgres/tx.go
type txKey struct{}

type querier interface {
	ExecContext(ctx context.Context, q string, args ...any) (sql.Result, error)
	QueryContext(ctx context.Context, q string, args ...any) (*sql.Rows, error)
}

func conn(ctx context.Context, db *sql.DB) querier {
	if tx, ok := ctx.Value(txKey{}).(*sql.Tx); ok {
		return tx
	}
	return db
}
```

## 框架整合（Gin / Echo / chi）
`*gin.Context` 只出現在 `internal/adapter/http/`。Handler 從 context 取出參數後，以 `c.Request.Context()` 傳入 use case。

## 強制依賴規則

使用 golangci-lint 的 `depguard`：

```yaml
# .golangci.yml（節錄）
linters-settings:
  depguard:
    rules:
      domain:
        files: ["**/internal/domain/**"]
        deny:
          - pkg: "example.com/library/internal/app"
          - pkg: "example.com/library/internal/adapter"
          - pkg: "database/sql"
          - pkg: "net/http"
      app:
        files: ["**/internal/app/**"]
        deny:
          - pkg: "example.com/library/internal/adapter"
          - pkg: "github.com/gin-gonic/gin"
          - pkg: "gorm.io/gorm"
          - pkg: "database/sql"
```

B. by-feature：`files` 改用萬用字元套用到所有模組；跨模組的限制（只能 import 對方根 package）depguard 較難表達，建議用 [go-arch-lint](https://github.com/fe3dback/go-arch-lint) 或在 code review 時檢查：

```yaml
      domain:
        files: ["**/internal/*/domain/**"]
        deny:
          - pkg: "database/sql"
          - pkg: "net/http"
      app:
        files: ["**/internal/*/app/**"]
        deny:
          - pkg: "github.com/gin-gonic/gin"
          - pkg: "gorm.io/gorm"
          - pkg: "database/sql"
```
