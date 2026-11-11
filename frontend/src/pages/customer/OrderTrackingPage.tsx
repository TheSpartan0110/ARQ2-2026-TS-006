import React from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Bike, ChefHat, House, MapPin, Receipt, ThumbsUp } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { catalogApi, ordersApi } from '../../services/api/eatsnowApi'
import { usePolling } from '../../hooks/usePolling'
import type { OrderHistoryEntry, OrderStatus, PaymentStatus } from '../../types/domain'
import { ORDER_STATUS, PAYMENT_STATUS, formatDateTime, formatMoney, formatTime, isFinal } from '../../utils/format'
import { cn } from '../../utils/cn'
import { ArchiAlert, ArchiCard } from '../../components/ui'
import { StatusPill } from '../../components/app/StatusPill'
import { PageContainer } from '../../components/app/PageHeader'
import { dishEmoji } from '../../utils/visuals'

/** The mandatory order states of the case, in order (Spec 2.4), plus delivery completion. */
const STEPS: { status: OrderStatus; icon: React.ElementType }[] = [
  { status: 'CREATED', icon: Receipt },
  { status: 'ACCEPTED', icon: ThumbsUp },
  { status: 'PREPARING', icon: ChefHat },
  { status: 'ON_THE_WAY', icon: Bike },
  { status: 'DELIVERED', icon: House },
]

/** Headline of each order state. */
const HERO: Record<OrderStatus, { emoji: string; title: string; text: string; color: string }> = {
  CREATED: {
    emoji: '🧾',
    title: 'We received your order',
    text: 'We are confirming your payment and sending it to the restaurant.',
    color: 'from-sky-600 via-sky-500 to-cyan-400',
  },
  ACCEPTED: {
    emoji: '👍',
    title: 'The restaurant accepted your order',
    text: 'The kitchen will start preparing it in a moment.',
    color: 'from-emerald-700 via-emerald-600 to-teal-500',
  },
  PREPARING: {
    emoji: '👨‍🍳',
    title: 'Your food is being prepared',
    text: 'A driver is assigned to pick it up as soon as it is ready.',
    color: 'from-orange-600 via-orange-500 to-amber-400',
  },
  ON_THE_WAY: {
    emoji: '🛵',
    title: 'Your order is on the way',
    text: 'The driver picked it up and is heading to you.',
    color: 'from-violet-700 via-violet-600 to-fuchsia-500',
  },
  DELIVERED: {
    emoji: '🎉',
    title: 'Delivered. Enjoy your meal!',
    text: 'Thank you for ordering with EatsNow.',
    color: 'from-emerald-700 via-emerald-600 to-lime-500',
  },
  REJECTED: {
    emoji: '😔',
    title: 'The restaurant could not take your order',
    text: 'Your payment is refunded automatically.',
    color: 'from-slate-700 via-slate-600 to-slate-500',
  },
  PAYMENT_FAILED: {
    emoji: '💳',
    title: 'Your payment was declined',
    text: 'The order was not sent to the restaurant and nothing was charged.',
    color: 'from-rose-700 via-rose-600 to-red-500',
  },
}

const PAYMENT_EXPLANATION: Partial<Record<PaymentStatus, string>> = {
  PENDING: 'Your order is saved. The payment is processed in the background; you do not have to wait on this page.',
  UNKNOWN:
    'The payment provider did not answer in time. We are confirming the charge with it; you will never be charged twice.',
  REFUND_PENDING: 'Your refund has been requested.',
  REFUNDED: 'Your payment was refunded.',
}

/** One readable sentence per recorded change. */
function describeChange(e: OrderHistoryEntry): string {
  if (e.previousStatus === null) return 'Order received'
  if (e.previousStatus !== e.status) return ORDER_STATUS[e.status].label
  if (e.previousPaymentStatus !== e.paymentStatus) return PAYMENT_STATUS[e.paymentStatus].label
  return 'Updated'
}

