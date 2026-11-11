/**
 * Saved payment methods of the customer. The app never handles card data: in production the payment provider's
 * checkout form returns a token for the card. Here the provider is the test double of the Payments service
 * (Spec 3.4), so each sandbox card below stands for one of its synthetic tokens and reproduces one outcome.
 *
 * The wallet lives in this browser only (per profile); the order request carries just the chosen token.
 */
export type CardBrand = 'Visa' | 'Mastercard' | 'Amex'

export interface PaymentCard {
  token: string
  brand: CardBrand
  last4: string
  /** What the bank does with this sandbox card. */
  behavior: string
}

/** Sandbox cards the provider supports. The token prefix selects the outcome (FakePaymentProvider). */
export const SANDBOX_CARDS: PaymentCard[] = [
  { token: 'tok_ok_4242', brand: 'Visa', last4: '4242', behavior: 'Payment approved' },
  { token: 'tok_ok_4444', brand: 'Mastercard', last4: '4444', behavior: 'Payment approved' },
  { token: 'tok_declined_0002', brand: 'Visa', last4: '0002', behavior: 'Declined by the bank' },
  { token: 'tok_insufficient_9995', brand: 'Mastercard', last4: '9995', behavior: 'Insufficient funds' },
  { token: 'tok_invalid_0069', brand: 'Visa', last4: '0069', behavior: 'Expired card' },
  {
    token: 'tok_lost_response_1117',
    brand: 'Amex',
    last4: '1117',
    behavior: 'Bank answers late: the payment is confirmed after a few seconds',
  },
  {
    token: 'tok_timeout_0005',
    brand: 'Visa',
    last4: '0005',
    behavior: 'Bank never answers: the payment stays unconfirmed',
  },
  {
    token: 'tok_refund_lost_3220',
    brand: 'Mastercard',
    last4: '3220',
    behavior: 'Payment approved; if the order is rejected, the refund is confirmed late',
  },
]

const DEFAULT_TOKENS = ['tok_ok_4242']

const storageKey = (profileId: string) => `eatsnow.wallet.${profileId}`

export function loadWallet(profileId: string): PaymentCard[] {
  let tokens = DEFAULT_TOKENS
  try {
    const stored = localStorage.getItem(storageKey(profileId))
    if (stored) tokens = JSON.parse(stored) as string[]
  } catch {
    // unavailable storage: fall back to the default card
  }
  return tokens
    .map((token) => SANDBOX_CARDS.find((card) => card.token === token))
    .filter((card): card is PaymentCard => card !== undefined)
}

export function saveWallet(profileId: string, cards: PaymentCard[]): void {
  try {
    localStorage.setItem(storageKey(profileId), JSON.stringify(cards.map((card) => card.token)))
  } catch {
    // unavailable storage: the wallet lasts for this page only
  }
}

export const cardLabel = (card: PaymentCard) => `${card.brand} •••• ${card.last4}`
