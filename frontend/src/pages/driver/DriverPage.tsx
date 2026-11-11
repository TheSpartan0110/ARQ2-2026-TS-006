import React, { useState } from 'react'
import { CheckCircle2, MapPin, Package, Power, Store } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { catalogApi, driverApi } from '../../services/api/eatsnowApi'
import { describeError } from '../../services/api/http'
import { usePolling } from '../../hooks/usePolling'
import type { Delivery } from '../../types/domain'
import { DELIVERY_STATUS, formatTime } from '../../utils/format'
import { cn } from '../../utils/cn'
import { ArchiAlert, ArchiButton } from '../../components/ui'
import { StatusPill } from '../../components/app/StatusPill'
import { EmptyState, PageContainer } from '../../components/app/PageHeader'
import { restaurantLook } from '../../utils/visuals'

/**
 * Driver app (Spec 3.6). Deliveries are assigned automatically to on-duty drivers; only the pickup moves the order to
 * ON_THE_WAY and only the completion to DELIVERED.
 */
export const DriverPage: React.FC = () => {
  const { token, profile } = useAuth()
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const driver = usePolling(() => driverApi.me(token!), 5_000)
  const deliveries = usePolling(() => driverApi.deliveries(token!), 3_000)
  const restaurants = usePolling(() => catalogApi.restaurants(token!), 60_000)
  const restaurantName = (id: string) => restaurants.data?.items.find((r) => r.id === id)?.name ?? 'Restaurant'

  const act = async (key: string, action: () => Promise<unknown>) => {
    setBusy(key)
    setError(null)
    try {
      await action()
      await Promise.all([driver.refresh(), deliveries.refresh()])
    } catch (err) {
      setError(describeError(err))
    } finally {
      setBusy(null)
    }
  }

  const active = (deliveries.data?.items ?? []).filter((d) => d.status === 'ASSIGNED' || d.status === 'PICKED_UP')
  const done = (deliveries.data?.items ?? []).filter((d) => d.status === 'DELIVERED').reverse()   // newest first
  const d = driver.data
  const firstName = profile?.displayName.split(' ')[0] ?? ''

  const activeCard = (delivery: Delivery) => {
    const name = restaurantName(delivery.restaurantId)
    const look = restaurantLook(delivery.restaurantId, name)
    const pickedUp = delivery.status === 'PICKED_UP'
    return (
      <div key={delivery.id} className="rounded-3xl bg-white border-2 border-emerald-500 shadow-lg overflow-hidden">
        <div className="bg-emerald-600 text-white px-5 py-3 flex items-center justify-between">
          <span className="font-bold flex items-center gap-2">
            <Package className="w-5 h-5" /> Order #{delivery.orderId.slice(0, 8)}
          </span>
          <StatusPill {...DELIVERY_STATUS[delivery.status]} className="bg-white" />
        </div>
        <div className="p-5">
          <ol className="relative border-l-2 border-dashed border-slate-300 ml-5 flex flex-col gap-6">
            <li className="pl-8 relative">
              <span
                className={cn(
                  'absolute -left-[21px] top-0 w-10 h-10 rounded-2xl bg-gradient-to-br flex items-center justify-center text-xl',
                  look.cover
                )}
                aria-hidden
              >
                {look.emoji}
              </span>
              <p className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1">
                <Store className="w-3.5 h-3.5" /> Pick up at
              </p>
              <p className={cn('font-extrabold', pickedUp ? 'text-slate-400 line-through' : 'text-slate-900')}>{name}</p>
            </li>
            <li className="pl-8 relative">
              <span className="absolute -left-[21px] top-0 w-10 h-10 rounded-2xl bg-rose-100 flex items-center justify-center">
                <MapPin className="w-5 h-5 text-rose-600" />
              </span>
              <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Deliver to</p>
              <p className="font-extrabold text-slate-900">
                {delivery.deliveryAddress.line1}, {delivery.deliveryAddress.city}
              </p>
              {delivery.deliveryAddress.instructions && (
                <p className="text-sm text-slate-500">“{delivery.deliveryAddress.instructions}”</p>
              )}
            </li>
          </ol>
          {delivery.status === 'ASSIGNED' && (
            <ArchiButton
              size="lg"
              className="w-full mt-6"
              isLoading={busy === delivery.id}
              onPress={() => void act(delivery.id, () => driverApi.pickUp(token!, delivery.id, delivery.version))}
            >
              I picked it up
            </ArchiButton>
          )}
          {pickedUp && (
            <ArchiButton
              size="lg"
              variant="secondary"
              className="w-full mt-6"
              isLoading={busy === delivery.id}
              leftIcon={<CheckCircle2 className="w-5 h-5" />}
              onPress={() => void act(delivery.id, () => driverApi.complete(token!, delivery.id, delivery.version))}
            >
              Delivered to the customer
            </ArchiButton>
          )}
        </div>
      </div>
    )
  }

  return (
    <>
      <section
        className={cn(
          'text-white transition-colors duration-500',
          d?.onDuty ? 'bg-gradient-to-br from-emerald-700 via-emerald-600 to-teal-500' : 'bg-slate-900'
        )}
      >
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8 flex flex-col sm:flex-row sm:items-center justify-between gap-6">
          <div className="flex items-center gap-5">
            <div className="w-20 h-20 rounded-3xl bg-white/15 border border-white/20 flex items-center justify-center text-5xl shrink-0" aria-hidden>
              🛵
            </div>
            <div>
              <p className="text-sm font-semibold text-white/80">Hi {firstName}</p>
              <h1 className="text-2xl sm:text-4xl font-extrabold tracking-tight">
                {d ? (d.onDuty ? 'You are on duty' : 'You are off duty') : 'Deliveries'}
              </h1>
              <p className="text-white/80 text-sm mt-1">
                {d?.onDuty
                  ? d.activeDeliveryId
                    ? 'Finish your current delivery to receive the next one.'
                    : 'Waiting for the next order…'
                  : 'Go on duty to receive orders automatically.'}
              </p>
            </div>
          </div>
          {d && (
            <div className="flex items-center gap-4">
              <div className="text-center">
                <p className="text-3xl font-extrabold">{done.length}</p>
                <p className="text-xs text-white/70">delivered</p>
              </div>
              <button
                type="button"
                disabled={busy === 'duty'}
                onClick={() => void act('duty', () => driverApi.setDuty(token!, !d.onDuty, d.version))}
                className={cn(
                  'flex items-center gap-2 rounded-2xl px-5 py-3 font-extrabold shadow-lg transition-transform active:scale-95 disabled:opacity-60',
                  d.onDuty ? 'bg-white/15 border border-white/30 hover:bg-white/25' : 'bg-emerald-500 hover:bg-emerald-400'
                )}
              >
                <Power className="w-5 h-5" />
                {d.onDuty ? 'Go off duty' : 'Go on duty'}
              </button>
            </div>
          )}
        </div>
      </section>

      <PageContainer>
        {error && <ArchiAlert variant="error" title="The action failed" className="mb-4">{error}</ArchiAlert>}
        {driver.error && <ArchiAlert variant="error" title="Could not load your driver profile" className="mb-4">{driver.error}</ArchiAlert>}

        <h2 className="text-xl font-extrabold text-slate-900 mb-4">Current delivery</h2>
        {active.length === 0 ? (
          <EmptyState title="No active delivery">
            {d?.onDuty ? 'A new delivery appears here as soon as a restaurant starts preparing an order.' : 'You are off duty.'}
          </EmptyState>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">{active.map(activeCard)}</div>
        )}

        {done.length > 0 && (
          <>
            <h2 className="text-xl font-extrabold text-slate-900 mt-10 mb-4">Completed</h2>
            <div className="flex flex-col gap-2">
              {done.slice(0, 8).map((delivery) => (
                <div key={delivery.id} className="rounded-2xl bg-white border border-slate-200 px-4 py-3 flex items-center gap-3">
                  <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="font-bold text-slate-900 truncate">{restaurantName(delivery.restaurantId)}</p>
                    <p className="text-xs text-slate-500 truncate">
                      {delivery.deliveryAddress.line1}, {delivery.deliveryAddress.city}
                    </p>
                  </div>
                  <span className="text-xs text-slate-400 tabular-nums">{formatTime(delivery.updatedAt)}</span>
                </div>
              ))}
            </div>
          </>
        )}
      </PageContainer>
    </>
  )
}
