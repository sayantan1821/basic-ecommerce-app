import { useCallback, useEffect, useState } from 'react'
import type { ApiError, OrderStatus } from '../types/store'
import { formatPrice } from '../utils/formatPrice'

type OrderStatusPageProps = {
  references: string[]
  token: string
  loginError: string
  onSignIn: () => void
  onSessionExpired: () => void
  onBack: () => void
}

export function OrderStatusPage({ references, token, loginError, onSignIn, onSessionExpired, onBack }: OrderStatusPageProps) {
  const [orders, setOrders] = useState<OrderStatus[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const loadOrders = useCallback(async () => {
    if (!token || !references.length) {
      setOrders([])
      return
    }
    setLoading(true)
    setError('')
    try {
      const results = await Promise.all(references.map(async referenceId => {
        const response = await fetch(`/api/orders/by-reference/${encodeURIComponent(referenceId)}`, {
          headers: { Authorization: `Bearer ${token}` },
        })
        const payload = await response.json() as OrderStatus | ApiError
        if (response.status === 401 || response.status === 403) onSessionExpired()
        if (!response.ok) throw new Error((payload as ApiError).error?.message ?? `Could not load an order (${response.status}).`)
        return payload as OrderStatus
      }))
      setOrders(results)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load order status.')
    } finally {
      setLoading(false)
    }
  }, [onSessionExpired, references, token])

  useEffect(() => { void loadOrders() }, [loadOrders])

  return <section className="orders-page">
    <div className="orders-heading">
      <div><p className="eyebrow">YOUR ACCOUNT</p><h1>Your orders</h1><p className="intro">Track each order and see the latest payment status.</p></div>
      <button className="dark-button" onClick={onBack}>Continue shopping</button>
    </div>

    {!token ? <div className="orders-empty status-panel">
      <h2>Log in to view your orders</h2>
      <p>Your order history is available with the demo checkout session.</p>
      {loginError && <p className="checkout-error" role="alert">{loginError}</p>}
      <button className="dark-button" onClick={onSignIn}>Log in</button>
    </div> : !references.length ? <div className="orders-empty status-panel">
      <h2>No orders yet</h2><p>Orders you place will appear here with their payment status.</p>
    </div> : <>
      <div className="orders-toolbar"><span>{references.length} {references.length === 1 ? 'order' : 'orders'}</span><button className="text-button" disabled={loading} onClick={() => void loadOrders()}>{loading ? 'Refreshing…' : 'Refresh status'}</button></div>
      {error && <div className="orders-error" role="alert"><p>{error}</p><button className="text-button" onClick={() => void loadOrders()}>Try again</button></div>}
      {loading && !orders.length && <div className="status-panel">Loading your orders…</div>}
      {!!orders.length && <div className="orders-list">{orders.map(order => <article className="order-card" key={order.referenceId}>
        <div className="order-card-heading"><div><p className="eyebrow">ORDER REFERENCE</p><strong className="order-reference">{order.referenceId}</strong></div><strong className={`status-pill status-${order.orderStatus.toLowerCase()}`}>{order.orderStatus}</strong></div>
        <div className="order-card-details">
          <div><span>Payment</span><strong className={`payment-${order.paymentStatus.toLowerCase()}`}>{order.paymentStatus}</strong></div>
          <div><span>Total</span><strong>{formatPrice(order.totalPaise)}</strong></div>
        </div>
      </article>)}</div>}
    </>}
  </section>
}
