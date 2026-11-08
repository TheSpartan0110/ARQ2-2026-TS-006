import React, { useEffect, useState } from 'react'
import { ChefHat } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { catalogApi, restaurantApi } from '../../services/api/eatsnowApi'
import { describeError } from '../../services/api/http'
import { usePolling } from '../../hooks/usePolling'
import type { RestaurantOrder, RestaurantOrderStatus } from '../../types/domain'
import { RESTAURANT_ORDER_STATUS, formatMoney } from '../../utils/format'
import { cn } from '../../utils/cn'
import { ArchiAlert, ArchiButton } from '../../components/ui'
import { StatusPill } from '../../components/app/StatusPill'
import { EmptyState, PageContainer, PageHeader } from '../../components/app/PageHeader'

type Tab = 'TODO' | 'PREPARING' | 'REJECTED'

/** "To do" keeps an order on screen from arrival until preparation starts: new and accepted orders both need action. */
const TABS: { tab: Tab; label: string; statuses: RestaurantOrderStatus[] }[] = [
  { tab: 'TODO', label: 'To do', statuses: ['PENDING', 'ACCEPTED'] },
  { tab: 'PREPARING', label: 'Preparing', statuses: ['PREPARING'] },
  { tab: 'REJECTED', label: 'Rejected', statuses: ['REJECTED'] },
]

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

  // One request per status of the tab (the API filters by a single status); kitchen order: oldest first.
  const inbox = usePolling(async () => {
    const statuses = TABS.find((t) => t.tab === tab)!.statuses
    const pages = await Promise.all(statuses.map((s) => restaurantApi.inbox(token!, s)))
    return { items: pages.flatMap((p) => p.items) }
  }, 3_000)
  const { refresh: refreshInbox } = inbox
  useEffect(() => {
    void refreshInbox()                                 // show the new tab at once, not at the next poll
  }, [tab, refreshInbox])
  const restaurants = usePolling(() => catalogApi.restaurants(token!), 60_000)
  const restaurantName = restaurants.data?.items.find((r) => r.id === profile?.restaurantId)?.name ?? 'Your restaurant'

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
    <PageContainer>
      <PageHeader title={restaurantName} subtitle="Incoming orders. Paid orders only: unpaid orders never reach the kitchen." />

      <div className="flex gap-2 mb-5 overflow-x-auto">
        {TABS.map((t) => (
          <button
            key={t.tab}
            type="button"
            onClick={() => setTab(t.tab)}
            className={cn(
              'px-4 py-2 rounded-xl text-sm font-semibold border transition-colors whitespace-nowrap',
              tab === t.tab
                ? 'bg-slate-900 text-white border-slate-900'
                : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {error && <ArchiAlert variant="error" title="The action failed" className="mb-4">{error}</ArchiAlert>}
      {inbox.error && <ArchiAlert variant="error" title="Could not load orders" className="mb-4">{inbox.error}</ArchiAlert>}
      {inbox.data && inbox.data.items.length === 0 && <EmptyState title="No orders here" />}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {inbox.data?.items.map((o) => (
          <div key={o.orderId} className="rounded-2xl border border-slate-200/80 bg-white/90 p-5 shadow-xs flex flex-col gap-3">
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-2">
                <ChefHat className="w-5 h-5 text-amber-600" />
                <span className="font-mono text-xs text-slate-500">#{o.orderId.slice(0, 8)}</span>
              </div>
              <StatusPill {...RESTAURANT_ORDER_STATUS[o.status]} />
            </div>

            <ul className="text-sm">
              {o.items.map((i) => (
                <li key={i.menuItemId} className="flex justify-between">
                  <span>{i.quantity} × {i.name}</span>
                  <span className="tabular-nums text-slate-500">
                    {formatMoney({ amountMinor: i.unitPrice.amountMinor * i.quantity, currency: i.unitPrice.currency })}
                  </span>
                </li>
              ))}
            </ul>
            <p className="text-sm font-bold text-right">{formatMoney({ amountMinor: orderTotal(o), currency: 'COP' })}</p>
            {o.reason && <p className="text-xs text-rose-700">Reason: {o.reason}</p>}

            {o.status === 'PENDING' && rejecting !== o.orderId && (
              <div className="flex gap-2">
                <ArchiButton
                  className="flex-1"
                  isLoading={busy === o.orderId}
                  onPress={() => void act(o.orderId, () => restaurantApi.accept(token!, o.orderId, o.version))}
                >
                  Accept
                </ArchiButton>
                <ArchiButton variant="outline" onPress={() => setRejecting(o.orderId)}>
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
                isLoading={busy === o.orderId}
                onPress={() => void act(o.orderId, () => restaurantApi.startPreparation(token!, o.orderId, o.version))}
              >
                Start preparation
              </ArchiButton>
            )}

            {o.status === 'PREPARING' && (
              <p className="text-xs text-slate-500">A driver is assigned automatically and picks it up here.</p>
            )}
          </div>
        ))}
      </div>
    </PageContainer>
  )
}
