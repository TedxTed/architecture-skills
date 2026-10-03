# 前端專案（Vue / React 等）

> 只在專案形態為「純前端」，或全端專案想在前端也分層時才讀本文件。

## 先判斷：前端需要分層嗎？

| 情況 | 建議 |
|---|---|
| 業務規則都在後端，前端只顯示資料、送出表單 | **不需要完整分層**。只要把「呼叫 API」集中在 `src/api/`，元件不直接 `fetch`。記一條 `[路徑]` 慣例 |
| 前端有自己的業務規則（購物車計價、離線編輯、表單之間的複雜規則） | 套用本 skill，規則放 domain |
| 純前端、沒有後端（資料存在瀏覽器） | 套用本 skill，儲存走 port |

**不要把後端已有的規則在前端再寫一次**。前端可以做「即時提示」等 UX 驗證，但以後端回應為準。

## 四層在前端的對應

| 層 | 前端放什麼 | 範例 |
|---|---|---|
| Entities `<domain>` | 純 TypeScript 的規則，不 import Vue / React | `Cart.addItem()`、`Cart.total()` |
| Use Cases `<application>` | 操作流程；ports 定義需要的外部能力 | `AddToCart`、`CartRepository` port、`ProductApi` port |
| Interface Adapters `<adapters>` | **Driving**：元件、composable / hook、store；**Driven**：API client、localStorage 儲存 | `CartView.vue`、`useCart()`、`HttpProductApi`、`LocalStorageCartRepository` |
| Frameworks & Drivers | 建立 app、路由、注入 use case | `main.ts`（`app.provide(...)`） |

### 狀態管理（Pinia / Redux / Zustand）放哪？

Store 屬於 **adapter**：它是「畫面狀態」的容器，呼叫 use case 並保存結果給元件使用。
業務規則不寫在 store 的 action 裡，而是在 entity；store 只負責呼叫 use case、更新畫面狀態。

## 目錄（by-feature）

```
src/
├── modules/
│   └── cart/
│       ├── domain/cart.ts
│       ├── application/
│       │   ├── ports/cart-repository.ts
│       │   └── use-cases/add-to-cart.ts
│       └── adapters/
│           ├── ui/CartView.vue          # driving：元件
│           ├── ui/use-cart.ts           # driving：composable / hook
│           ├── storage/local-storage-cart-repository.ts   # driven
│           └── api/http-product-api.ts  # driven
├── shared_kernel/
└── main.ts                              # 組裝並 provide use cases
```

## 「CSV 優先」在前端的對應

瀏覽器不能直接寫檔案，所以改成**先用瀏覽器內的儲存**，確認後再接真正的 API：

| 階段 | Driven adapter |
|---|---|
| 1. 先做 | `LocalStorageXxxRepository`（或 in-memory），資料存在瀏覽器 |
| 2. 使用者確認行為後 | `HttpXxxRepository`，呼叫後端 API |

兩者實作同一個 port，元件與 use case 不需要改。

## 範例程式碼

前端不是 Java，以下用 **TypeScript + Vue 3** 示範（React 的 hook 寫法對應 composable）。後端仍依本 skill 的 Java 範例。

```ts
// FILE: <domain>/cart.ts           （純 TypeScript，不 import vue）
export class DomainError extends Error {
  constructor(public readonly code: string) { super(code) }
}

export type CartItem = { productId: string; price: number; qty: number }

export class Cart {
  constructor(private readonly items: CartItem[] = []) {}

  addItem(productId: string, price: number, qty: number): void {
    if (qty <= 0) throw new DomainError('INVALID_QUANTITY')
    const existing = this.items.find((i) => i.productId === productId)
    if (existing) existing.qty += qty
    else this.items.push({ productId, price, qty })
  }

  total(): number {
    return this.items.reduce((sum, i) => sum + i.price * i.qty, 0)
  }

  toItems(): CartItem[] { return this.items.map((i) => ({ ...i })) }
}

// FILE: <application>/ports/cart-repository.ts
export interface CartRepository {
  load(): Cart
  save(cart: Cart): void
}

// FILE: <application>/use-cases/add-to-cart.ts
export class AddToCart {
  constructor(private readonly carts: CartRepository) {}

  execute(input: { productId: string; price: number; qty: number }): { total: number } {
    const cart = this.carts.load()
    cart.addItem(input.productId, input.price, input.qty)
    this.carts.save(cart)
    return { total: cart.total() }
  }
}

// FILE: <adapters>/storage/local-storage-cart-repository.ts     （driven）
export class LocalStorageCartRepository implements CartRepository {
  load(): Cart {
    const raw = localStorage.getItem('cart')
    return new Cart(raw ? (JSON.parse(raw) as CartItem[]) : [])
  }
  save(cart: Cart): void {
    localStorage.setItem('cart', JSON.stringify(cart.toItems()))
  }
}

// FILE: <adapters>/ui/use-cart.ts      （driving：composable）
import { inject, ref } from 'vue'

const MESSAGES: Record<string, string> = { INVALID_QUANTITY: '數量必須大於 0' }   // 錯誤碼 → 畫面文字

export function useCart() {
  const addToCart = inject<AddToCart>('addToCart')!          // 由 main.ts 提供
  const total = ref(0)
  const error = ref<string | null>(null)

  function add(productId: string, price: number, qty: number) {
    try {
      total.value = addToCart.execute({ productId, price, qty }).total
      error.value = null
    } catch (e) {
      error.value = e instanceof DomainError ? MESSAGES[e.code] ?? e.code : '發生錯誤'
    }
  }
  return { total, error, add }
}

// FILE: <main>                          （src/main.ts）
const app = createApp(App)
app.provide('addToCart', new AddToCart(new LocalStorageCartRepository()))
app.mount('#app')
```

## 規則

| 必須 | 禁止 |
|---|---|
| 元件透過 composable / store 呼叫 use case | 元件直接 `fetch`、直接讀寫 `localStorage` |
| domain 是純 TypeScript | domain import `vue`、`react`、`pinia` |
| 錯誤碼 → 畫面文字的轉換在 adapter（UI） | domain / use case 回傳畫面用的中文訊息 |
| use case 在 `main` 組裝後以 provide / context 注入 | 元件內 `new` repository |
