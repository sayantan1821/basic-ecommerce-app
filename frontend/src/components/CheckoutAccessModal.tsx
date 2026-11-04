import { useState, type FormEvent } from 'react'

type CheckoutAccessModalProps = {
  open: boolean
  submitting: boolean
  error: string
  onClose: () => void
  onLogin: (email: string, password: string) => void
  onSignup: (displayName: string, email: string, password: string) => void
  onDemoLogin: () => void
}

export function CheckoutAccessModal({ open, submitting, error, onClose, onLogin, onSignup, onDemoLogin }: CheckoutAccessModalProps) {
  const [mode, setMode] = useState<'login' | 'signup'>('login')
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  if (!open) return null

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (mode === 'login') onLogin(email, password)
    else onSignup(displayName, email, password)
  }

  return <div className="modal-scrim" onMouseDown={event => { if (event.target === event.currentTarget) onClose() }}><section className="checkout-modal login-modal" role="dialog" aria-modal="true" aria-labelledby="login-title">
    <button className="close-button modal-close" onClick={onClose} aria-label="Close login">×</button>
    <p className="eyebrow">WELCOME</p><h2 id="login-title">{mode === 'login' ? 'Log in to continue' : 'Create your account'}</h2>
    <p>{mode === 'login' ? 'Sign in to continue to checkout and view your orders.' : 'Create an account to continue to checkout and view your orders.'}</p>
    {error && <p className="checkout-error" role="alert">{error}</p>}
    <form className="login-form" onSubmit={submit}>
      {mode === 'signup' && <label>Your name<input type="text" autoComplete="name" required maxLength={120} value={displayName} onChange={event => setDisplayName(event.target.value)} placeholder="Your name" /></label>}
      <label>Email address<input type="email" autoComplete="email" required value={email} onChange={event => setEmail(event.target.value)} placeholder="you@example.com" /></label>
      <label>Password<input type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={mode === 'signup' ? 8 : undefined} maxLength={72} value={password} onChange={event => setPassword(event.target.value)} placeholder={mode === 'signup' ? 'At least 8 characters' : 'Your password'} /></label>
      <button className="checkout-button login-submit" type="submit" disabled={submitting}>{submitting ? (mode === 'login' ? 'Signing in…' : 'Creating account…') : (mode === 'login' ? 'Log in' : 'Sign up')} <span>→</span></button>
    </form>
    <p className="login-switch">{mode === 'login' ? 'New here?' : 'Already have an account?'} <button onClick={() => setMode(mode === 'login' ? 'signup' : 'login')}>{mode === 'login' ? 'Create an account' : 'Log in'}</button></p>
    <div className="login-divider"><span>or</span></div>
    <button className="demo-login-button" disabled={submitting} onClick={onDemoLogin}>Login as demo user <span>→</span></button>
    <div className="social-login-options">
      <span title="Currently disabled"><button type="button" disabled aria-label="Login with Google (currently disabled)">Continue with Google</button></span>
      <span title="Currently disabled"><button type="button" disabled aria-label="Login with Apple (currently disabled)">Continue with Apple</button></span>
    </div>
  </section></div>
}
