export type Product = {
  id: string
  sku: string
  name: string
  description: string
  imageUrls: string[]
  pricePaise: number
  available: number
}

export type Cart = Record<string, number>

export type CartItem = { product: Product; quantity: number }

export type CheckoutResult = {
  order: { referenceId: string; status: string; currency: string; totalPaise: number }
  payment: { id: string; status: string }
  replayed: boolean
}

export type StripeCheckoutSession = { sessionId: string; checkoutUrl: string }

export type SessionResponse = {
  token: string
  tokenType: string
  expiresAt: string
  user: { email: string; displayName: string }
}

export type OrderStatus = {
  referenceId: string
  orderStatus: string
  paymentStatus: string
  currency: string
  totalPaise: number
}

export type ApiError = { error?: { message?: string } }

