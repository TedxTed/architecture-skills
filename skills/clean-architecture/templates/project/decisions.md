# 模板：判斷紀錄（decisions.md）

> AI 指引：把下方 `---` 之間的內容存為使用者專案的 `docs/architecture/decisions.md`（UTF-8）。初始只放標頭。

---

# 判斷紀錄

> 已經推理過的結論。AI **不要整份讀**：要做判斷前，先用關鍵字搜尋標題列（`^## DEC-`），
> 找到相關條目就**直接採用結論，不重新推理**；找不到才推理，推理完記下來。
> 每條 4 行以內：標題寫結論，內文寫理由與否決的方案。與 conventions.md 的差別：
> conventions 是「全專案慣例」，這裡是「某個具體問題的結論」。

<!-- 標題格式：## DEC-編號 [模組][主題…] 結論一句話
主題標籤：[放置] [取捨] [否決] [調查] [port] [交易] [錯誤] [模組邊界] [效能] 或 entity 名稱

範例：
## DEC-001 [lending][Loan][放置] 續借次數上限放在 Loan.renew()，不放 Member
- 理由：上限屬於單筆借閱的規則，與會員等級無關
- 否決：放 RenewLoan use case（會變成貧血模型）
- 2026-10-03｜相關檔案：src/lending/domain/loan.ts

## DEC-002 [lending][調查] 寄信失敗不影響借書結果
- 結論：Notifier 失敗只記 log，不 rollback；使用者確認過
- 2026-10-03｜相關檔案：src/lending/application/use-cases/borrow-book.ts
-->

---
