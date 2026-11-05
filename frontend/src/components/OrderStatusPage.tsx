import { useCallback, useEffect, useState } from 'react'
import type { ApiError, OrderStatus } from '../types/store'
import { formatPrice } from '../utils/formatPrice'

function formatPaidAt(value: string) {
  return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

type OrderStatusPageProps = {
  token: string
  loginError: string
  onSignIn: () => void
  onSessionExpired: () => void
  onBack: () => void
}

export function OrderStatusPage({ token, loginError, onSignIn, onSessionExpired, onBack }: OrderStatusPageProps) {
  const [orders, setOrders] = useState<OrderStatus[]>([])
  const [loading, setLoading] = useState(Boolean(token))
  const [error, setError] = useState('')

  const loadOrders = useCallback(async (reconcilePending = false) => {
    if (!token) {
      setOrders([])
      setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    try {
      const response = await fetch('/api/orders', { headers: { Authorization: `Bearer ${token}` } })
      const payload = await response.json() as OrderStatus[] | ApiError
      if (response.status === 401 || response.status === 403) onSessionExpired()
      if (!response.ok) throw new Error((payload as ApiError).error?.message ?? `Could not load your orders (${response.status}).`)
      let loadedOrders = payload as OrderStatus[]
      let reconciliationFailed = false
      if (reconcilePending) {
        const pending = loadedOrders.filter(order => order.paymentStatus === 'PENDING')
        const results = await Promise.all(pending.map(async order => {
          try {
            const reconcileResponse = await fetch(`/api/orders/by-reference/${order.referenceId}/reconcile`, {
              method: 'POST',
              headers: { Authorization: `Bearer ${token}` },
            })
            if (reconcileResponse.status === 401 || reconcileResponse.status === 403) onSessionExpired()
            return reconcileResponse.ok
          } catch {
            return false
          }
        }))
        reconciliationFailed = results.some(result => !result)
        const refreshedResponse = await fetch('/api/orders', { headers: { Authorization: `Bearer ${token}` } })
        const refreshedPayload = await refreshedResponse.json() as OrderStatus[] | ApiError
        if (refreshedResponse.status === 401 || refreshedResponse.status === 403) onSessionExpired()
        if (!refreshedResponse.ok) throw new Error((refreshedPayload as ApiError).error?.message ?? `Could not refresh your orders (${refreshedResponse.status}).`)
        loadedOrders = refreshedPayload as OrderStatus[]
      }
      setOrders(loadedOrders)
      loadedOrders.filter(order => order.paymentStatus !== 'PENDING').forEach(order => {
        const orderKey = `checkout-idempotency-order:${order.referenceId}`
        const idempotencyKey = sessionStorage.getItem(orderKey)
        if (idempotencyKey) sessionStorage.removeItem(idempotencyKey)
        sessionStorage.removeItem(orderKey)
      })
      if (reconciliationFailed) setError('Some pending payments could not be verified with Stripe. Check the backend logs and retry.')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load order status.')
    } finally {
      setLoading(false)
    }
  }, [onSessionExpired, token])

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
    </div> : !loading && !orders.length && !error ? <div className="orders-empty status-panel">
      <h2>No orders yet</h2><p>Orders you place will appear here with their payment status.</p>
    </div> : <>
      <div className="orders-toolbar"><span>{orders.length} {orders.length === 1 ? 'order' : 'orders'}</span><button className="text-button" disabled={loading} onClick={() => void loadOrders(true)}>{loading ? 'Checking with Stripe…' : 'Refresh status'}</button></div>
      {error && <div className="orders-error" role="alert"><p>{error}</p><button className="text-button" onClick={() => void loadOrders()}>Try again</button></div>}
      {loading && !orders.length && <div className="status-panel">Loading your orders…</div>}
      {!!orders.length && <div className="orders-list">{orders.map(order => <article className="order-card" key={order.referenceId}>
        <div className="order-card-heading"><div><p className="eyebrow">ORDER REFERENCE</p><strong className="order-reference">{order.referenceId}</strong></div><strong className={`status-pill status-${order.orderStatus.toLowerCase()}`}>{order.orderStatus}</strong></div>
        <div className="order-card-details">
          <div><span>Payment</span><strong className={`payment-${order.paymentStatus.toLowerCase()}`}>{order.paymentStatus}</strong></div>
          <div><span>Total</span><strong>{formatPrice(order.totalPaise)}</strong></div>
          {order.paidAt && <div><span>Paid on</span><strong>{formatPaidAt(order.paidAt)}</strong></div>}
        </div>
      </article>)}</div>}
    </>}
  </section>
}
