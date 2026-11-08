import React, { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Minus, Plus, ShoppingBag } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { catalogApi, ordersApi } from '../../services/api/eatsnowApi'
import { describeError } from '../../services/api/http'
import { usePolling } from '../../hooks/usePolling'
import { formatMoney } from '../../utils/format'
import { ArchiAlert, ArchiButton, ArchiCard } from '../../components/ui'
import { EmptyState, PageContainer, PageHeader } from '../../components/app/PageHeader'

/**
 * Synthetic payment tokens of the payment test double (Spec 3.4, no real cards). Each one reproduces a scenario.
 */
const PAYMENT_OPTIONS = [
  { token: 'tok_ok', label: 'Card that is approved' },
  { token: 'tok_declined', label: 'Card that is declined' },
  { token: 'tok_lost_response', label: 'Provider response lost (reconciled later)' },
  { token: 'tok_timeout', label: 'Provider timeout (stays unconfirmed)' },
  { token: 'tok_refund_lost', label: 'Approved; a later refund response is lost' },
]

const inputClass =
  'w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20'

export const RestaurantMenuPage: React.FC = () => {
  const { restaurantId } = useParams<{ restaurantId: string }>()
  const { token } = useAuth()
  const navigate = useNavigate()

  const restaurants = usePolling(() => catalogApi.restaurants(token!), 60_000)
  const menu = usePolling(() => catalogApi.menu(token!, restaurantId!), 60_000)
  const restaurant = restaurants.data?.items.find((r) => r.id === restaurantId)

  const [cart, setCart] = useState<Record<string, number>>({})
  const [line1, setLine1] = useState('Calle 10 #43-12')
  const [city, setCity] = useState('Medellin')
  const [instructions, setInstructions] = useState('')
  const [paymentToken, setPaymentToken] = useState('tok_ok')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const lines = useMemo(
    () =>
      (menu.data?.items ?? [])
        .filter((item) => (cart[item.id] ?? 0) > 0)
        .map((item) => ({ item, quantity: cart[item.id] })),
    [menu.data, cart]
  )
  const subtotalMinor = lines.reduce((sum, l) => sum + l.item.price.amountMinor * l.quantity, 0)

  const change = (itemId: string, delta: number) =>
    setCart((c) => ({ ...c, [itemId]: Math.min(20, Math.max(0, (c[itemId] ?? 0) + delta)) }))

  /** Quote first (Catalog signs the prices), then the order: Orders never calls Catalog synchronously (Spec 2.3). */
  const placeOrder = async () => {
    setSubmitting(true)
    setError(null)
    try {
      const quote = await catalogApi.quote(
        token!,
        restaurantId!,
        lines.map((l) => ({ menuItemId: l.item.id, quantity: l.quantity }))
      )
      const created = await ordersApi.create(
        token!,
        quote.quoteToken,
        { line1, city, instructions: instructions || undefined },
        paymentToken
      )
      navigate(`/orders/${created.data.orderId}`, { state: { traceId: created.traceId } })
    } catch (err) {
      setError(describeError(err))
      setSubmitting(false)
    }
  }

  return (
    <PageContainer>
      <Link to="/restaurants" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800 mb-4">
        <ArrowLeft className="w-4 h-4" /> Restaurants
      </Link>
      <PageHeader title={restaurant?.name ?? 'Menu'} subtitle="Add dishes to your order." />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
        <div className="lg:col-span-2 flex flex-col gap-3">
          {menu.error && <ArchiAlert variant="error" title="Could not load the menu">{menu.error}</ArchiAlert>}
          {menu.data && menu.data.items.length === 0 && <EmptyState title="This restaurant has no dishes yet" />}
          {menu.data?.items.map((item) => (
            <div
              key={item.id}
              className="rounded-2xl border border-slate-200/80 bg-white/90 p-4 flex items-center justify-between gap-4"
            >
              <div>
                <p className="font-semibold text-slate-900">{item.name}</p>
                <p className="text-sm text-slate-500">{formatMoney(item.price)}</p>
                {!item.available && <p className="text-xs text-rose-600">Not available</p>}
              </div>
              <div className="flex items-center gap-2">
                <ArchiButton
                  variant="outline"
                  size="sm"
                  aria-label={`Remove one ${item.name}`}
                  isDisabled={!cart[item.id]}
                  onPress={() => change(item.id, -1)}
                >
                  <Minus className="w-3.5 h-3.5" />
                </ArchiButton>
                <span className="w-6 text-center font-bold tabular-nums">{cart[item.id] ?? 0}</span>
                <ArchiButton
                  variant="outline"
                  size="sm"
                  aria-label={`Add one ${item.name}`}
                  isDisabled={!item.available}
                  onPress={() => change(item.id, 1)}
                >
                  <Plus className="w-3.5 h-3.5" />
                </ArchiButton>
              </div>
            </div>
          ))}
        </div>

        <ArchiCard className="lg:sticky lg:top-24">
          <div className="flex items-center gap-2 mb-3">
            <ShoppingBag className="w-5 h-5 text-emerald-600" />
            <h2 className="font-bold text-slate-900">Your order</h2>
          </div>

          {lines.length === 0 ? (
            <p className="text-sm text-slate-500">Your cart is empty.</p>
          ) : (
            <ul className="text-sm divide-y divide-slate-100 mb-3">
              {lines.map((l) => (
                <li key={l.item.id} className="py-1.5 flex justify-between gap-2">
                  <span>
                    {l.quantity} × {l.item.name}
                  </span>
                  <span className="tabular-nums">
                    {formatMoney({ amountMinor: l.item.price.amountMinor * l.quantity, currency: 'COP' })}
                  </span>
                </li>
              ))}
              <li className="pt-2 flex justify-between font-bold">
                <span>Total</span>
                <span className="tabular-nums">{formatMoney({ amountMinor: subtotalMinor, currency: 'COP' })}</span>
              </li>
            </ul>
          )}

          <div className="flex flex-col gap-2.5 mt-2">
            <label className="text-xs font-semibold text-slate-600">
              Delivery address
              <input className={inputClass} value={line1} maxLength={200} onChange={(e) => setLine1(e.target.value)} />
            </label>
            <label className="text-xs font-semibold text-slate-600">
              City
              <input className={inputClass} value={city} maxLength={100} onChange={(e) => setCity(e.target.value)} />
            </label>
            <label className="text-xs font-semibold text-slate-600">
              Instructions (optional)
              <input
                className={inputClass}
                value={instructions}
                maxLength={300}
                onChange={(e) => setInstructions(e.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-600">
              Payment method (test cards)
              <select className={inputClass} value={paymentToken} onChange={(e) => setPaymentToken(e.target.value)}>
                {PAYMENT_OPTIONS.map((o) => (
                  <option key={o.token} value={o.token}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {error && (
            <ArchiAlert variant="error" title="The order was not placed" className="mt-3">
              {error}
            </ArchiAlert>
          )}

          <ArchiButton
            className="w-full mt-4"
            isDisabled={lines.length === 0 || !line1.trim() || !city.trim()}
            isLoading={submitting}
            onPress={() => void placeOrder()}
          >
            Place order
          </ArchiButton>
        </ArchiCard>
      </div>
    </PageContainer>
  )
}
