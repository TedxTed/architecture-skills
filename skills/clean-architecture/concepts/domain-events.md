# Domain Event（領域事件）

> 定義在：`<domain>/event/`｜發佈：use case 透過 `EventPublisher` port｜處理：訂閱方的 application 層

## 職責

記錄「**業務上已經發生的事**」，讓其他部分在不直接呼叫的情況下做出反應。
例：借書成功（`BookBorrowed`）→ 寄通知、更新統計、館藏模組更新熱門書排行。

## 何時用 / 何時不用

| 用 | 不用 |
|---|---|
| 一件事發生後，有**多個**、**彼此無關**的後續動作 | 只有一個後續動作，且在同一模組內 → use case 直接呼叫 port 即可 |
| 模組之間需要鬆散耦合（by-feature） | 後續動作必須與主操作**同一交易成功或失敗** |
| 後續動作可以晚一點完成（最終一致） | 呼叫方需要立即拿到後續動作的結果 |

**小專案預設不用。** 先用 use case 直接呼叫 port；出現第二、第三個後續動作，或要跨模組時再引入。

## 遵循規則

| 必須 | 禁止 |
|---|---|
| 名稱用**過去式**：`BookBorrowed`、`LoanClosed` | `BorrowBookEvent`、`SendEmailEvent`（命令式或技術式） |
| 欄位是**不可變的基本資料**（id、日期、金額） | 事件裡放 entity 物件 |
| 發生時間、事件 id 由**參數傳入** | 事件建構子裡呼叫 `LocalDateTime.now()`、`UUID.randomUUID()`（隱藏依賴） |
| Entity 只**記錄**事件，不發佈 | Entity 呼叫 event bus |
| Use case 在**交易成功後**發佈 | 交易提交前發佈（rollback 後事件已送出） |
| Handler 是 application 層的程式，透過 port 做事 | Handler 直接操作資料庫或框架 |

## 兩種事件

| | Domain Event | Integration Event |
|---|---|---|
| 範圍 | 模組內部 | 跨模組 / 跨服務 |
| 定義在 | `<domain>/event/` | 發佈方模組的 `public_api`（對外契約） |
| 欄位 | 可以用 domain 的 Value | 只用基本型別（對方不認識我的 Value） |
| 傳遞 | 同一程序內的 event bus | event bus 或 Message Queue |

小專案通常一種就夠：直接把 Domain Event 當 Integration Event 用，但欄位只放基本型別。

## 怎麼寫（步驟）

1. 在 entity 方法中，狀態改變成功後把事件加進 `pendingEvents`
2. Use case 儲存成功後，取出 entity 累積的事件，交給 `EventPublisher` port
3. 訂閱方寫一個 handler（application 層），在組裝時註冊到 bus
4. 需要「事件一定送達」時改用 Outbox（見下方）

## 範例程式碼

Java，相容 JDK 1.7。

