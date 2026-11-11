import React, { useState } from 'react'
import { Check, ChefHat, Flame, X } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { catalogApi, restaurantApi } from '../../services/api/eatsnowApi'
import { describeError } from '../../services/api/http'
import { usePolling } from '../../hooks/usePolling'
import type { RestaurantOrder, RestaurantOrderStatus } from '../../types/domain'
import { RESTAURANT_ORDER_STATUS, formatMoney } from '../../utils/format'
import { cn } from '../../utils/cn'
import { ArchiAlert, ArchiButton } from '../../components/ui'
import { StatusPill } from '../../components/app/StatusPill'
import { EmptyState, PageContainer } from '../../components/app/PageHeader'
import { dishEmoji, restaurantLook } from '../../utils/visuals'

type Tab = 'TODO' | 'PREPARING' | 'REJECTED'

/** "To do" keeps an order on screen from arrival until preparation starts: new and accepted orders both need action. */
const TABS: { tab: Tab; label: string; statuses: RestaurantOrderStatus[] }[] = [
  { tab: 'TODO', label: 'To do', statuses: ['PENDING', 'ACCEPTED'] },
  { tab: 'PREPARING', label: 'Preparing', statuses: ['PREPARING'] },
  { tab: 'REJECTED', label: 'Rejected', statuses: ['REJECTED'] },
]
const ALL_STATUSES: RestaurantOrderStatus[] = ['PENDING', 'ACCEPTED', 'PREPARING', 'REJECTED']

const orderTotal = (o: RestaurantOrder) =>
  o.items.reduce((sum, i) => sum + i.unitPrice.amountMinor * i.quantity, 0)

/**
 * Restaurant inbox (Spec 3.5). Orders arrive only after the payment is authorized; each decision is local to the
 * Restaurant Service and reaches Orders later as an event.
 */
