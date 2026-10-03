# TypeScript 對照

## Java → TypeScript

本 skill 的範例是 Java。轉成 TypeScript 時對照下表：

| Java 範例中的寫法 | TypeScript 慣用寫法 |
|---|---|
| Entity：`class` + `private` 欄位 + 業務方法 | 相同：`class`，欄位 `private`，以方法改變狀態 |
| Value：`final class LoanId` + `equals` | `class` + `readonly` 欄位，或 branded type：`type LoanId = string & { __brand: "LoanId" }` |
| `enum BookStatus` | 字串字面值聯集：`type BookStatus = "AVAILABLE" \| "ON_LOAN"` |
| Port：`interface` | `interface` |
| Adapter：`class X implements Port` | 相同 |
| Use case：`class` + `execute(Input)`，建構子注入 | 相同 |
| DTO：`final class` + `final` 欄位 + getter | `type` / `interface`（`readonly` 欄位） |
| `throw new XxxException()`（unchecked） | `throw new DomainError(code)`，或 `Result<T, E>` 型別（擇一並全專案一致） |
| 找不到回傳 `null` | `null` |
| 匿名類別 `new Runnable() { ... }` | 箭頭函式 `async () => { ... }` |
| `LocalDate` / `LocalDateTime` | `Date` 或 date-only 字串（`"2026-10-17"`）；需要時用 date-fns / dayjs |
| 組裝類別（`LendingModule`） | 手寫 `main.ts` 組裝，或 NestJS module 的 factory provider |

## 資料夾與檔名

```
A. by-layer                                  B. by-feature
src/                                         src/
├── domain/loan.ts                           ├── modules/lending/
├── application/                             │   ├── domain/loan.ts
│   ├── ports/loan-repository.ts             │   ├── application/
│   └── use-cases/borrow-book/borrow-book.ts │   │   ├── ports/loan-repository.ts
├── adapters/http/loan-controller.ts         │   │   └── use-cases/borrow-book/borrow-book.ts
└── main.ts                                  │   ├── adapters/http/loan-controller.ts
                                             │   ├── public-api.ts
                                             │   └── lending.module.ts   # 模組組裝
                                             ├── shared-kernel/
                                             └── main.ts
```

檔名用 kebab-case。路徑別名：A 用 `@domain/*`、`@application/*`；B 用 `@lending/*`、`@shared-kernel/*`。
以下範例 import 以 A 的別名撰寫，B 請換成模組內相對路徑或模組別名。

## 範例

```ts
// <domain>/errors.ts
export class DomainError extends Error {
  constructor(public readonly code: string) { super(code); }
}
export const BookNotAvailable = () => new DomainError("BOOK_NOT_AVAILABLE");

// <domain>/book.ts
import { BookNotAvailable } from "./errors";

export type BookStatus = "AVAILABLE" | "ON_LOAN";

export class Book {
  constructor(
    public readonly id: string,
    public readonly title: string,
    private _status: BookStatus,
  ) {}

  get status() { return this._status; }

  markAsLent(): void {
    if (this._status !== "AVAILABLE") throw BookNotAvailable(); // R4
    this._status = "ON_LOAN";
  }
}

// <application>/ports/clock.ts
export interface Clock { now(): Date; }

// <application>/ports/loan-repository.ts
import { Loan } from "@domain/loan";
export interface LoanRepository {
  findOpenByMember(memberId: string): Promise<Loan[]>;
  save(loan: Loan): Promise<void>;
  nextId(): string;
}

// <application>/use-cases/borrow-book/borrow-book.ts
import { Loan } from "@domain/loan";
import type { MemberRepository, BookRepository, LoanRepository, Clock, Notifier, UnitOfWork } from "@application/ports";
import { MemberNotFound, BookNotFound } from "@application/errors";

export type BorrowBookInput = { memberId: string; bookId: string };
export type BorrowBookOutput = { loanId: string; dueDate: Date };

export class BorrowBook {
  constructor(
    private readonly members: MemberRepository,
    private readonly books: BookRepository,
    private readonly loans: LoanRepository,
    private readonly clock: Clock,
    private readonly notifier: Notifier,
    private readonly uow: UnitOfWork,
  ) {}

  async execute(input: BorrowBookInput): Promise<BorrowBookOutput> {
    const now = this.clock.now();
    const member = await this.members.findById(input.memberId);
    if (!member) throw MemberNotFound();
    const book = await this.books.findById(input.bookId);
    if (!book) throw BookNotFound();
    const openLoans = await this.loans.findOpenByMember(member.id);

    member.assertCanBorrow(openLoans, now);
    book.markAsLent();
    const loan = Loan.open(this.loans.nextId(), member.id, book.id, now);

    await this.uow.run(async () => {
      await this.books.save(book);
      await this.loans.save(loan);
    });

    await this.notifier.notifyBookBorrowed({ email: member.email, name: member.name }, book.title, loan.dueDate);
    return { loanId: loan.id, dueDate: loan.dueDate };
  }
}
```

