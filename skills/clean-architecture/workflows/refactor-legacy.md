# Workflow：把既有程式重構成 Clean Architecture

適用：controller 裡塞滿 SQL 與業務判斷的「大泥球」程式。
**原則：漸進式。一次只搬一個功能，每一步都能跑、測試都能過。不要一次重寫。**

---

## 起點：典型的大泥球

```
// FILE: src/routes/loans.x   （重構前）
ROUTE POST /loans
  member ← db.query("SELECT * FROM members WHERE id = ?", req.body.memberId)
  IF member == null RETURN 404
  IF member.suspended RETURN 422 "停權"
  count ← db.query("SELECT COUNT(*) FROM loans WHERE member_id = ? AND returned_at IS NULL", ...)
  limit ← member.tier == "VIP" ? 5 : 3
  IF count >= limit RETURN 422 "超過上限"
  book ← db.query("SELECT * FROM books WHERE id = ?", req.body.bookId)
  IF book.status != "AVAILABLE" RETURN 409
  db.begin()
  db.exec("UPDATE books SET status = 'ON_LOAN' WHERE id = ?", ...)
  due ← now() + 14 days
  db.exec("INSERT INTO loans ...", ..., due)
  db.commit()
  mailer.send(member.email, "借書成功")
  RETURN 201 { dueDate: due }
```

---

## Step 0 — 安全網：先寫特徵測試（Characterization Test）

在動任何程式碼之前，用 HTTP 層的測試**鎖定現在的行為**（包括怪異的行為）。

```
TEST "POST /loans 成功時回 201 與到期日"
TEST "停權會員回 422"
TEST "書已借出回 409"
```

✅ **檢查點**：測試全過。之後每一步都要維持全過。

---

## Step 1 — 標記：在原檔案中標出每一行屬於哪一層

```
  member ← db.query(...)                    // [ADAPTER-persistence]
  IF member == null RETURN 404              // [USE CASE] 流程 + [ADAPTER-http] 狀態碼
  IF member.suspended RETURN 422            // [DOMAIN] R1
  limit ← member.tier == "VIP" ? 5 : 3      // [DOMAIN] R2
  due ← now() + 14 days                     // [DOMAIN] R5 + [PORT] Clock
  mailer.send(...)                          // [USE CASE] A1 + [ADAPTER] SMTP
```

這一步不改程式，只是讓你與使用者看見結構。

---

## Step 2 — 抽出 Domain（風險最低，收益最高）

1. 建立 `domain/member.x`、`domain/book.x`、`domain/loan.x`
2. 把標記為 `[DOMAIN]` 的邏輯搬成 entity 方法
3. 原 route 改成：查出 row → 轉成 entity → 呼叫方法
4. 為 entity 寫單元測試

```
  memberRow ← db.query(...)
  member ← toMemberEntity(memberRow)              // 暫時寫在 route 檔案中
  member.assertCanBorrow(openLoans, now())        // 規則已搬走
```

✅ **檢查點**：特徵測試仍全過；domain 測試新增並通過。

---

## Step 3 — 抽出 Ports 與 Adapters

1. 為每種 `db.query` 的用途定義 port（`MemberRepository.findById`…）
2. 把 SQL 搬進 `adapters/persistence/sql_*_repository.x`
3. `now()` → `Clock` port；`mailer` → `Notifier` port
4. route 改為呼叫 repository

✅ **檢查點**：route 檔案中不再出現 SQL 字串。

---

## Step 4 — 抽出 Use Case

1. 建立 `application/use_cases/borrow_book/`
2. 把 route 中剩下的「流程」搬進去
3. route 只剩：解析 request → 呼叫 use case → 轉 response（它現在就是 controller）
4. 新增 use case 測試（in-memory fakes）

✅ **檢查點**：route / controller 少於約 20 行，沒有 `IF` 業務判斷。

---

## Step 5 — 搬到 Composition Root

1. 把 `new SqlXxxRepository(db)` 等建立動作從 route 搬到 `main`
2. 加入依賴規則檢查工具（見 `languages/` 的「強制依賴規則」）

---

## Step 6 — 下一個功能

重複 Step 0–5。**第二個功能會快很多**，因為 entity、port、repository 大多已存在。

---

## 重構順序建議

| 優先 | 挑選標準 |
|---|---|
| 1 | 業務規則最多、bug 最多、最常改的功能 |
| 2 | 即將要大改的功能（順便重構） |
| 最後 | 純 CRUD、很少動的功能（可能永遠不需要重構） |

## 與使用者溝通

- 每完成一個 Step 回報一次，讓使用者可以隨時停下
- 明確告知：「這一步只搬移，不改變行為」
- 若發現既有行為看起來是 bug，**先記錄、不要順手修**，等重構完成後另外處理
