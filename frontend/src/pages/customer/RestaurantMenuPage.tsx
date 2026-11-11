import React, { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, CreditCard, MapPin, Minus, Plus, ShoppingBag } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { catalogApi, ordersApi } from '../../services/api/eatsnowApi'
import { describeError } from '../../services/api/http'
import { usePolling } from '../../hooks/usePolling'
import { formatMoney } from '../../utils/format'
import { ArchiAlert, ArchiButton } from '../../components/ui'
import { EmptyState, PageContainer } from '../../components/app/PageHeader'
import { dishEmoji, restaurantLook } from '../../utils/visuals'
import { cardLabel, loadWallet } from '../../services/wallet'

const inputClass =
  'w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20'

export const RestaurantMenuPage: React.FC = () => {
  const { restaurantId } = useParams<{ restaurantId: string }>()
  const { token, profile } = useAuth()
  const navigate = useNavigate()

  const restaurants = usePolling(() => catalogApi.restaurants(token!), 60_000)
  const menu = usePolling(() => catalogApi.menu(token!, restaurantId!), 60_000)
  const restaurant = restaurants.data?.items.find((r) => r.id === restaurantId)
  const look = restaurantLook(restaurantId!, restaurant?.name ?? '')

  const [cart, setCart] = useState<Record<string, number>>({})
  const [line1, setLine1] = useState('Calle 10 #43-12')
  const [city, setCity] = useState('Medellin')
  const [instructions, setInstructions] = useState('')
  // Saved cards of the profile page; the order carries only the provider token of the chosen card.
  const [cards] = useState(() => loadWallet(profile!.id))
  const [cardToken, setCardToken] = useState(() => cards[0]?.token ?? '')
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
  const itemCount = lines.reduce((n, l) => n + l.quantity, 0)

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
        cardToken
      )
      navigate(`/orders/${created.data.orderId}`)
    } catch (err) {
      setError(describeError(err))
      setSubmitting(false)
    }
  }

  return (
    <>
      <section className={`bg-gradient-to-br ${look.cover} text-white`}>
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8 sm:py-10 relative overflow-hidden">
          <div className="absolute right-4 sm:right-10 top-1/2 -translate-y-1/2 text-[7rem] sm:text-[9rem] leading-none drop-shadow-xl select-none" aria-hidden>
            {look.emoji}
          </div>
          <Link to="/restaurants" className="inline-flex items-center gap-1 text-sm font-semibold text-white/90 hover:text-white">
            <ArrowLeft className="w-4 h-4" /> All restaurants
          </Link>
          <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight mt-3 drop-shadow-sm">{restaurant?.name ?? 'Menu'}</h1>
          <p className="mt-1 font-semibold text-white/90">
            {look.cuisine} · {restaurant?.available === false ? 'Closed' : 'Open now'}
          </p>
        </div>
      </section>

      <PageContainer>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
          <div className="lg:col-span-2">
            <h2 className="text-xl font-extrabold text-slate-900 mb-4">Menu</h2>
            {menu.error && <ArchiAlert variant="error" title="Could not load the menu">{menu.error}</ArchiAlert>}
            {menu.data && menu.data.items.length === 0 && <EmptyState title="This restaurant has no dishes yet" />}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {menu.data?.items.map((item) => {
                const quantity = cart[item.id] ?? 0
                return (
                  <div
                    key={item.id}
                    className={
                      quantity > 0
                        ? 'rounded-3xl border-2 border-emerald-500 bg-white p-4 flex gap-4 shadow-md'
                        : 'rounded-3xl border-2 border-transparent bg-white p-4 flex gap-4 shadow-sm hover:shadow-md transition-shadow'
                    }
                  >
                    <div className="w-24 h-24 rounded-2xl bg-gradient-to-br from-amber-50 to-orange-100 flex items-center justify-center text-5xl shrink-0" aria-hidden>
                      {dishEmoji(item.name)}
                    </div>
                    <div className="flex-1 min-w-0 flex flex-col">
                      <p className="font-bold text-slate-900">{item.name}</p>
                      <p className="text-lg font-extrabold text-emerald-700 mt-0.5">{formatMoney(item.price)}</p>
                      {!item.available && <p className="text-xs font-semibold text-rose-600">Not available</p>}
                      <div className="mt-auto pt-2 flex justify-end">
                        {quantity === 0 ? (
                          <ArchiButton
                            size="sm"
                            isDisabled={!item.available}
                            onPress={() => change(item.id, 1)}
                            leftIcon={<Plus className="w-3.5 h-3.5" />}
                          >
                            Add
                          </ArchiButton>
                        ) : (
                          <div className="flex items-center gap-1 rounded-full bg-emerald-600 text-white p-1">
                            <button
                              type="button"
                              aria-label={`Remove one ${item.name}`}
                              onClick={() => change(item.id, -1)}
                              className="w-7 h-7 rounded-full hover:bg-white/20 flex items-center justify-center"
                            >
                              <Minus className="w-3.5 h-3.5" />
                            </button>
                            <span className="w-6 text-center font-bold tabular-nums">{quantity}</span>
                            <button
                              type="button"
                              aria-label={`Add one ${item.name}`}
                              onClick={() => change(item.id, 1)}
                              className="w-7 h-7 rounded-full hover:bg-white/20 flex items-center justify-center"
                            >
                              <Plus className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          <div className="lg:sticky lg:top-24 rounded-3xl bg-white shadow-xl shadow-slate-200/70 border border-slate-200 overflow-hidden">
            <div className="bg-slate-900 text-white px-5 py-4 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ShoppingBag className="w-5 h-5 text-emerald-400" />
                <h2 className="font-bold">Your order</h2>
              </div>
              {itemCount > 0 && (
                <span className="text-xs font-bold bg-emerald-500 rounded-full px-2.5 py-0.5">
                  {itemCount} {itemCount === 1 ? 'item' : 'items'}
                </span>
              )}
            </div>

            <div className="p-5">
              {lines.length === 0 ? (
                <div className="text-center py-6">
                  <div className="text-4xl mb-2" aria-hidden>🛒</div>
                  <p className="text-sm text-slate-500">Your cart is empty. Add something tasty!</p>
                </div>
              ) : (
                <ul className="text-sm divide-y divide-slate-100 mb-4">
                  {lines.map((l) => (
                    <li key={l.item.id} className="py-2 flex justify-between gap-2">
                      <span>
                        <span className="font-bold text-emerald-700">{l.quantity}×</span> {l.item.name}
                      </span>
                      <span className="tabular-nums font-semibold">
                        {formatMoney({ amountMinor: l.item.price.amountMinor * l.quantity, currency: 'COP' })}
                      </span>
                    </li>
                  ))}
                  <li className="pt-3 flex justify-between text-base font-extrabold">
                    <span>Total</span>
                    <span className="tabular-nums">{formatMoney({ amountMinor: subtotalMinor, currency: 'COP' })}</span>
                  </li>
                </ul>
              )}

              <div className="flex flex-col gap-3">
                <p className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1">
                  <MapPin className="w-3.5 h-3.5" /> Delivery
                </p>
                <label className="text-xs font-semibold text-slate-600">
                  Address
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
                    placeholder="e.g. Ring the bell twice"
                    onChange={(e) => setInstructions(e.target.value)}
                  />
                </label>
                <p className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1 mt-2">
                  <CreditCard className="w-3.5 h-3.5" /> Payment
                </p>
                {cards.length > 0 ? (
                  <select
                    aria-label="Payment method"
                    className={inputClass}
                    value={cardToken}
                    onChange={(e) => setCardToken(e.target.value)}
                  >
                    {cards.map((card) => (
                      <option key={card.token} value={card.token}>
                        {cardLabel(card)} · {card.behavior}
                      </option>
                    ))}
                  </select>
                ) : (
                  <p className="text-sm text-slate-500">
                    No saved cards. <Link to="/profile" className="text-emerald-700 font-semibold underline">Add one in your account</Link>.
                  </p>
                )}
              </div>

              {error && (
                <ArchiAlert variant="error" title="The order was not placed" className="mt-4">
                  {error}
                </ArchiAlert>
              )}

              <ArchiButton
                size="lg"
                className="w-full mt-5"
                isDisabled={lines.length === 0 || !line1.trim() || !city.trim() || !cardToken}
                isLoading={submitting}
                onPress={() => void placeOrder()}
              >
                {lines.length === 0
                  ? 'Place order'
                  : `Place order · ${formatMoney({ amountMinor: subtotalMinor, currency: 'COP' })}`}
              </ArchiButton>
            </div>
          </div>
        </div>
      </PageContainer>
    </>
  )
}
