# Workflow：新增 / 替換 Adapter

適用：換資料庫、加第三方服務、加新入口（CLI、Message Queue、gRPC）。
**這是 Clean Architecture 回本的時刻：domain 與 application 一行都不用改。**

---

## 情境 A：替換 Driven Adapter（例：PostgreSQL → MongoDB）

### 步驟

1. **找到 port**：`application/ports/loan_repository.x`。不修改它。
2. **新增實作**：`adapters/persistence/mongo/mongo_loan_repository.x`
3. **新增 mapper**：Entity ⇄ Mongo document
4. **跑同一套 repository contract test**（見下方）
5. **在 main 切換**：`loans ← MongoLoanRepository(mongo)`
6. 舊的 adapter 確認無用後再刪除

```
// FILE: <adapters>/persistence/mongo/mongo_loan_repository.x
ADAPTER MongoLoanRepository IMPLEMENTS LoanRepository
  DEPENDS ON collection: MongoCollection

  FUNCTION findOpenByMember(memberId)
    docs ← collection.find({ memberId: memberId.value, returnedAt: null })
    RETURN docs.map(docToEntity)

  FUNCTION save(loan)
    collection.replaceOne({ _id: loan.id.value }, entityToDoc(loan), upsert: true)
```

### Repository Contract Test（強烈建議）

同一組測試對所有實作都跑一次，保證行為一致：

```
// FILE: <tests>/contracts/loan_repository_contract.x
CONTRACT_TEST LoanRepositoryContract(createRepo: Function -> LoanRepository)
  TEST "save 後 findById 取得相同內容"
  TEST "findOpenByMember 不包含已歸還的借閱"
  TEST "save 同一 id 兩次為更新而非新增"

RUN LoanRepositoryContract WITH () -> InMemoryLoanRepository()
RUN LoanRepositoryContract WITH () -> SqlLoanRepository(testDb)
RUN LoanRepositoryContract WITH () -> MongoLoanRepository(testMongo)
```

✅ **檢查點**：`git diff` 中 `domain/` 與 `application/` 沒有任何變動。若有，代表原本的 port 洩漏了技術細節，先修 port。

---

## 情境 B：新增外部服務（例：借書後也要發 LINE 通知）

1. **確認 port 是否已足夠**：`Notifier.notifyBookBorrowed(...)` 已描述「要通知」，不描述「用什麼通知」→ 不改 port
2. **新增實作**：`adapters/notification/line_notifier.x`
3. **要同時發 email 與 LINE？** 用組合，不改 use case：

```
// FILE: <adapters>/notification/composite_notifier.x
ADAPTER CompositeNotifier IMPLEMENTS Notifier
  DEPENDS ON notifiers: List<Notifier>
  FUNCTION notifyBookBorrowed(memberId, title, dueDate)
    FOR n IN notifiers
      n.notifyBookBorrowed(memberId, title, dueDate)

// FILE: <main>
notifier ← CompositeNotifier([SmtpNotifier(smtp, members), LineNotifier(lineApi, members)])
```

4. **第三方 SDK 只能出現在 adapter 內**，連它的錯誤型別也不能往外拋，要翻譯：

```
ADAPTER LineNotifier IMPLEMENTS Notifier
  FUNCTION notifyBookBorrowed(...)
    TRY lineSdk.pushMessage(...)
    ON ERROR LineApiError e
      log.warn(e)
      FAIL NotificationFailed          // application 定義的錯誤，或依需求吞掉
```

---

## 情境 C：新增 Driving Adapter（例：加 CLI 管理工具）

1. **重用既有 use case**，不要為 CLI 寫一份新的邏輯
2. 新增 `adapters/cli/admin_commands.x`
3. 新增 CLI 的 entry point（`main_cli.x` 或在 main 中依參數分流），組裝方式與 HTTP 相同

```
// FILE: <adapters>/cli/admin_commands.x
ADAPTER AdminCli
  DEPENDS ON returnBook: ReturnBook

  // 用法：library-admin return <loanId>
  FUNCTION run(args: List<String>)
    IF args[0] == "return"
      result ← returnBook.execute(ReturnBookInput(loanId: args[1]))
      ON ERROR e → print("失敗：" + e.code); exit(1)
      print("歸還成功，罰金：" + result.fine)
```

✅ **檢查點**：HTTP 與 CLI 呼叫的是**同一個** `ReturnBook` 實例類別；兩者之間沒有複製貼上的邏輯。

---

## 情境 D：Message Queue 消費者

與情境 C 相同，只是輸入來源是訊息：

```
ADAPTER BookDamagedConsumer
  DEPENDS ON markBookDamaged: MarkBookDamaged
  FUNCTION onMessage(msg: QueueMessage)
    input ← MarkBookDamagedInput(bookId: msg.payload.bookId)
    markBookDamaged.execute(input)
    ON ERROR e → msg.nack() ELSE msg.ack()       // ack/nack 是 adapter 的責任
```