export const RestaurantInboxPage: React.FC = () => {
  const { token, profile } = useAuth()
  const [tab, setTab] = useState<Tab>('TODO')
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [rejecting, setRejecting] = useState<string | null>(null)
  const [reason, setReason] = useState('Kitchen closed due to a gas outage')

  // One request per status (the API filters by a single status); the tabs show their counts.
  const inbox = usePolling(async () => {
    const pages = await Promise.all(ALL_STATUSES.map((s) => restaurantApi.inbox(token!, s)))
    return pages.flatMap((p) => p.items)
  }, 3_000)
  const restaurants = usePolling(() => catalogApi.restaurants(token!), 60_000)
  const restaurantName = restaurants.data?.items.find((r) => r.id === profile?.restaurantId)?.name ?? 'Your restaurant'
  const look = restaurantLook(profile?.restaurantId ?? '', restaurantName)

  const ordersOf = (t: Tab) =>
    (inbox.data ?? []).filter((o) => TABS.find((x) => x.tab === t)!.statuses.includes(o.status))
  const newCount = (inbox.data ?? []).filter((o) => o.status === 'PENDING').length

  const act = async (orderId: string, action: () => Promise<unknown>) => {
    setBusy(orderId)
    setError(null)
    try {
      await action()
      setRejecting(null)
      await inbox.refresh()
    } catch (err) {
      setError(describeError(err))
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      <section className="bg-slate-900 text-white">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8 flex items-center gap-5">
          <div className={`w-20 h-20 rounded-3xl bg-gradient-to-br ${look.cover} flex items-center justify-center text-5xl shrink-0`} aria-hidden>
            {look.emoji}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-emerald-400">Kitchen dashboard</p>
            <h1 className="text-2xl sm:text-4xl font-extrabold tracking-tight truncate">{restaurantName}</h1>
            <p className="text-slate-400 text-sm mt-1">Only paid orders reach your kitchen.</p>
          </div>
          {newCount > 0 && (
            <div className="hidden sm:flex items-center gap-2 rounded-2xl bg-amber-400 text-slate-900 px-4 py-3 font-extrabold animate-pulse">
              <Flame className="w-5 h-5" /> {newCount} new
            </div>
          )}
        </div>
      </section>

      <PageContainer>
        <div className="flex gap-2 mb-6 overflow-x-auto">
          {TABS.map((t) => {
            const count = ordersOf(t.tab).length
            return (
              <button
                key={t.tab}
                type="button"
                onClick={() => setTab(t.tab)}
                className={cn(
                  'px-5 py-2.5 rounded-2xl text-sm font-bold border-2 transition-colors whitespace-nowrap flex items-center gap-2',
                  tab === t.tab
                    ? 'bg-slate-900 text-white border-slate-900'
                    : 'bg-white text-slate-600 border-slate-200 hover:border-slate-300'
                )}
              >
                {t.label}
                <span
                  className={cn(
                    'min-w-6 px-1.5 rounded-full text-xs',
                    tab === t.tab ? 'bg-emerald-500 text-white' : 'bg-slate-100 text-slate-600'
                  )}
                >
                  {count}
                </span>
              </button>
            )
          })}
        </div>

        {error && <ArchiAlert variant="error" title="The action failed" className="mb-4">{error}</ArchiAlert>}
        {inbox.error && <ArchiAlert variant="error" title="Could not load orders" className="mb-4">{inbox.error}</ArchiAlert>}
        {inbox.data && ordersOf(tab).length === 0 && <EmptyState title="No orders here" />}

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {ordersOf(tab).map((o) => (
            <div
              key={o.orderId}
              className={cn(
                'rounded-3xl bg-white p-5 shadow-sm flex flex-col gap-4 border-2',
                o.status === 'PENDING' ? 'border-amber-400' : 'border-slate-200'
              )}
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold text-slate-400">Order</p>
                  <p className="font-mono font-bold text-slate-900">#{o.orderId.slice(0, 8)}</p>
                </div>
                <StatusPill {...RESTAURANT_ORDER_STATUS[o.status]} />
              </div>

              <ul className="flex flex-col gap-2">
                {o.items.map((i) => (
                  <li key={i.menuItemId} className="flex items-center gap-3">
                    <span className="w-10 h-10 rounded-xl bg-orange-50 flex items-center justify-center text-xl" aria-hidden>
                      {dishEmoji(i.name)}
                    </span>
                    <span className="flex-1 text-sm">
                      <span className="font-extrabold text-slate-900">{i.quantity}×</span> {i.name}
                    </span>
                  </li>
                ))}
              </ul>
              <div className="flex justify-between items-baseline border-t border-slate-100 pt-3">
                <span className="text-sm text-slate-500">Total</span>
                <span className="text-lg font-extrabold">{formatMoney({ amountMinor: orderTotal(o), currency: 'COP' })}</span>
              </div>
              {o.reason && <p className="text-sm text-rose-700 bg-rose-50 rounded-xl px-3 py-2">Reason: {o.reason}</p>}

              {o.status === 'PENDING' && rejecting !== o.orderId && (
                <div className="flex gap-2">
                  <ArchiButton
                    className="flex-1"
                    isLoading={busy === o.orderId}
                    leftIcon={<Check className="w-4 h-4" />}
                    onPress={() => void act(o.orderId, () => restaurantApi.accept(token!, o.orderId, o.version))}
                  >
                    Accept
                  </ArchiButton>
                  <ArchiButton variant="outline" leftIcon={<X className="w-4 h-4" />} onPress={() => setRejecting(o.orderId)}>
                    Reject
                  </ArchiButton>
                </div>
              )}

              {o.status === 'PENDING' && rejecting === o.orderId && (
                <div className="flex flex-col gap-2">
                  <label className="text-xs font-semibold text-slate-600">
                    Reason (the customer is refunded)
                    <input
                      className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm outline-none focus:border-rose-400"
                      value={reason}
                      maxLength={300}
                      onChange={(e) => setReason(e.target.value)}
                    />
                  </label>
                  <div className="flex gap-2">
                    <ArchiButton
                      variant="destructive"
                      className="flex-1"
                      isDisabled={!reason.trim()}
                      isLoading={busy === o.orderId}
                      onPress={() => void act(o.orderId, () => restaurantApi.reject(token!, o.orderId, o.version, reason.trim()))}
                    >
                      Confirm rejection
                    </ArchiButton>
                    <ArchiButton variant="ghost" onPress={() => setRejecting(null)}>
                      Cancel
                    </ArchiButton>
                  </div>
                </div>
              )}

              {o.status === 'ACCEPTED' && (
                <ArchiButton
                  variant="secondary"
                  isLoading={busy === o.orderId}
                  leftIcon={<ChefHat className="w-4 h-4" />}
                  onPress={() => void act(o.orderId, () => restaurantApi.startPreparation(token!, o.orderId, o.version))}
                >
                  Start preparation
                </ArchiButton>
              )}

              {o.status === 'PREPARING' && (
                <p className="text-sm text-slate-600 bg-emerald-50 rounded-xl px-3 py-2">
                  🛵 A driver is assigned automatically and picks it up here.
                </p>
              )}
            </div>
          ))}
        </div>
      </PageContainer>
    </>
  )
}
