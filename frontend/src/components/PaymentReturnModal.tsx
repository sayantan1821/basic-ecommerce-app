import type { OrderStatus } from '../types/store'
import { formatPrice } from '../utils/formatPrice'

type PaymentReturnModalProps = {
  mode: 'success' | 'cancelled'
  order: OrderStatus | null
  checking: boolean
  error: string
  onClose: () => void
}

export function PaymentReturnModal({ mode, order, checking, error, onClose }: PaymentReturnModalProps) {
  const paid = order?.paymentStatus === 'SUCCEEDED'
  const failed = order?.paymentStatus === 'FAILED' || order?.orderStatus === 'EXPIRED'
  const heading = mode === 'cancelled'
    ? 'Checkout was cancelled.'
    : paid
      ? 'Payment confirmed.'
      : failed
        ? 'Payment was not completed.'
        : 'Confirming your payment…'

  return <div className="modal-scrim"><section className="checkout-modal order-created" role="dialog" aria-modal="true" aria-labelledby="payment-return-title">
    <button className="close-button modal-close" onClick={onClose} aria-label="Close">×</button>
    <span className="order-check">{paid ? '✓' : '…'}</span>
    <p className="eyebrow">STRIPE CHECKOUT</p>
    <h2 id="payment-return-title">{heading}</h2>
    {mode === 'cancelled' && <p>Your bag is still here. You can continue checkout to reopen your Stripe payment page.</p>}
    {mode === 'success' && checking && <p>Stripe is sending the payment confirmation. This usually takes a moment.</p>}
    {mode === 'success' && !checking && order?.paymentStatus === 'PENDING' && <p>Your payment is still processing. The order status will update when Stripe confirms it.</p>}
    {mode === 'success' && !checking && !order && <p>We couldn’t load the order status. Your payment will still be reconciled by Stripe.</p>}
    {error && <p className="checkout-error" role="alert">{error}</p>}
    {order && <>
      <p>Reference <strong>{order.referenceId}</strong></p>
      <div className="order-result-row"><span>Order status</span><strong>{order.orderStatus}</strong></div>
      <div className="order-result-row"><span>Payment status</span><strong>{order.paymentStatus}</strong></div>
      <div className="order-result-row"><span>Total</span><strong>{formatPrice(order.totalPaise)}</strong></div>
    </>}
    <button className="dark-button" onClick={onClose}>Back to catalog</button>
  </section></div>
}
