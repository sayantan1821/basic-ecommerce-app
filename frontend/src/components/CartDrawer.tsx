import type { CartItem, Product } from '../types/store'
import { formatPrice } from '../utils/formatPrice'

type CartDrawerProps = {
  open: boolean
  items: CartItem[]
  count: number
  subtotal: number
  checkoutSubmitting: boolean
  checkoutError: string
  onClose: () => void
  onSetQuantity: (product: Product, quantity: number) => void
  onCheckout: () => void
}

export function CartDrawer({ open, items, count, subtotal, checkoutSubmitting, checkoutError, onClose, onSetQuantity, onCheckout }: CartDrawerProps) {
  if (!open) return null
  return <div className="scrim" onMouseDown={event => { if (event.target === event.currentTarget) onClose() }}>
    <aside className="cart-drawer" aria-label="Shopping cart">
      <div className="drawer-heading"><div><p className="eyebrow">YOUR SELECTION</p><h2>Your bag <span>({count})</span></h2></div><button className="close-button" onClick={onClose} aria-label="Close cart">×</button></div>
      {!items.length ? <div className="cart-empty"><span className="empty-bag">✳</span><h3>Your bag is taking a breather.</h3><p>Add a few essentials and they’ll be waiting here.</p><button className="dark-button" onClick={onClose}>Explore products</button></div> : <>
        <div className="cart-lines">{items.map(({ product, quantity }) => <div className="cart-line" key={product.id}>
          <div className="cart-line-image"><img src={product.imageUrls[0]} alt="" onError={event => { event.currentTarget.style.opacity = '0' }} /></div>
          <div className="cart-line-content"><h3>{product.name}</h3><strong>{formatPrice(product.pricePaise * quantity)}</strong>
            <div className="line-controls"><div className="quantity-control">
              <button aria-label={`Decrease ${product.name} quantity`} onClick={() => onSetQuantity(product, quantity - 1)}>−</button><span>{quantity}</span>
              <button aria-label={`Increase ${product.name} quantity`} disabled={quantity >= Math.min(product.available, 10)} onClick={() => onSetQuantity(product, quantity + 1)}>+</button>
            </div><button className="remove-button" onClick={() => onSetQuantity(product, 0)}>Remove</button></div>
          </div>
        </div>)}</div>
        <div className="cart-summary"><div><span>Subtotal</span><strong>{formatPrice(subtotal)}</strong></div><p>Shipping and payment are calculated at checkout.</p>
          {checkoutError && <p className="checkout-error" role="alert">{checkoutError}</p>}
          <button className="checkout-button" disabled={checkoutSubmitting} onClick={onCheckout}>{checkoutSubmitting ? 'Creating your order…' : 'Continue to checkout'} <span>→</span></button>
          <button className="continue-link" onClick={onClose}>Continue shopping</button>
        </div>
      </>}
    </aside>
  </div>
}
