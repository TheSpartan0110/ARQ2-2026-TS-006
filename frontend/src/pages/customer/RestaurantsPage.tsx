import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Search } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { catalogApi } from '../../services/api/eatsnowApi'
import { usePolling } from '../../hooks/usePolling'
import { ArchiAlert } from '../../components/ui'
import { EmptyState, PageContainer } from '../../components/app/PageHeader'
import { restaurantLook } from '../../utils/visuals'

/** Catalog read path (Feature 2): served from Redis, independent of the purchasing services. */
export const RestaurantsPage: React.FC = () => {
  const { token, profile } = useAuth()
  const { data, error, loading } = usePolling(() => catalogApi.restaurants(token!), 60_000)
  const [query, setQuery] = useState('')

  const restaurants = (data?.items ?? []).map((r) => ({ ...r, look: restaurantLook(r.id, r.name) }))
  const cuisines = [...new Set(restaurants.map((r) => r.look.cuisine))]
  const visible = restaurants.filter((r) =>
    `${r.name} ${r.look.cuisine}`.toLowerCase().includes(query.trim().toLowerCase())
  )
  const firstName = profile?.displayName.split(' ')[0] ?? ''

  return (
    <>
      <section className="bg-gradient-to-br from-emerald-700 via-emerald-600 to-teal-500 text-white">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-10 sm:py-14 relative overflow-hidden">
          <div className="absolute -right-6 -top-4 text-[9rem] sm:text-[12rem] leading-none opacity-20 select-none rotate-12" aria-hidden>
            🍔
          </div>
          <div className="absolute right-40 bottom-0 text-7xl opacity-15 select-none -rotate-12 hidden md:block" aria-hidden>
            🍝
          </div>
          <p className="text-emerald-100 font-semibold">Hi {firstName} 👋</p>
          <h1 className="text-3xl sm:text-5xl font-extrabold tracking-tight mt-1 max-w-xl">
            What are you craving today?
          </h1>
          <div className="mt-6 max-w-lg relative">
            <Search className="w-5 h-5 text-slate-400 absolute left-4 top-1/2 -translate-y-1/2" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search restaurants or cuisines"
              className="w-full rounded-2xl bg-white text-slate-900 placeholder:text-slate-400 pl-12 pr-4 py-3.5 text-base shadow-lg shadow-emerald-900/20 outline-none focus:ring-4 focus:ring-white/40"
            />
          </div>
          {cuisines.length > 1 && (
            <div className="flex flex-wrap gap-2 mt-4">
              {cuisines.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => setQuery(query === c ? '' : c)}
                  className={
                    query === c
                      ? 'px-3.5 py-1.5 rounded-full text-sm font-semibold bg-white text-emerald-700'
                      : 'px-3.5 py-1.5 rounded-full text-sm font-semibold bg-white/15 hover:bg-white/25 text-white border border-white/20'
                  }
                >
                  {c}
                </button>
              ))}
            </div>
          )}
        </div>
      </section>

      <PageContainer>
        <div className="flex items-baseline justify-between mb-5">
          <h2 className="text-xl font-extrabold text-slate-900">Restaurants near you</h2>
          {data && <span className="text-sm text-slate-500">{visible.length} available</span>}
        </div>

        {error && <ArchiAlert variant="error" title="Could not load restaurants" className="mb-4">{error}</ArchiAlert>}
        {loading && <p className="text-sm text-slate-500">Loading…</p>}
        {data && visible.length === 0 && <EmptyState title="No restaurant matches your search" />}

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {visible.map((r) => (
            <Link
              key={r.id}
              to={`/restaurants/${r.id}`}
              className="group rounded-3xl bg-white border border-slate-200 shadow-sm hover:shadow-xl hover:-translate-y-1 transition-all overflow-hidden"
            >
              <div className={`h-40 bg-gradient-to-br ${r.look.cover} relative flex items-center justify-center`}>
                <span className="text-7xl drop-shadow-lg group-hover:scale-110 transition-transform" aria-hidden>
                  {r.look.emoji}
                </span>
                <span
                  className={
                    r.available
                      ? 'absolute top-3 left-3 px-2.5 py-1 rounded-full text-xs font-bold bg-white text-emerald-700 shadow'
                      : 'absolute top-3 left-3 px-2.5 py-1 rounded-full text-xs font-bold bg-slate-900/80 text-white'
                  }
                >
                  {r.available ? '● Open now' : 'Closed'}
                </span>
              </div>
              <div className="p-5 flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-lg font-extrabold text-slate-900 truncate">{r.name}</p>
                  <p className="text-sm text-slate-500">{r.look.cuisine}</p>
                </div>
                <span className="w-10 h-10 rounded-full bg-slate-900 text-white flex items-center justify-center group-hover:bg-emerald-600 transition-colors shrink-0">
                  <ArrowRight className="w-5 h-5" />
                </span>
              </div>
            </Link>
          ))}
        </div>
      </PageContainer>
    </>
  )
}
