# 審查清單

用法：AI 每寫完一層、或審查使用者的程式碼時，逐項檢查。
發現違反時，回報格式：`[層] 檔案:行號 — 違反哪一條 — 建議修法`。

## 🔴 必須修（違反依賴規則）

- [ ] `domain/` 中沒有 import `application/`、`adapters/`、`infrastructure/`
- [ ] `domain/`、`application/` 中沒有 import 任何框架、ORM、HTTP、SDK、DB driver
- [ ] `domain/`、`application/` 的型別上沒有框架註解（`@Entity`、`@Column`、`@JsonProperty`、`@Injectable`…）
- [ ] Use case 的參數與回傳型別中沒有 `Request`、`Response`、`Context`、ORM model
- [ ] 只有 composition root 會建立 adapter 實例
- [ ] 模組之間（by-feature）只透過 `public_api` 互動

## 🟠 應該修（職責放錯）

**Domain**
- [ ] 業務規則在 entity / domain service 中，不在 use case 或 controller
- [ ] Entity 沒有直接呼叫 `now()`、`random()`、`uuid()`
- [ ] Entity 有行為（方法），不只是 getter / setter 的資料袋
- [ ] 方法名是業務動詞，不是 `setStatus`

**Application**
- [ ] 一個 use case 一個類別 / 函式，一個公開方法
- [ ] Use case 中的 `IF` 只做流程判斷（找不到、權限），不做業務規則判斷
- [ ] 回傳 Output DTO，不回傳 entity
- [ ] Port 介面用業務語言，參數 / 回傳是內層型別
- [ ] 副作用（通知、事件）發生在交易成功之後

**Adapters**
- [ ] Controller 只做：解析 → 呼叫 use case → 轉換回應
- [ ] 錯誤翻譯（領域錯誤 → HTTP status）集中在一處
- [ ] 外部 SDK 的錯誤型別沒有洩漏到 adapter 之外
- [ ] Repository 回傳 entity，不回傳 ORM model / row

## 🟡 建議改善

- [ ] 時間、ID 生成透過 port，測試可控制
- [ ] Domain 測試不需要任何 mock
- [ ] Use case 測試使用 in-memory fakes，不需要 DB
- [ ] 有 repository contract test
- [ ] 有自動化的依賴規則檢查（lint / arch test）
- [ ] 資料夾命名與專案既有慣例一致

## 快速自動檢查（給 AI 執行）

用搜尋工具在內層找禁止的 import。將 `<框架關鍵字>` 換成使用者的技術棧：

```
搜尋路徑：<domain>/  <application>/
搜尋樣式：import.*(adapters|infrastructure|<框架關鍵字>)
預期結果：無結果
```

常見框架關鍵字：
`express|nestjs|@nestjs|fastify|prisma|typeorm|sequelize|mongoose`（TS）、
`django|flask|fastapi|sqlalchemy|pydantic`（Python）、
`gin|echo|fiber|gorm|database/sql|net/http`（Go）、
`springframework|jakarta.persistence|javax.persistence|hibernate`（Java）
