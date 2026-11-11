import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight, Plus } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { catalogApi, ordersApi } from '../../services/api/eatsnowApi'
import { usePolling } from '../../hooks/usePolling'
import { ORDER_STATUS, PAYMENT_STATUS, formatDateTime, formatMoney, isFinal } from '../../utils/format'
import { ArchiAlert, ArchiButton } from '../../components/ui'
import { StatusPill } from '../../components/app/StatusPill'
import { EmptyState, PageContainer, PageHeader } from '../../components/app/PageHeader'
import { restaurantLook } from '../../utils/visuals'

export const MyOrdersPage: React.FC = () => {
  const { token } = useAuth()
  // Newest first; "Show more" asks for a longer first page (the API caps a page at 100 orders).
  const [limit, setLimit] = useState(10)
  const orders = usePolling(() => ordersApi.list(token!, limit), 5_000)
  const { refresh } = orders
  useEffect(() => {
    void refresh()
  }, [limit, refresh])
  const restaurants = usePolling(() => catalogApi.restaurants(token!), 60_000)
  const restaurantName = (id: string) => restaurants.data?.items.find((r) => r.id === id)?.name ?? 'Restaurant'

  const all = orders.data?.items ?? []
  const active = all.filter((o) => !isFinal(o.status, o.paymentStatus))
  const past = all.filter((o) => isFinal(o.status, o.paymentStatus))

  const orderRow = (o: (typeof all)[number]) => {
    const name = restaurantName(o.restaurantId)
    const look = restaurantLook(o.restaurantId, name)
    const count = o.items.reduce((n, i) => n + i.quantity, 0)
    return (
      <Link
        key={o.id}
        to={`/orders/${o.id}`}
        className="group rounded-3xl bg-white border border-slate-200 p-3 pr-5 shadow-sm hover:shadow-lg hover:-translate-y-0.5 transition-all flex items-center gap-4"
      >
        <div className={`w-16 h-16 rounded-2xl bg-gradient-to-br ${look.cover} flex items-center justify-center text-3xl shrink-0`} aria-hidden>
          {look.emoji}
        </div>
        <div className="flex-1 min-w-0">
          <p className="font-extrabold text-slate-900 truncate">{name}</p>
          <p className="text-xs text-slate-500">
            {formatDateTime(o.createdAt)} · {count} {count === 1 ? 'item' : 'items'}
          </p>
          <div className="flex flex-wrap gap-1.5 mt-1.5">
            <StatusPill {...ORDER_STATUS[o.status]} />
            <StatusPill {...PAYMENT_STATUS[o.paymentStatus]} />
          </div>
        </div>
        <div className="text-right shrink-0">
          <p className="font-extrabold text-slate-900 tabular-nums">{formatMoney(o.total)}</p>
          <ChevronRight className="w-5 h-5 text-slate-300 group-hover:text-emerald-600 ml-auto mt-1" />
        </div>
      </Link>
    )
  }

  return (
    <PageContainer>
      <PageHeader
        title="My orders"
        subtitle="Statuses update by themselves while your order moves forward."
        actions={
          <Link to="/restaurants">
            <ArchiButton leftIcon={<Plus className="w-4 h-4" />}>New order</ArchiButton>
          </Link>
        }
      />

      {orders.error && <ArchiAlert variant="error" title="Could not load your orders" className="mb-4">{orders.error}</ArchiAlert>}
      {orders.data && all.length === 0 && (
        <EmptyState title="You have no orders yet">Choose a restaurant to place your first order.</EmptyState>
      )}

      {active.length > 0 && (
        <>
          <h2 className="text-sm font-bold uppercase tracking-wider text-emerald-700 mb-3 flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" /> In progress
          </h2>
          <div className="flex flex-col gap-3 mb-8">{active.map(orderRow)}</div>
        </>
      )}
      {past.length > 0 && (
        <>
          <h2 className="text-sm font-bold uppercase tracking-wider text-slate-400 mb-3">Past orders</h2>
          <div className="flex flex-col gap-3">{past.map(orderRow)}</div>
        </>
      )}
      {orders.data?.nextCursor && limit < 100 && (
        <div className="flex justify-center mt-6">
          <ArchiButton variant="outline" onPress={() => setLimit((l) => Math.min(100, l + 10))}>
            Show more
          </ArchiButton>
        </div>
      )}
    </PageContainer>
  )
}
