import { useEffect, useMemo, useState } from 'react'

type Product = { id: string; sku: string; name: string; description: string; imageUrls: string[]; pricePaise: number; available: number }
type Cart = Record<string, number>
type CheckoutResult = { order: { referenceId: string; status: string; currency: string; totalPaise: number }; payment: { id: string; status: string; mockClientSecret: string }; replayed: boolean }
type ApiError = { error?: { message?: string } }
const CART_KEY = 'basic-ecommerce-guest-cart'
const demoToken = import.meta.env.VITE_DEMO_API_TOKEN ?? ''
const money = (paise: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(paise / 100)
function readCart(): Cart {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(CART_KEY) ?? '{}')
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
    return Object.fromEntries(Object.entries(value).filter(([id, quantity]) => typeof id === 'string' && Number.isInteger(quantity) && (quantity as number) > 0)) as Cart
  } catch { return {} }
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
  const [checkoutResult, setCheckoutResult] = useState<CheckoutResult | null>(null)

  async function loadProducts() {
    setLoading(true); setError('')
    try {
      const response = await fetch('/api/products')
      if (!response.ok) throw new Error(`Catalog request failed (${response.status})`)
      const data: { products: Product[] } = await response.json()
      setProducts(data.products)
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not load the catalog.') }
    finally { setLoading(false) }
  }
  useEffect(() => { void loadProducts() }, [])
  useEffect(() => { localStorage.setItem(CART_KEY, JSON.stringify(cart)) }, [cart])

  const items = useMemo(() => products.filter(p => cart[p.id] > 0).map(product => ({ product, quantity: cart[product.id] })), [products, cart])
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
    if (!demoToken) { setCheckoutError('No demo token is configured. Set DEMO_API_TOKEN in .env and rebuild the frontend.'); return }
    const requestItems = items.map(({ product, quantity }) => ({ productId: product.id, quantity })).sort((a, b) => a.productId.localeCompare(b.productId))
    const fingerprint = JSON.stringify(requestItems)
    const storageKey = `checkout-idempotency:${fingerprint}`
    const idempotencyKey = sessionStorage.getItem(storageKey) ?? crypto.randomUUID()
    sessionStorage.setItem(storageKey, idempotencyKey)
    setCheckoutSubmitting(true); setCheckoutError('')
    try {
      const response = await fetch('/api/checkout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${demoToken}`, 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify({ items: requestItems }),
      })
      const payload = await response.json() as CheckoutResult | ApiError
      if (!response.ok) throw new Error((payload as ApiError).error?.message ?? `Checkout failed (${response.status}).`)
      sessionStorage.removeItem(storageKey)
      setCheckoutResult(payload as CheckoutResult); setCart({}); setDemoLoginOpen(false); setCartOpen(false)
    } catch (cause) {
      setCheckoutError(cause instanceof Error ? cause.message : 'Checkout failed. Your cart is unchanged; you can safely retry.')
    } finally { setCheckoutSubmitting(false) }
  }

  return <main className="page-shell">
    <header className="topbar"><a className="brand" href="/">BASIC<span>FIT</span></a><button className="cart-trigger" onClick={() => setCartOpen(true)}>Your cart <strong>{count}</strong></button></header>
    <section className="hero"><p className="eyebrow">THE EVERYDAY EDIT</p><h1>Little things.<br/><em>Big difference.</em></h1><p className="intro">A considered collection of everyday essentials, picked for you.</p><a className="hero-link" href="#catalog">Explore the collection <span>↓</span></a></section>
    <section className="catalog" id="catalog"><div className="section-heading"><div><p className="eyebrow">THE EVERYDAY KIT</p><h2>Shop the essentials</h2></div>{!loading && !error && <span className="catalog-count">{products.length} considered essentials</span>}</div>
      {loading && <div className="status-panel" role="status">Loading the collection…</div>}
      {!loading && error && <div className="status-panel" role="alert"><p>We couldn’t load the collection.</p><button className="text-button" onClick={() => void loadProducts()}>Try again</button><small>{error}</small></div>}
      {!loading && !error && products.length === 0 && <div className="status-panel">No products are available right now.</div>}
      {!loading && !error && products.length > 0 && <div className="product-grid">{products.map((product, index) => {
        const quantity = cart[product.id] ?? 0
        return <article className="product-card" key={product.id}>
          <div className={`product-image tone-${index % 5}`}><img src={product.imageUrls[imageIndexes[product.id] ?? 0]} alt={`${product.name} image ${(imageIndexes[product.id] ?? 0) + 1}`} loading="lazy" onError={event => { event.currentTarget.style.opacity = '0' }}/>
            {product.imageUrls.length > 1 && <><button className="carousel-arrow previous" aria-label={`Previous ${product.name} image`} onClick={() => changeImage(product, -1)}>‹</button><button className="carousel-arrow next" aria-label={`Next ${product.name} image`} onClick={() => changeImage(product, 1)}>›</button><span className="carousel-position">{(imageIndexes[product.id] ?? 0) + 1} / {product.imageUrls.length}</span></>}
          </div>
          <div className="product-info"><div className="product-title-row"><h3>{product.name}</h3><strong className="product-price">{money(product.pricePaise)}</strong></div><p className="product-description">{product.description}</p><div className="product-actions"><span className={product.available ? 'stock' : 'stock out'}>{product.available ? `${product.available} available` : 'Sold out'}</span>{quantity ? <div className="quantity-control"><button aria-label="Decrease quantity" onClick={() => setQuantity(product, quantity - 1)}>−</button><span>{quantity}</span><button aria-label="Increase quantity" disabled={quantity >= Math.min(product.available, 10)} onClick={() => setQuantity(product, quantity + 1)}>+</button></div> : <button className="add-button" disabled={!product.available} onClick={() => { setQuantity(product, 1); setCartOpen(true) }}>Add to bag <span>+</span></button>}</div></div>
        </article>
      })}</div>}
    </section>
    <footer><span>BASICFIT</span><span>Built for everyday essentials.</span></footer>
    {cartOpen && <div className="scrim" onMouseDown={event => { if (event.target === event.currentTarget) setCartOpen(false) }}><aside className="cart-drawer" aria-label="Shopping cart">
      <div className="drawer-heading"><div><p className="eyebrow">YOUR SELECTION</p><h2>Your bag <span>({count})</span></h2></div><button className="close-button" onClick={() => setCartOpen(false)} aria-label="Close cart">×</button></div>
      {!items.length ? <div className="cart-empty"><span className="empty-bag">✳</span><h3>Your bag is taking a breather.</h3><p>Add a few essentials and they’ll be waiting here.</p><button className="dark-button" onClick={() => setCartOpen(false)}>Explore products</button></div> : <>
        <div className="cart-lines">{items.map(({ product, quantity }) => <div className="cart-line" key={product.id}><div className="cart-line-image"><img src={product.imageUrls[0]} alt="" onError={event => { event.currentTarget.style.opacity = '0' }}/></div><div className="cart-line-content"><h3>{product.name}</h3><strong>{money(product.pricePaise * quantity)}</strong><div className="line-controls"><div className="quantity-control"><button aria-label="Decrease quantity" onClick={() => setQuantity(product, quantity - 1)}>−</button><span>{quantity}</span><button aria-label="Increase quantity" disabled={quantity >= Math.min(product.available, 10)} onClick={() => setQuantity(product, quantity + 1)}>+</button></div><button className="remove-button" onClick={() => setQuantity(product, 0)}>Remove</button></div></div></div>)}</div>
        <div className="cart-summary"><div><span>Subtotal</span><strong>{money(subtotal)}</strong></div><p>Shipping and payment are calculated at checkout.</p>{checkoutError && !demoLoginOpen && <p className="checkout-error" role="alert">{checkoutError}</p>}
          <button className="checkout-button" disabled={checkoutSubmitting} onClick={() => { setCheckoutError(''); setDemoLoginOpen(true) }}>{checkoutSubmitting ? 'Creating your order…' : 'Continue to checkout'} <span>→</span></button><button className="continue-link" onClick={() => setCartOpen(false)}>Continue shopping</button>
        </div>
      </>}
    </aside></div>}
    {demoLoginOpen && <div className="modal-scrim"><section className="checkout-modal" role="dialog" aria-modal="true" aria-labelledby="demo-login-title"><button className="close-button modal-close" onClick={() => setDemoLoginOpen(false)} aria-label="Close">×</button><p className="eyebrow">CHECKOUT ACCESS</p><h2 id="demo-login-title">Please log in to continue</h2><p>This take-home uses a shared demo bearer token to protect checkout. It is not a personal customer account.</p>{checkoutError && <p className="checkout-error" role="alert">{checkoutError}</p>}<button className="checkout-button" disabled={checkoutSubmitting} onClick={() => void submitCheckout()}>{checkoutSubmitting ? 'Creating your order…' : 'Log in and continue'} <span>→</span></button></section></div>}
    {checkoutResult && <div className="modal-scrim"><section className="checkout-modal order-created" role="dialog" aria-modal="true" aria-labelledby="order-created-title"><button className="close-button modal-close" onClick={() => setCheckoutResult(null)} aria-label="Close">×</button><span className="order-check">✓</span><p className="eyebrow">ORDER CREATED</p><h2 id="order-created-title">Your order is in.</h2><p>Reference <strong>{checkoutResult.order.referenceId}</strong></p><div className="order-result-row"><span>Order status</span><strong>{checkoutResult.order.status}</strong></div><div className="order-result-row"><span>Payment status</span><strong>{checkoutResult.payment.status}</strong></div><div className="order-result-row"><span>Total</span><strong>{money(checkoutResult.order.totalPaise)}</strong></div><button className="dark-button" onClick={() => setCheckoutResult(null)}>Back to catalog</button></section></div>}
  </main>
}
export default App