export const OrderTrackingPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>()
  const { token } = useAuth()

  const order = usePolling(() => ordersApi.get(token!, orderId!), 2_000)
  const live = order.data ? !isFinal(order.data.status, order.data.paymentStatus) : true
  const history = usePolling(() => ordersApi.history(token!, orderId!), 2_000, live)
  const restaurants = usePolling(() => catalogApi.restaurants(token!), 60_000)

  if (order.error && !order.data) {
    return (
      <PageContainer>
        <ArchiAlert variant="error" title="Could not load the order">{order.error}</ArchiAlert>
      </PageContainer>
    )
  }
  if (!order.data) {
    return <PageContainer><p className="text-sm text-slate-500">Loading…</p></PageContainer>
  }

  const o = order.data
  const restaurantName = restaurants.data?.items.find((r) => r.id === o.restaurantId)?.name ?? 'Restaurant'
  const failed = o.status === 'REJECTED' || o.status === 'PAYMENT_FAILED'
  const currentStep = STEPS.findIndex((s) => s.status === o.status)
  const hero = HERO[o.status]
  const progress = o.status === 'DELIVERED' ? 100 : (Math.max(currentStep, 0) / (STEPS.length - 1)) * 100

  return (
    <>
      <section className={`bg-gradient-to-br ${hero.color} text-white transition-colors duration-700`}>
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8 sm:py-10">
          <Link to="/orders" className="inline-flex items-center gap-1 text-sm font-semibold text-white/90 hover:text-white">
            <ArrowLeft className="w-4 h-4" /> My orders
          </Link>
          <div className="flex items-center gap-5 mt-4">
            <div
              className={cn(
                'w-20 h-20 sm:w-24 sm:h-24 rounded-3xl bg-white/20 backdrop-blur border border-white/30 flex items-center justify-center text-5xl sm:text-6xl shrink-0',
                live && 'animate-pulse'
              )}
              aria-hidden
            >
              {hero.emoji}
            </div>
            <div className="min-w-0">
              <p className="text-sm font-semibold text-white/80">
                {restaurantName} · {formatMoney(o.total)}
              </p>
              <h1 className="text-2xl sm:text-4xl font-extrabold tracking-tight">{hero.title}</h1>
              <p className="text-white/90 mt-1">{hero.text}</p>
            </div>
          </div>

          {!failed && (
            <div className="mt-8">
              <div className="relative h-2 rounded-full bg-white/25 overflow-hidden">
                <div
                  className="absolute inset-y-0 left-0 rounded-full bg-white transition-all duration-700"
                  style={{ width: `${progress}%` }}
                />
              </div>
              <ol className="grid grid-cols-5 mt-3">
                {STEPS.map(({ status, icon: Icon }, i) => {
                  const reached = i <= currentStep || o.status === 'DELIVERED'
                  return (
                    <li
                      key={status}
                      className={cn(
                        'flex flex-col items-center text-center gap-1 text-xs sm:text-sm font-semibold',
                        reached ? 'text-white' : 'text-white/50'
                      )}
                    >
                      <Icon className="w-5 h-5" />
                      {ORDER_STATUS[status].label}
                    </li>
                  )
                })}
              </ol>
            </div>
          )}
        </div>
      </section>

      <PageContainer>
        {PAYMENT_EXPLANATION[o.paymentStatus] && (
          <ArchiAlert variant={o.paymentStatus === 'UNKNOWN' ? 'warning' : 'info'} className="mb-6">
            {PAYMENT_EXPLANATION[o.paymentStatus]}
          </ArchiAlert>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
          <ArchiCard className="lg:col-span-2">
            <div className="flex items-center justify-between gap-3 mb-4">
              <h2 className="text-lg font-extrabold text-slate-900">Order activity</h2>
              {live && (
                <span className="flex items-center gap-1.5 text-xs font-semibold text-emerald-700">
                  <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping" /> Live
                </span>
              )}
            </div>
            <ol className="relative border-l-2 border-slate-200 ml-2 flex flex-col gap-5">
              {[...(history.data ?? [])].reverse().map((e, i) => (
                <li key={e.version + e.occurredAt} className="pl-6 relative">
                  <span
                    className={cn(
                      'absolute -left-[9px] top-0.5 w-4 h-4 rounded-full border-2 border-white',
                      i === 0 ? 'bg-emerald-500 ring-4 ring-emerald-100' : 'bg-slate-300'
                    )}
                  />
                  <div className="flex flex-wrap items-baseline justify-between gap-x-3">
                    <span className={cn('text-sm', i === 0 ? 'font-bold text-slate-900' : 'font-semibold text-slate-600')}>
                      {describeChange(e)}
                    </span>
                    <span className="text-xs text-slate-400 tabular-nums">{formatTime(e.occurredAt)}</span>
                  </div>
                  <div className="flex flex-wrap gap-1.5 mt-1.5">
                    <StatusPill {...ORDER_STATUS[e.status]} />
                    <StatusPill {...PAYMENT_STATUS[e.paymentStatus]} />
                  </div>
                </li>
              ))}
            </ol>
          </ArchiCard>

          <ArchiCard>
            <h2 className="text-lg font-extrabold text-slate-900 mb-1">Your order</h2>
            <p className="text-xs text-slate-500 mb-3">Placed {formatDateTime(o.createdAt)}</p>
            <ul className="text-sm divide-y divide-slate-100">
              {o.items.map((i) => (
                <li key={i.menuItemId} className="py-2 flex items-center justify-between gap-2">
                  <span className="flex items-center gap-2">
                    <span className="text-xl" aria-hidden>{dishEmoji(i.name)}</span>
                    <span>
                      <span className="font-bold text-emerald-700">{i.quantity}×</span> {i.name}
                    </span>
                  </span>
                  <span className="tabular-nums font-semibold">
                    {formatMoney({ amountMinor: i.unitPrice.amountMinor * i.quantity, currency: i.unitPrice.currency })}
                  </span>
                </li>
              ))}
              <li className="pt-3 flex justify-between text-base font-extrabold">
                <span>Total</span>
                <span className="tabular-nums">{formatMoney(o.total)}</span>
              </li>
            </ul>
            <div className="mt-4 rounded-2xl bg-slate-50 p-3 text-sm text-slate-700 flex items-start gap-2">
              <MapPin className="w-4 h-4 text-rose-500 mt-0.5 shrink-0" />
              <span>
                {o.deliveryAddress.line1}, {o.deliveryAddress.city}
                {o.deliveryAddress.instructions ? ` · ${o.deliveryAddress.instructions}` : ''}
              </span>
            </div>
          </ArchiCard>
        </div>
      </PageContainer>
    </>
  )
}
