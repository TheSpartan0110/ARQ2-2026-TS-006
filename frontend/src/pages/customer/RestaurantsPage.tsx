import React from 'react'
import { Link } from 'react-router-dom'
import { Store, ChevronRight } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { catalogApi } from '../../services/api/eatsnowApi'
import { usePolling } from '../../hooks/usePolling'
import { ArchiAlert } from '../../components/ui'
import { EmptyState, PageContainer, PageHeader } from '../../components/app/PageHeader'

/** Catalog read path (Feature 2): served from Redis, independent of the purchasing services. */
export const RestaurantsPage: React.FC = () => {
  const { token } = useAuth()
  const { data, error, loading } = usePolling(() => catalogApi.restaurants(token!), 60_000)

  return (
    <PageContainer>
      <PageHeader title="Restaurants" subtitle="Choose a restaurant to see its menu." />

      {error && <ArchiAlert variant="error" title="Could not load restaurants" className="mb-4">{error}</ArchiAlert>}
      {loading && <p className="text-sm text-slate-500">Loading…</p>}
      {data && data.items.length === 0 && <EmptyState title="No restaurants yet" />}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {data?.items.map((r) => (
          <Link
            key={r.id}
            to={`/restaurants/${r.id}`}
            className="group rounded-2xl border border-slate-200/80 bg-white/90 p-5 shadow-xs hover:shadow-md hover:-translate-y-0.5 transition-all flex items-center gap-4"
          >
            <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-amber-100 to-orange-100 flex items-center justify-center">
              <Store className="w-6 h-6 text-amber-700" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="font-bold text-slate-900 truncate">{r.name}</p>
              <p className={r.available ? 'text-xs text-emerald-700' : 'text-xs text-slate-400'}>
                {r.available ? 'Open' : 'Closed'}
              </p>
            </div>
            <ChevronRight className="w-5 h-5 text-slate-300 group-hover:text-slate-500" />
          </Link>
        ))}
      </div>
    </PageContainer>
  )
}
