import React from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Check, CircleDot } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { catalogApi, ordersApi } from '../../services/api/eatsnowApi'
import { usePolling } from '../../hooks/usePolling'
import type { OrderHistoryEntry, OrderStatus, PaymentStatus } from '../../types/domain'
import { ORDER_STATUS, PAYMENT_STATUS, formatDateTime, formatMoney, formatTime, isFinal } from '../../utils/format'
import { cn } from '../../utils/cn'
import { ArchiAlert, ArchiCard } from '../../components/ui'
import { StatusPill } from '../../components/app/StatusPill'
import { PageContainer, PageHeader } from '../../components/app/PageHeader'
import { TraceLinks } from '../../components/app/TraceLinks'

/** The mandatory order states of the case, in order (Spec 2.4), plus delivery completion. */
const STEPS: OrderStatus[] = ['CREATED', 'ACCEPTED', 'PREPARING', 'ON_THE_WAY', 'DELIVERED']

const PAYMENT_EXPLANATION: Partial<Record<PaymentStatus, string>> = {
  PENDING: 'Your order is saved. The payment is processed in the background; you do not have to wait on this page.',
  UNKNOWN:
    'The payment provider did not answer in time. We are confirming the charge with it; you will never be charged twice.',
  REFUND_PENDING: 'The restaurant could not take your order. Your refund has been requested.',
  REFUNDED: 'The restaurant could not take your order. Your payment was refunded.',
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
  const currentStep = STEPS.indexOf(o.status)

  return (
    <PageContainer>
      <Link to="/orders" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800 mb-4">
        <ArrowLeft className="w-4 h-4" /> My orders
      </Link>
      <PageHeader
        title={restaurantName}
        subtitle={`Placed ${formatDateTime(o.createdAt)} · ${formatMoney(o.total)}`}
        actions={
          <>
            <StatusPill {...ORDER_STATUS[o.status]} />
            <StatusPill {...PAYMENT_STATUS[o.paymentStatus]} />
          </>
        }
      />

      {o.status === 'PAYMENT_FAILED' && (
        <ArchiAlert variant="error" title="Payment declined" className="mb-4">
          The payment provider refused the card. The order was not sent to the restaurant and nothing was charged.
        </ArchiAlert>
      )}
      {o.status === 'REJECTED' && (
        <ArchiAlert variant="warning" title="The restaurant could not take your order" className="mb-4">
          {PAYMENT_EXPLANATION[o.paymentStatus] ?? 'Your payment is being refunded.'}
        </ArchiAlert>
      )}
      {!failed && PAYMENT_EXPLANATION[o.paymentStatus] && (
        <ArchiAlert variant={o.paymentStatus === 'UNKNOWN' ? 'warning' : 'info'} className="mb-4">
          {PAYMENT_EXPLANATION[o.paymentStatus]}
        </ArchiAlert>
      )}

      {/* Progress through the mandatory states */}
      {!failed && (
        <ArchiCard className="mb-6">
          <ol className="grid grid-cols-5 gap-2">
            {STEPS.map((step, i) => {
              const done = i < currentStep || o.status === 'DELIVERED'
              const current = i === currentStep && o.status !== 'DELIVERED'
              return (
                <li key={step} className="flex flex-col items-center text-center gap-2">
                  <div
                    className={cn(
                      'w-9 h-9 rounded-full flex items-center justify-center border-2 transition-colors',
                      done && 'bg-emerald-500 border-emerald-500 text-white',
                      current && 'border-emerald-500 text-emerald-600 bg-emerald-50 animate-pulse',
                      !done && !current && 'border-slate-200 text-slate-300 bg-white'
                    )}
                  >
                    {done ? <Check className="w-4 h-4" /> : <CircleDot className="w-4 h-4" />}
                  </div>
                  <span className={cn('text-xs font-semibold', done || current ? 'text-slate-800' : 'text-slate-400')}>
                    {ORDER_STATUS[step].label}
                  </span>
                </li>
              )
            })}
          </ol>
        </ArchiCard>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
        <ArchiCard className="lg:col-span-2">
          <h2 className="font-bold text-slate-900 mb-1">Timeline</h2>
          <p className="text-xs text-slate-500 mb-4">
            Every change is caused by an event from another service (payments, restaurant, delivery).
          </p>
          <ol className="relative border-l-2 border-slate-100 ml-2 flex flex-col gap-4">
            {(history.data ?? []).map((e) => (
              <li key={e.version + e.occurredAt} className="pl-5 relative">
                <span className="absolute -left-[7px] top-1 w-3 h-3 rounded-full bg-emerald-500 border-2 border-white" />
                <div className="flex flex-wrap items-baseline gap-x-2">
                  <span className="font-semibold text-slate-900 text-sm">{describeChange(e)}</span>
                  <span className="text-xs text-slate-400 tabular-nums">{formatTime(e.occurredAt)}</span>
                </div>
                <div className="text-xs text-slate-500">
                  {ORDER_STATUS[e.status].label} · {PAYMENT_STATUS[e.paymentStatus].label} · version {e.version}
                  {e.causedBy && (
                    <>
                      {' '}· event <code className="font-mono">{e.causedBy.slice(0, 8)}</code>
                    </>
                  )}
                </div>
              </li>
            ))}
          </ol>
          {live && <p className="text-xs text-slate-400 mt-4">Updating automatically…</p>}
        </ArchiCard>

        <div className="flex flex-col gap-6">
          <ArchiCard>
            <h2 className="font-bold text-slate-900 mb-2">Items</h2>
            <ul className="text-sm divide-y divide-slate-100">
              {o.items.map((i) => (
                <li key={i.menuItemId} className="py-1.5 flex justify-between gap-2">
                  <span>{i.quantity} × {i.name}</span>
                  <span className="tabular-nums">
                    {formatMoney({ amountMinor: i.unitPrice.amountMinor * i.quantity, currency: i.unitPrice.currency })}
                  </span>
                </li>
              ))}
              <li className="pt-2 flex justify-between font-bold">
                <span>Total</span>
                <span className="tabular-nums">{formatMoney(o.total)}</span>
              </li>
            </ul>
            <p className="text-xs text-slate-500 mt-3">
              Deliver to {o.deliveryAddress.line1}, {o.deliveryAddress.city}
              {o.deliveryAddress.instructions ? ` (${o.deliveryAddress.instructions})` : ''}
            </p>
          </ArchiCard>

          <ArchiCard>
            <h2 className="font-bold text-slate-900 mb-2">Traceability</h2>
            <TraceLinks correlationId={o.correlationId} createdAt={o.createdAt} />
          </ArchiCard>
        </div>
      </div>
    </PageContainer>
  )
}
