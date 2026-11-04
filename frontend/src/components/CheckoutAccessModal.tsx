type CheckoutAccessModalProps = {
  open: boolean
  submitting: boolean
  error: string
  onClose: () => void
  onContinue: () => void
}

export function CheckoutAccessModal({ open, submitting, error, onClose, onContinue }: CheckoutAccessModalProps) {
  if (!open) return null
  return <div className="modal-scrim"><section className="checkout-modal" role="dialog" aria-modal="true" aria-labelledby="demo-login-title">
    <button className="close-button modal-close" onClick={onClose} aria-label="Close">×</button>
    <p className="eyebrow">CHECKOUT ACCESS</p><h2 id="demo-login-title">Please log in to continue</h2>
    <p>Continue with the shared demo access token, then pay securely through Stripe test checkout. This is not a personal customer account.</p>
    {error && <p className="checkout-error" role="alert">{error}</p>}
    <button className="checkout-button" disabled={submitting} onClick={onContinue}>{submitting ? 'Opening Stripe Checkout…' : 'Continue to Stripe'} <span>→</span></button>
  </section></div>
}
