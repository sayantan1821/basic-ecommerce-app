import { useCallback, useEffect, useMemo, useState } from 'react'
import { CartDrawer } from './components/CartDrawer'
import { CheckoutAccessModal } from './components/CheckoutAccessModal'
import { OrderStatusPage } from './components/OrderStatusPage'
import { PaymentReturnModal } from './components/PaymentReturnModal'
import { ProductCatalog } from './components/ProductCatalog'
import type { ApiError, Cart, CartItem, CheckoutResult, OrderStatus, Product, StripeCheckoutSession } from './types/store'

const CART_KEY = 'basic-ecommerce-guest-cart'
const SESSION_KEY = 'basic-ecommerce-demo-session-token'
const ORDER_REFERENCES_KEY = 'basic-ecommerce-order-references'
const demoToken = import.meta.env.VITE_DEMO_API_TOKEN ?? ''

function readOrderReferences(): string[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(ORDER_REFERENCES_KEY) ?? '[]')
    return Array.isArray(value) ? [...new Set(value.filter((reference): reference is string => typeof reference === 'string' && reference.length > 0))] : []
  } catch {
    return []
  }
}

function rememberOrder(referenceId: string) {
  const references = readOrderReferences()
  if (!references.includes(referenceId)) localStorage.setItem(ORDER_REFERENCES_KEY, JSON.stringify([referenceId, ...references]))
}

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
  const [sessionToken, setSessionToken] = useState(() => localStorage.getItem(SESSION_KEY) ?? '')
  const [orderReferences, setOrderReferences] = useState<string[]>(readOrderReferences)
  const [activePage, setActivePage] = useState(() => window.location.pathname === '/orders' ? 'orders' : 'home')
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
    const reference = params.get('order') ?? ''
    setPaymentReturnReference(reference)
    if (reference) {
      rememberOrder(reference)
      setOrderReferences(readOrderReferences())
    }
    if (mode === 'success') setCart({})
  }, [])

  useEffect(() => {
    const onPopState = () => setActivePage(window.location.pathname === '/orders' ? 'orders' : 'home')
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
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
    const token = sessionToken || demoToken
    if (!token) {
      setCheckoutError('No demo token is configured. Set DEMO_API_TOKEN in .env and rebuild the frontend.')
      return
    }
    if (!sessionToken) {
      localStorage.setItem(SESSION_KEY, token)
      setSessionToken(token)
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
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify({ items: requestItems }),
      })
      const payload = await response.json() as CheckoutResult | ApiError
      if (response.status === 401 || response.status === 403) expireSession()
      if (!response.ok) throw new Error((payload as ApiError).error?.message ?? `Checkout failed (${response.status}).`)
      const checkout = payload as CheckoutResult
      rememberOrder(checkout.order.referenceId)
      setOrderReferences(readOrderReferences())
      const sessionResponse = await fetch(`/api/payments/${checkout.payment.id}/stripe-session`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      })
      const sessionPayload = await sessionResponse.json() as StripeCheckoutSession | ApiError
      if (sessionResponse.status === 401 || sessionResponse.status === 403) expireSession()
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
            headers: { Authorization: `Bearer ${sessionToken || demoToken}` },
          })
          const payload = await response.json() as OrderStatus | ApiError
          if (response.status === 401 || response.status === 403) expireSession()
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
  }, [paymentReturnMode, paymentReturnReference, sessionToken])

  const expireSession = useCallback(() => {
    localStorage.removeItem(SESSION_KEY)
    setSessionToken('')
  }, [])

  function openCheckoutAccess() {
    setCheckoutError('')
    if (sessionToken) {
      void submitCheckout()
      return
    }
    setDemoLoginOpen(true)
  }

  function navigate(path: '/orders' | '/') {
    window.history.pushState({}, '', path)
    setActivePage(path === '/orders' ? 'orders' : 'home')
  }

  function signInDemo() {
    if (!demoToken) {
      setCheckoutError('No demo token is configured. Set DEMO_API_TOKEN in .env and rebuild the frontend.')
      return
    }
    localStorage.setItem(SESSION_KEY, demoToken)
    setSessionToken(demoToken)
  }

  function signOut() {
    expireSession()
  }

  function closePaymentReturn() {
    window.history.replaceState({}, '', window.location.pathname)
    setPaymentReturnMode(null)
    setPaymentReturnStatus(null)
    setPaymentReturnError('')
  }

  return <main className="page-shell">
    <header className="topbar">
      <a className="brand" href="/" onClick={event => { event.preventDefault(); navigate('/') }}>BASIC<span>FIT</span></a>
      <nav className="topbar-actions" aria-label="Main navigation">
        <a className={`nav-link ${activePage === 'orders' ? 'active' : ''}`} href="/orders" onClick={event => { event.preventDefault(); navigate('/orders') }}>Your orders</a>
        {sessionToken && <button className="nav-link sign-out" onClick={signOut}>Sign out</button>}
        <button className="cart-trigger" onClick={() => setCartOpen(true)}>Your cart <strong>{count}</strong></button>
      </nav>
    </header>

    {activePage === 'orders' ? <OrderStatusPage
      references={orderReferences}
      token={sessionToken}
      loginError={checkoutError}
      onSignIn={signInDemo}
      onSessionExpired={expireSession}
      onBack={() => navigate('/')}
    /> : <><section className="hero">
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

    <footer><span>BASICFIT</span><span>Built for everyday essentials.</span></footer></>}

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
