import React, { useState } from 'react'
import { Bike, MapPin } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { catalogApi, driverApi } from '../../services/api/eatsnowApi'
import { describeError } from '../../services/api/http'
import { usePolling } from '../../hooks/usePolling'
import type { Delivery } from '../../types/domain'
import { DELIVERY_STATUS, formatTime } from '../../utils/format'
import { ArchiAlert, ArchiButton, ArchiCard } from '../../components/ui'
import { StatusPill } from '../../components/app/StatusPill'
import { EmptyState, PageContainer, PageHeader } from '../../components/app/PageHeader'

/**
 * Driver app (Spec 3.6). Deliveries are assigned automatically to on-duty drivers; only the pickup moves the order to
 * ON_THE_WAY and only the completion to DELIVERED.
 */
export const DriverPage: React.FC = () => {
  const { token } = useAuth()
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

  const deliveryCard = (delivery: Delivery) => (
    <div key={delivery.id} className="rounded-2xl border border-slate-200/80 bg-white/90 p-5 shadow-xs flex flex-col gap-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-bold text-slate-900">{restaurantName(delivery.restaurantId)}</p>
          <p className="font-mono text-xs text-slate-500">Order #{delivery.orderId.slice(0, 8)}</p>
        </div>
        <StatusPill {...DELIVERY_STATUS[delivery.status]} />
      </div>
      <p className="text-sm text-slate-700 flex items-start gap-1.5">
        <MapPin className="w-4 h-4 text-rose-500 mt-0.5 shrink-0" />
        {delivery.deliveryAddress.line1}, {delivery.deliveryAddress.city}
        {delivery.deliveryAddress.instructions ? ` (${delivery.deliveryAddress.instructions})` : ''}
      </p>
      {delivery.status === 'ASSIGNED' && (
        <ArchiButton
          isLoading={busy === delivery.id}
          onPress={() => void act(delivery.id, () => driverApi.pickUp(token!, delivery.id, delivery.version))}
        >
          Confirm pickup
        </ArchiButton>
      )}
      {delivery.status === 'PICKED_UP' && (
        <ArchiButton
          isLoading={busy === delivery.id}
          onPress={() => void act(delivery.id, () => driverApi.complete(token!, delivery.id, delivery.version))}
        >
          Confirm delivery
        </ArchiButton>
      )}
      {delivery.status === 'DELIVERED' && (
        <p className="text-xs text-slate-500">Delivered at {formatTime(delivery.updatedAt)}</p>
      )}
    </div>
  )

  return (
    <PageContainer>
      <PageHeader title="Deliveries" subtitle="Go on duty to receive orders automatically." />

      {error && <ArchiAlert variant="error" title="The action failed" className="mb-4">{error}</ArchiAlert>}
      {driver.error && <ArchiAlert variant="error" title="Could not load your driver profile" className="mb-4">{driver.error}</ArchiAlert>}

      {d && (
        <ArchiCard className="mb-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className={d.onDuty ? 'w-11 h-11 rounded-xl bg-emerald-100 flex items-center justify-center' : 'w-11 h-11 rounded-xl bg-slate-100 flex items-center justify-center'}>
              <Bike className={d.onDuty ? 'w-6 h-6 text-emerald-700' : 'w-6 h-6 text-slate-400'} />
            </div>
            <div>
              <p className="font-bold text-slate-900">{d.onDuty ? 'On duty' : 'Off duty'}</p>
              <p className="text-xs text-slate-500">
                {d.onDuty
                  ? d.activeDeliveryId
                    ? 'You have an active delivery. New ones arrive when you finish it.'
                    : 'Waiting for the next order…'
                  : 'You receive no new deliveries.'}
              </p>
            </div>
          </div>
          <ArchiButton
            variant={d.onDuty ? 'outline' : 'primary'}
            isLoading={busy === 'duty'}
            onPress={() => void act('duty', () => driverApi.setDuty(token!, !d.onDuty, d.version))}
          >
            {d.onDuty ? 'Go off duty' : 'Go on duty'}
          </ArchiButton>
        </ArchiCard>
      )}

      <h2 className="font-bold text-slate-900 mb-3">Current</h2>
      {active.length === 0 ? (
        <EmptyState title="No active delivery" />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">{active.map(deliveryCard)}</div>
      )}

      {done.length > 0 && (
        <>
          <h2 className="font-bold text-slate-900 mt-8 mb-3">Completed</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">{done.slice(0, 6).map(deliveryCard)}</div>
        </>
      )}
    </PageContainer>
  )
}
