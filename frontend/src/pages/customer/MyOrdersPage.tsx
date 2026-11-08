import React from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { catalogApi, ordersApi } from '../../services/api/eatsnowApi'
import { usePolling } from '../../hooks/usePolling'
import { ORDER_STATUS, PAYMENT_STATUS, formatDateTime, formatMoney } from '../../utils/format'
import { ArchiAlert, ArchiButton } from '../../components/ui'
import { StatusPill } from '../../components/app/StatusPill'
import { EmptyState, PageContainer, PageHeader } from '../../components/app/PageHeader'

export const MyOrdersPage: React.FC = () => {
  const { token } = useAuth()
  const orders = usePolling(() => ordersApi.list(token!), 5_000)
  const restaurants = usePolling(() => catalogApi.restaurants(token!), 60_000)
  const restaurantName = (id: string) => restaurants.data?.items.find((r) => r.id === id)?.name ?? 'Restaurant'

  return (
    <PageContainer>
      <PageHeader
        title="My orders"
        subtitle="Statuses update by themselves as each service processes the order."
        actions={
          <Link to="/restaurants">
            <ArchiButton size="sm">New order</ArchiButton>
          </Link>
        }
      />

      {orders.error && <ArchiAlert variant="error" title="Could not load your orders" className="mb-4">{orders.error}</ArchiAlert>}
      {orders.data && orders.data.items.length === 0 && (
        <EmptyState title="You have no orders yet">Choose a restaurant to place your first order.</EmptyState>
      )}

      <div className="flex flex-col gap-3">
        {orders.data?.items.map((o) => (
          <Link
            key={o.id}
            to={`/orders/${o.id}`}
            className="group rounded-2xl border border-slate-200/80 bg-white/90 p-4 shadow-xs hover:shadow-md transition-all flex flex-col sm:flex-row sm:items-center gap-3"
          >
            <div className="flex-1 min-w-0">
              <p className="font-bold text-slate-900">{restaurantName(o.restaurantId)}</p>
              <p className="text-xs text-slate-500">
                {formatDateTime(o.createdAt)} · {o.items.reduce((n, i) => n + i.quantity, 0)} items ·{' '}
                {formatMoney(o.total)}
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <StatusPill {...ORDER_STATUS[o.status]} />
              <StatusPill {...PAYMENT_STATUS[o.paymentStatus]} />
              <ChevronRight className="w-5 h-5 text-slate-300 group-hover:text-slate-500 hidden sm:block" />
            </div>
          </Link>
        ))}
      </div>
    </PageContainer>
  )
}
