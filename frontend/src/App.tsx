import { useCallback, useEffect, useMemo, useState } from 'react'
import { CartDrawer } from './components/CartDrawer'
import { CheckoutAccessModal } from './components/CheckoutAccessModal'
import { OrderStatusPage } from './components/OrderStatusPage'
import { PaymentReturnModal } from './components/PaymentReturnModal'
import { ProductCatalog } from './components/ProductCatalog'
import type { ApiError, Cart, CartItem, CheckoutResult, OrderStatus, Product, SessionResponse, StripeCheckoutSession } from './types/store'

const CART_KEY = 'basic-ecommerce-guest-cart'
const SESSION_KEY = 'basic-ecommerce-demo-session-token'

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
  const [activePage, setActivePage] = useState(() => window.location.pathname === '/orders' && Boolean(localStorage.getItem(SESSION_KEY)) ? 'orders' : 'home')
  const [demoLoginOpen, setDemoLoginOpen] = useState(false)
  const [checkoutAfterLogin, setCheckoutAfterLogin] = useState(false)
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
    if (mode === 'success') setCart({})
  }, [])

  useEffect(() => {
    const onPopState = () => setActivePage(window.location.pathname === '/orders' ? 'orders' : 'home')
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  useEffect(() => {
    if (!sessionToken && window.location.pathname === '/orders') window.history.replaceState({}, '', '/')
  }, [])

  useEffect(() => {
    if (!sessionToken && activePage === 'orders') navigate('/')
  }, [sessionToken, activePage])

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

  async function submitCheckout(tokenOverride?: string) {
    const token = tokenOverride || sessionToken
    if (!token) {
      setCheckoutAfterLogin(true)
      setCheckoutError('Please log in to continue to checkout.')
      setDemoLoginOpen(true)
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
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify({ items: requestItems }),
      })
      const payload = await response.json() as CheckoutResult | ApiError
      if (response.status === 401 || response.status === 403) expireSession()
      if (!response.ok) throw new Error((payload as ApiError).error?.message ?? `Checkout failed (${response.status}).`)
      const checkout = payload as CheckoutResult
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
            headers: { Authorization: `Bearer ${sessionToken}` },
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
    setCheckoutAfterLogin(true)
    setDemoLoginOpen(true)
  }

  function openLogin() {
    setCheckoutError('')
    setCheckoutAfterLogin(false)
    setDemoLoginOpen(true)
  }

  async function authenticate(path: '/login' | '/signup' | '/demo', body?: Record<string, string>) {
    setCheckoutSubmitting(true)
    setCheckoutError('')
    try {
      const response = await fetch(`/api/auth${path}`, {
        method: 'POST',
        headers: body ? { 'Content-Type': 'application/json' } : undefined,
        body: body ? JSON.stringify(body) : undefined,
      })
      const payload = await response.json() as SessionResponse | ApiError
      if (!response.ok) throw new Error((payload as ApiError).error?.message ?? `Authentication failed (${response.status}).`)
      const session = payload as SessionResponse
      localStorage.setItem(SESSION_KEY, session.token)
      setSessionToken(session.token)
      setDemoLoginOpen(false)
      if (checkoutAfterLogin) {
        setCheckoutAfterLogin(false)
        await submitCheckout(session.token)
      }
    } catch (cause) {
      setCheckoutError(cause instanceof Error ? cause.message : 'Could not authenticate. Please try again.')
    } finally {
      setCheckoutSubmitting(false)
    }
  }

  function navigate(path: '/orders' | '/') {
    window.history.pushState({}, '', path)
    setActivePage(path === '/orders' ? 'orders' : 'home')
  }

  async function signOut() {
    const token = sessionToken
    expireSession()
    if (token) {
      try { await fetch('/api/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${token}` } }) } catch { /* local sign-out still completes offline */ }
    }
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
        {sessionToken
          ? <a className={`nav-link ${activePage === 'orders' ? 'active' : ''}`} href="/orders" onClick={event => { event.preventDefault(); navigate('/orders') }}>Your orders</a>
          : <button className="nav-link" onClick={openLogin}>Log in</button>}
        {sessionToken && <button className="nav-link sign-out" onClick={signOut}>Sign out</button>}
        <button className="cart-trigger" onClick={() => setCartOpen(true)}>Your cart <strong>{count}</strong></button>
      </nav>
    </header>

    {activePage === 'orders' ? <OrderStatusPage
      token={sessionToken}
      loginError={checkoutError}
      onSignIn={openLogin}
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
      onClose={() => { setDemoLoginOpen(false); setCheckoutAfterLogin(false) }}
      onLogin={(email, password) => void authenticate('/login', { email, password })}
      onSignup={(displayName, email, password) => void authenticate('/signup', { displayName, email, password })}
      onDemoLogin={() => void authenticate('/demo')}
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
