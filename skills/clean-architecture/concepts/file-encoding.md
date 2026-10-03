# 檔案編碼：非 UTF-8 專案（Big5 / CP950）

**只在專案的 card.md 標示編碼不是 UTF-8 時才需要讀本文件。**

## 為什麼要特別處理

| 風險 | 後果 |
|---|---|
| AI 的寫檔工具通常以 UTF-8 寫入 | Big5 專案的檔案變成混合編碼，編譯器或 IDE 顯示亂碼 |
| 以 UTF-8 讀取 Big5 檔案 | AI 看到亂碼，再依亂碼修改，原本的中文被破壞 |
| emoji、`✅`、簡體字、部分罕用字不在 Big5 中 | 轉換失敗，或被悄悄換成 `?` |

## 名詞

| 名稱 | 說明 | 何時用 |
|---|---|---|
| Big5 | 原始標準 | 一般不直接用這個名稱 |
| CP950 / MS950 | 微軟的 Big5 擴充，Windows 繁中預設 | **台灣 Windows 專案預設用這個** |
| Big5-HKSCS | 香港擴充字集 | 香港專案 |

以下以 CP950 表示。

---

## AI 的規則

1. **讀**：用 CP950 解碼後再閱讀。若看到亂碼，**停止修改**，先用下方指令轉換後再讀
2. **寫新檔**：寫完立刻轉成 CP950；或直接用指令以 CP950 寫入
3. **改既有檔**：CP950 → UTF-8 暫存檔 → 修改 → 轉回 CP950 → `git diff` 確認只有預期的行變動
4. **字元限制**：程式碼、註解、字串中**不使用** emoji 與 `✅ ❌ ⚡ ★` 等符號，改用 `[OK]`、`[NG]`、`*`。避免簡體字與罕用字
5. **嚴格轉換**：轉換時遇到無法表示的字元必須**報錯**，不可以悄悄替換成 `?`
6. **保留原狀**：沿用既有檔案的換行（CRLF / LF）與 BOM

## 例外：專案記憶一律 UTF-8

`docs/architecture/`（card.md、conventions.md、debt.md）**固定使用 UTF-8**，不跟隨專案編碼。
這些檔案是給 AI 讀的，大部分 AI 工具以 UTF-8 讀檔；存成 Big5 會讓每次任務都讀到亂碼。

## 指令

### Git Bash（iconv）

```bash
# 讀：CP950 → UTF-8 暫存檔
iconv -f CP950 -t UTF-8 src/lending/loan.cs > /tmp/loan.cs

# 寫回：UTF-8 → CP950（沒有 //TRANSLIT，遇到無法表示的字元會報錯，這是要的行為）
iconv -f UTF-8 -t CP950 /tmp/loan.cs > src/lending/loan.cs
```

### PowerShell 5.1（.NET，嚴格模式）

```powershell
$enc = [System.Text.Encoding]::GetEncoding(950,
    [System.Text.EncoderFallback]::ExceptionFallback,
    [System.Text.DecoderFallback]::ExceptionFallback)

# 讀
$text = [System.IO.File]::ReadAllText($path, $enc)

# 寫（遇到無法表示的字元會丟出例外）
[System.IO.File]::WriteAllText($path, $text, $enc)
```

> 不要用 `Set-Content -Encoding Default`。它依系統語系決定編碼，而且遇到無法表示的字元會悄悄換成 `?`。

### Python

```python
text = open(path, encoding="cp950", errors="strict").read()
open(path, "w", encoding="cp950", errors="strict", newline="").write(text)
```

### 驗證檔案能以 CP950 解碼

```bash
iconv -f CP950 -t UTF-8 file > /dev/null && echo OK
```

## 程式中讀寫 CP950（例如 CSV adapter）

| 語言 | 做法 |
|---|---|
| TypeScript / Node | Node 內建不支援 → `iconv-lite`：`iconv.decode(buffer, "cp950")` / `iconv.encode(text, "cp950")` |
| Python | 內建：`open(path, encoding="cp950")` |
| Go | `golang.org/x/text/encoding/traditionalchinese.Big5` 搭配 `transform.NewReader` |
| Java | `Charset.forName("MS950")` |
| C# | .NET Core 以上需先 `Encoding.RegisterProvider(CodePagesEncodingProvider.Instance)`，再 `Encoding.GetEncoding(950)` |

編碼是 **adapter 的細節**：由 `CsvStore` 依設定處理，domain 與 application 只看到一般字串。

```
// FILE: <main>
store ← CsvStore(config.dataDir, encoding: config.csvEncoding)    // "utf-8" | "utf-8-bom" | "cp950"
```

## 混用編碼的專案

若不同資料夾使用不同編碼，在 conventions.md 逐一列出：

```markdown
## C-00X [編碼] 混用編碼
- 預設：全專案 UTF-8
- 本專案：`legacy/` 為 CP950；`src/`、`tests/` 為 UTF-8；CSV 資料檔為 CP950
- 依據：使用者訪談（日期）
```

新檔案一律依所在資料夾的編碼。**不要主動轉換既有檔案的編碼**；若使用者想統一成 UTF-8，另開任務處理並記入 debt.md。