```java
// FILE: <domain>/event/DomainEvent.java
public interface DomainEvent {
    String getEventId();
    LocalDateTime getOccurredAt();
}

// FILE: <domain>/event/BookBorrowed.java
public final class BookBorrowed implements DomainEvent {
    private final String eventId;
    private final LocalDateTime occurredAt;
    private final String loanId;
    private final String memberId;
    private final String bookId;
    private final LocalDate dueDate;

    public BookBorrowed(String eventId, LocalDateTime occurredAt, String loanId,
                        String memberId, String bookId, LocalDate dueDate) {
        this.eventId = eventId;          // 由參數傳入，不在這裡呼叫 UUID.randomUUID()
        this.occurredAt = occurredAt;    // 由參數傳入，不在這裡呼叫 LocalDateTime.now()
        this.loanId = loanId;
        this.memberId = memberId;
        this.bookId = bookId;
        this.dueDate = dueDate;
    }
    // getter 略
}

// FILE: <domain>/Loan.java    （節錄）
public class Loan {
    private final List<DomainEvent> pendingEvents = new ArrayList<DomainEvent>();

    public static Loan open(LoanId id, MemberId memberId, BookId bookId, LocalDateTime now, String eventId) {
        Loan loan = new Loan(id, memberId, bookId, now, now.toLocalDate().plusDays(LOAN_DAYS), null, 0);
        loan.pendingEvents.add(new BookBorrowed(eventId, now, id.getValue(),
                memberId.getValue(), bookId.getValue(), loan.getDueDate()));
        return loan;
    }

    // 取出後清空，避免重複發佈
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> events = new ArrayList<DomainEvent>(pendingEvents);
        pendingEvents.clear();
        return events;
    }
}

// FILE: <application>/port/EventPublisher.java
public interface EventPublisher {
    void publish(List<DomainEvent> events);
}

// FILE: <application>/usecase/borrowbook/BorrowBook.java    （節錄）
final Loan loan = Loan.open(loans.nextId(), memberId, bookId, now, ids.next());   // ids：IdGenerator port
uow.run(new Runnable() {
    @Override public void run() { books.save(book); loans.save(loan); }
});
events.publish(loan.pullEvents());              // 交易成功之後

// FILE: <application>/eventhandler/EventHandler.java
public interface EventHandler<E extends DomainEvent> {
    void handle(E event);
}

// FILE: <application>/eventhandler/SendBorrowNotification.java    （訂閱方）
public class SendBorrowNotification implements EventHandler<BookBorrowed> {
    private final MemberRepository members;
    private final BookRepository books;
    private final Notifier notifier;

    public SendBorrowNotification(MemberRepository members, BookRepository books, Notifier notifier) {
        this.members = members;
        this.books = books;
        this.notifier = notifier;
    }

    @Override
    public void handle(BookBorrowed event) {
        Member member = members.findById(new MemberId(event.getMemberId()));
        Book book = books.findById(new BookId(event.getBookId()));
        notifier.notifyBookBorrowed(new Recipient(member.getEmail(), member.getName()),
                book.getTitle(), event.getDueDate());
    }
}

// FILE: <adapters>/event/InProcessEventBus.java
public class InProcessEventBus implements EventPublisher {
    private final Map<Class<?>, List<EventHandler<DomainEvent>>> handlers =
            new HashMap<Class<?>, List<EventHandler<DomainEvent>>>();

    @SuppressWarnings("unchecked")
    public <E extends DomainEvent> void subscribe(Class<E> type, EventHandler<E> handler) {
        if (!handlers.containsKey(type)) {
            handlers.put(type, new ArrayList<EventHandler<DomainEvent>>());
        }
        handlers.get(type).add((EventHandler<DomainEvent>) (EventHandler<?>) handler);
    }

    @Override
    public void publish(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            List<EventHandler<DomainEvent>> list = handlers.get(event.getClass());
            if (list == null) {
                continue;
            }
            for (EventHandler<DomainEvent> handler : list) {
                try {
                    handler.handle(event);
                } catch (RuntimeException e) {
                    LoggerFactory.getLogger(InProcessEventBus.class).error("事件處理失敗", e);   // 一個失敗不影響其他
                }
            }
        }
    }
}

// FILE: <main>/LendingConfig.java    （節錄）
@Bean
public InProcessEventBus eventBus(MemberRepository members, BookRepository books, Notifier notifier) {
    InProcessEventBus bus = new InProcessEventBus();
    bus.subscribe(BookBorrowed.class, new SendBorrowNotification(members, books, notifier));
    return bus;
}
```

> Spring 專案也可以用 `ApplicationEventPublisher` 實作 `EventPublisher` port；但 `@EventListener` 只能加在 adapter 的類別上，
> handler 本身（`SendBorrowNotification`）仍是 application 層的普通類別。

## 可靠性：Outbox

In-process bus 在「交易成功、發佈前程式當掉」時會遺失事件。必須保證送達時：

```
1. 交易內：把事件寫進 outbox 表（與業務資料同一交易）
2. 另一個背景程序：讀 outbox → 發佈 → 標記已送出
3. Handler 要能處理重複的事件（以 eventId 去重）
```

Outbox 的讀寫是 adapter 的事；use case 仍然只呼叫 `EventPublisher` port（此時由 `OutboxEventPublisher` 實作，在交易內寫入）。

## 與各層的配合

| 層 | 角色 |
|---|---|
| Entities | 定義事件型別；在狀態改變時記錄事件（不發佈） |
| Use Cases | 交易成功後取出事件，交給 `EventPublisher` port；訂閱方的 handler 也在這層 |
| Interface Adapters | `EventPublisher` 的實作：in-process bus、MQ、outbox |
| Frameworks & Drivers | 建立 bus、註冊 handler、啟動 outbox 背景程序 |

## 自我檢查

- [ ] 事件名稱是過去式；欄位只有不可變的基本資料
- [ ] 事件的時間與 id 由參數傳入
- [ ] Entity 只記錄，use case 在交易成功後才發佈
- [ ] Handler 透過 port 做事，失敗不影響主操作
- [ ] 需要保證送達時用了 outbox，handler 能處理重複事件
