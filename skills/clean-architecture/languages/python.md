# Python 對照

## Pseudocode → Python

| Pseudocode | Python 慣用寫法 |
|---|---|
| `ENTITY` | 一般 `class`，或 `@dataclass`（非 frozen） |
| `VALUE` | `@dataclass(frozen=True)` 或 `NewType` |
| `ENUM` | `enum.Enum` / `StrEnum` |
| `PORT` | `typing.Protocol`（推薦，結構型別）或 `abc.ABC` |
| `ADAPTER ... IMPLEMENTS` | 實作 Protocol 的方法即可（不必繼承） |
| `USE_CASE` | `class` + `execute()` 或 `__call__()` |
| `DTO` | `@dataclass(frozen=True)` |
| `FAIL` | `raise DomainError(...)` |
| `Nothing` | `None` |

> **Pydantic 放哪？** Pydantic model 屬於 adapter 層（HTTP schema）。domain / application 用標準庫 `dataclass`，避免依賴第三方套件。

## 資料夾

```
A. by-layer                                    B. by-feature
src/library/                                   src/library/
├── domain/loan.py                             ├── modules/lending/
├── application/                               │   ├── domain/loan.py
│   ├── ports.py                               │   ├── application/
│   └── use_cases/borrow_book.py               │   │   ├── ports.py
├── adapters/                                  │   │   └── use_cases/borrow_book.py
│   ├── http/routes.py                         │   ├── adapters/http/routes.py
│   └── persistence/sqlalchemy_loan_repo.py    │   ├── public_api.py
└── main.py                                    │   └── module.py        # 模組組裝
                                               ├── shared_kernel/
                                               └── main.py
```

以下範例以 A 的 import 路徑撰寫；B 請把 `library.domain` 換成 `library.modules.lending.domain`，以此類推。

## 範例

```python
# src/library/domain/errors.py
class DomainError(Exception):
    code: str = "DOMAIN_ERROR"

class BookNotAvailable(DomainError):
    code = "BOOK_NOT_AVAILABLE"

# src/library/domain/book.py
from dataclasses import dataclass
from enum import Enum
from .errors import BookNotAvailable

class BookStatus(Enum):
    AVAILABLE = "AVAILABLE"
    ON_LOAN = "ON_LOAN"

@dataclass
class Book:
    id: str
    title: str
    status: BookStatus

    def mark_as_lent(self) -> None:
        if self.status is not BookStatus.AVAILABLE:
            raise BookNotAvailable()  # R4
        self.status = BookStatus.ON_LOAN

# src/library/application/ports.py
from datetime import datetime
from typing import Protocol, Callable
from library.domain.loan import Loan

class Clock(Protocol):
    def now(self) -> datetime: ...

class LoanRepository(Protocol):
    def find_open_by_member(self, member_id: str) -> list[Loan]: ...
    def save(self, loan: Loan) -> None: ...
    def next_id(self) -> str: ...

class UnitOfWork(Protocol):
    def run(self, work: Callable[[], None]) -> None: ...

# src/library/application/use_cases/borrow_book.py
from dataclasses import dataclass
from datetime import date
from library.domain.loan import Loan
from library.application.errors import MemberNotFound, BookNotFound
from library.application.ports import (
    MemberRepository, BookRepository, LoanRepository, Clock, Notifier, UnitOfWork,
)

@dataclass(frozen=True)
class BorrowBookInput:
    member_id: str
    book_id: str

@dataclass(frozen=True)
class BorrowBookOutput:
    loan_id: str
    due_date: date

class BorrowBook:
    def __init__(self, members: MemberRepository, books: BookRepository,
                 loans: LoanRepository, clock: Clock, notifier: Notifier,
                 uow: UnitOfWork) -> None:
        self._members, self._books, self._loans = members, books, loans
        self._clock, self._notifier, self._uow = clock, notifier, uow

    def execute(self, input: BorrowBookInput) -> BorrowBookOutput:
        now = self._clock.now()
        member = self._members.find_by_id(input.member_id)
        if member is None:
            raise MemberNotFound()
        book = self._books.find_by_id(input.book_id)
        if book is None:
            raise BookNotFound()
        open_loans = self._loans.find_open_by_member(member.id)

        member.assert_can_borrow(open_loans, now)
        book.mark_as_lent()
        loan = Loan.open(self._loans.next_id(), member.id, book.id, now)

        def work() -> None:
            self._books.save(book)
            self._loans.save(loan)
        self._uow.run(work)

        self._notifier.notify_book_borrowed(member.id, book.title, loan.due_date)
        return BorrowBookOutput(loan_id=loan.id, due_date=loan.due_date)
```

## 框架整合

### FastAPI
- Pydantic request / response model 放在 `adapters/http/schemas.py`
- `Depends()` 只用在 adapter 層取得 use case 實例；use case 由 `main.py` 建立

```python
# src/library/adapters/http/routes.py
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

class BorrowRequest(BaseModel):
    member_id: str
    book_id: str

def build_router(borrow_book: BorrowBook) -> APIRouter:
    router = APIRouter()

    @router.post("/loans", status_code=201)
    def borrow(req: BorrowRequest):
        try:
            out = borrow_book.execute(BorrowBookInput(req.member_id, req.book_id))
        except (DomainError, AppError) as e:
            status, code = to_http_status(e)
            raise HTTPException(status_code=status, detail=code)
        return {"loanId": out.loan_id, "dueDate": out.due_date.isoformat()}

    return router
```

### Django
Django ORM model（`models.Model`）屬於 adapter。Django app 結構可以放在 `adapters/django_app/`，`views.py` 就是 controller。
若團隊不願脫離 Django 慣例，至少保證 `domain/` 與 `application/` 不 import `django`。

### SQLAlchemy
使用 **imperative mapping** 可以把 domain dataclass 直接映射到資料表而不汙染 domain；或維持獨立 ORM model + mapper（較明確，推薦給初學者）。

## 強制依賴規則

使用 [import-linter](https://github.com/seddonym/import-linter)：

```ini
# .importlinter
[importlinter]
root_package = library

[importlinter:contract:layers]
name = Clean Architecture layers
type = layers
layers =
    library.main
    library.adapters
    library.application
    library.domain

[importlinter:contract:no-frameworks-inside]
name = Inner layers are framework-free
type = forbidden
source_modules =
    library.domain
    library.application
forbidden_modules =
    fastapi
    django
    sqlalchemy
    pydantic
```

B. by-feature：用 `containers` 讓同一組層級規則套用到每個模組，再用 `independence` 隔離模組：

```ini
[importlinter:contract:module-layers]
name = Layers inside each module
type = layers
containers =
    library.modules.lending
    library.modules.catalog
    library.modules.membership
layers =
    module
    adapters
    application
    domain

[importlinter:contract:modules-independent]
name = Modules talk only via public_api
type = independence
modules =
    library.modules.lending
    library.modules.catalog
    library.modules.membership
ignore_imports =
    library.modules.*.adapters.** -> library.modules.*.public_api
```

> `no-frameworks-inside` 的 `source_modules` 在 B 中改列 `library.modules.lending.domain`、`library.modules.lending.application`……每個模組各一組。