## 框架整合

### Express / Fastify
Controller 是普通類別或函式，在 `main.ts` 中組裝並註冊路由。

### NestJS
NestJS 的 DI 很方便，但**裝飾器不可進入內層**：
- `domain/`、`application/` 中**不使用** `@Injectable()`
- 在 adapters 或 infrastructure 的 module 中用 factory provider 組裝：

```ts
// <infrastructure>/lending.module.ts
@Module({
  controllers: [LoanController],
  providers: [
    { provide: "LoanRepository", useClass: PrismaLoanRepository },
    { provide: "Clock", useClass: SystemClock },
    // ...
    {
      provide: BorrowBook,
      useFactory: (m, b, l, c, n, u) => new BorrowBook(m, b, l, c, n, u),
      inject: ["MemberRepository", "BookRepository", "LoanRepository", "Clock", "Notifier", "UnitOfWork"],
    },
  ],
})
export class LendingModule {}
```

### Prisma / TypeORM
Prisma 產生的型別、TypeORM 的 `@Entity` 類別都屬於 `adapters/persistence/`，必須以 mapper 轉為 domain entity。

### 交易的實作
用 `AsyncLocalStorage` 保存目前交易，repository 從中取得 transaction client；或讓 `UnitOfWork.run` 把 transactional repositories 當參數傳給 callback。

## 強制依賴規則

使用 [dependency-cruiser](https://github.com/sverweij/dependency-cruiser) 或 `eslint-plugin-boundaries`：

```js
// A. by-layer —— .dependency-cruiser.js（節錄）
forbidden: [
  { name: "domain-is-pure", from: { path: "^src/domain" },
    to: { path: "^src/(application|adapters|infrastructure)|node_modules" } },
  { name: "application-no-outer", from: { path: "^src/application" },
    to: { path: "^src/(adapters|infrastructure)" } },
  { name: "application-no-frameworks", from: { path: "^src/application" },
    to: { path: "node_modules/(express|@nestjs|prisma|@prisma|typeorm|fastify)" } },
]
```

```js
// B. by-feature —— 用 group matching（$1 = 同一個模組）
forbidden: [
  { name: "domain-is-pure", from: { path: "^src/modules/([^/]+)/domain" },
    to: { path: "^src/modules/[^/]+/(application|adapters)|^src/infrastructure|node_modules" } },
  { name: "application-no-outer", from: { path: "^src/modules/([^/]+)/application" },
    to: { path: "^src/modules/[^/]+/adapters|^src/infrastructure" } },
  { name: "modules-only-via-public-api", from: { path: "^src/modules/([^/]+)/" },
    to: { path: "^src/modules/[^/]+/", pathNot: ["^src/modules/$1/", "^src/modules/[^/]+/public-api"] } },
]
```
