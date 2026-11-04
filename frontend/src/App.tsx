import { useEffect, useMemo, useState } from 'react'
import { CartDrawer } from './components/CartDrawer'
import { CheckoutAccessModal } from './components/CheckoutAccessModal'
import { PaymentReturnModal } from './components/PaymentReturnModal'
import { ProductCatalog } from './components/ProductCatalog'
import type { ApiError, Cart, CartItem, CheckoutResult, OrderStatus, Product, StripeCheckoutSession } from './types/store'

const CART_KEY = 'basic-ecommerce-guest-cart'
const demoToken = import.meta.env.VITE_DEMO_API_TOKEN ?? ''

function readCart(): Cart {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(CART_KEY) ?? '{}')
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
    return Object.fromEntries(Object.entries(value).filter(([id, quantity]) => typeof id === 'string' && Number.isInteger(quantity) && (quantity as number) > 0)) as Cart
  } catch {
    return {}
  }
}

function App() {
  const [products, setProducts] = useState<Product[]>([])
  const [cart, setCart] = useState<Cart>(readCart)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [cartOpen, setCartOpen] = useState(false)
  const [imageIndexes, setImageIndexes] = useState<Record<string, number>>({})
  const [demoLoginOpen, setDemoLoginOpen] = useState(false)
  const [checkoutSubmitting, setCheckoutSubmitting] = useState(false)
  const [checkoutError, setCheckoutError] = useState('')
  const [paymentReturnMode, setPaymentReturnMode] = useState<'success' | 'cancelled' | null>(null)
  const [paymentReturnReference, setPaymentReturnReference] = useState('')
  const [paymentReturnStatus, setPaymentReturnStatus] = useState<OrderStatus | null>(null)
  const [paymentReturnChecking, setPaymentReturnChecking] = useState(false)
  const [paymentReturnError, setPaymentReturnError] = useState('')

  async function loadProducts() {
    setLoading(true)
    setError('')
    try {
      const response = await fetch('/api/products')
      if (!response.ok) throw new Error(`Catalog request failed (${response.status})`)
      const data: { products: Product[] } = await response.json()
      setProducts(data.products)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load the catalog.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void loadProducts() }, [])
  useEffect(() => { localStorage.setItem(CART_KEY, JSON.stringify(cart)) }, [cart])
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const mode = params.get('stripe')
    if (mode !== 'success' && mode !== 'cancelled') return
    setPaymentReturnMode(mode)
    setPaymentReturnReference(params.get('order') ?? '')
  }, [])

  const items = useMemo<CartItem[]>(() => products.filter(product => cart[product.id] > 0).map(product => ({ product, quantity: cart[product.id] })), [products, cart])
  const count = items.reduce((sum, item) => sum + item.quantity, 0)
  const subtotal = items.reduce((sum, item) => sum + item.product.pricePaise * item.quantity, 0)

  function changeImage(product: Product, direction: number) {
    setImageIndexes(current => ({ ...current, [product.id]: ((current[product.id] ?? 0) + direction + product.imageUrls.length) % product.imageUrls.length }))
  }

  function setQuantity(product: Product, quantity: number) {
    setCart(current => {
      const next = { ...current }
      if (quantity <= 0) delete next[product.id]
      else next[product.id] = Math.min(quantity, product.available, 10)
      return next
    })
  }

  async function submitCheckout() {
    if (!demoToken) {
      setCheckoutError('No demo token is configured. Set DEMO_API_TOKEN in .env and rebuild the frontend.')
      return
    }
    const requestItems = items.map(({ product, quantity }) => ({ productId: product.id, quantity })).sort((a, b) => a.productId.localeCompare(b.productId))
    const fingerprint = JSON.stringify(requestItems)
    const storageKey = `checkout-idempotency:${fingerprint}`
    const idempotencyKey = sessionStorage.getItem(storageKey) ?? crypto.randomUUID()
    sessionStorage.setItem(storageKey, idempotencyKey)
    setCheckoutSubmitting(true)
    setCheckoutError('')
    try {
      const response = await fetch('/api/checkout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${demoToken}`, 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify({ items: requestItems }),
      })
      const payload = await response.json() as CheckoutResult | ApiError
      if (!response.ok) throw new Error((payload as ApiError).error?.message ?? `Checkout failed (${response.status}).`)
      const checkout = payload as CheckoutResult
      const sessionResponse = await fetch(`/api/payments/${checkout.payment.id}/stripe-session`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${demoToken}` },
      })
      const sessionPayload = await sessionResponse.json() as StripeCheckoutSession | ApiError
      if (!sessionResponse.ok) throw new Error((sessionPayload as ApiError).error?.message ?? `Could not start Stripe Checkout (${sessionResponse.status}).`)
      setDemoLoginOpen(false)
      setCartOpen(false)
      window.location.assign((sessionPayload as StripeCheckoutSession).checkoutUrl)
    } catch (cause) {
      setCheckoutError(cause instanceof Error ? cause.message : 'Checkout failed. Your cart is unchanged; you can safely retry.')
    } finally {
      setCheckoutSubmitting(false)
    }
  }

  useEffect(() => {
    if (!paymentReturnMode || !paymentReturnReference) return
    let active = true
    let timeout: ReturnType<typeof setTimeout> | undefined

    async function loadPaymentStatus() {
      let finalStatus: OrderStatus | null = null
      setPaymentReturnChecking(true)
      setPaymentReturnError('')
      try {
        const attempts = paymentReturnMode === 'success' ? 20 : 1
        for (let attempt = 0; attempt < attempts; attempt += 1) {
          const response = await fetch(`/api/orders/by-reference/${paymentReturnReference}`, {
            headers: { Authorization: `Bearer ${demoToken}` },
          })
          const payload = await response.json() as OrderStatus | ApiError
          if (!response.ok) throw new Error((payload as ApiError).error?.message ?? `Could not load order status (${response.status}).`)
          const status = payload as OrderStatus
          if (!active) return
          finalStatus = status
          setPaymentReturnStatus(status)
          if (status.paymentStatus !== 'PENDING' || attempt === attempts - 1) break
          await new Promise<void>(resolve => { timeout = setTimeout(resolve, 1500) })
        }
        if (active && paymentReturnMode === 'success' && finalStatus?.paymentStatus === 'SUCCEEDED') {
          setCart({})
        }
        if (active && finalStatus && finalStatus.paymentStatus !== 'PENDING') {
          Object.keys(sessionStorage).filter(key => key.startsWith('checkout-idempotency:')).forEach(key => sessionStorage.removeItem(key))
        }
      } catch (cause) {
        if (active) setPaymentReturnError(cause instanceof Error ? cause.message : 'Could not load order status.')
      } finally {
        if (active) setPaymentReturnChecking(false)
      }
    }

    void loadPaymentStatus()
    return () => { active = false; if (timeout) clearTimeout(timeout) }
  }, [paymentReturnMode, paymentReturnReference])

  function openCheckoutAccess() {
    setCheckoutError('')
    setDemoLoginOpen(true)
  }

  function closePaymentReturn() {
    window.history.replaceState({}, '', window.location.pathname)
    setPaymentReturnMode(null)
    setPaymentReturnStatus(null)
    setPaymentReturnError('')
  }

  return <main className="page-shell">
    <header className="topbar">
      <a className="brand" href="/">BASIC<span>FIT</span></a>
      <button className="cart-trigger" onClick={() => setCartOpen(true)}>Your cart <strong>{count}</strong></button>
    </header>

    <section className="hero">
      <p className="eyebrow">THE EVERYDAY EDIT</p>
      <h1>Little things.<br /><em>Big difference.</em></h1>
      <p className="intro">A considered collection of everyday essentials, picked for you.</p>
      <a className="hero-link" href="#catalog">Explore the collection <span>↓</span></a>
    </section>

    <ProductCatalog
      products={products}
      cart={cart}
      imageIndexes={imageIndexes}
      loading={loading}
      error={error}
      onRetry={() => void loadProducts()}
      onChangeImage={changeImage}
      onSetQuantity={setQuantity}
      onAdd={product => { setQuantity(product, 1); setCartOpen(true) }}
    />

    <footer><span>BASICFIT</span><span>Built for everyday essentials.</span></footer>

    <CartDrawer
      open={cartOpen}
      items={items}
      count={count}
      subtotal={subtotal}
      checkoutSubmitting={checkoutSubmitting}
      checkoutError={demoLoginOpen ? '' : checkoutError}
      onClose={() => setCartOpen(false)}
      onSetQuantity={setQuantity}
      onCheckout={openCheckoutAccess}
    />
    <CheckoutAccessModal
      open={demoLoginOpen}
      submitting={checkoutSubmitting}
      error={checkoutError}
      onClose={() => setDemoLoginOpen(false)}
      onContinue={() => void submitCheckout()}
    />
    {paymentReturnMode && <PaymentReturnModal
      mode={paymentReturnMode}
      order={paymentReturnStatus}
      checking={paymentReturnChecking}
      error={paymentReturnError}
      onClose={closePaymentReturn}
    />}
  </main>
}

export default App
