import React, { useState } from 'react'
import { Check, CreditCard, Edit3, Plus, Trash2 } from 'lucide-react'
import { ArchiAlert, ArchiButton, ArchiCard, ArchiTextField } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { catalogApi } from '../services/api/eatsnowApi'
import { usePolling } from '../hooks/usePolling'
import { PageContainer, PageHeader } from '../components/app/PageHeader'
import { SANDBOX_CARDS, cardLabel, loadWallet, saveWallet, type PaymentCard } from '../services/wallet'
import type { Role } from '../types/auth'

const ROLE_LABEL: Record<Role, string> = {
  USER: 'Customer',
  RESTAURANT: 'Restaurant',
  DRIVER: 'Driver',
  ADMIN: 'Administrator',
}

const inputClass =
  'w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20'

/** Saved cards of a customer (see services/wallet.ts). */
const PaymentMethods: React.FC<{ profileId: string }> = ({ profileId }) => {
  const [cards, setCards] = useState<PaymentCard[]>(() => loadWallet(profileId))
  const available = SANDBOX_CARDS.filter((card) => !cards.some((c) => c.token === card.token))
  const [toAdd, setToAdd] = useState('')

  const update = (next: PaymentCard[]) => {
    setCards(next)
    saveWallet(profileId, next)
  }
  const add = () => {
    const card = SANDBOX_CARDS.find((c) => c.token === toAdd)
    if (card) update([...cards, card])
    setToAdd('')
  }

  return (
    <ArchiCard>
      <h2 className="font-bold text-slate-900">Payment methods</h2>
      <p className="text-xs text-slate-500 mb-4">
        Cards of the payment provider's test environment: each one behaves differently, so you can try every payment
        outcome. No real card is charged.
      </p>

      {cards.length === 0 && <p className="text-sm text-slate-500 mb-3">You have no saved cards.</p>}
      <ul className="flex flex-col gap-2 mb-4">
        {cards.map((card) => (
          <li
            key={card.token}
            className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white px-3 py-2.5"
          >
            <div className="flex items-center gap-3 min-w-0">
              <CreditCard className="w-5 h-5 text-slate-500 shrink-0" />
              <div className="min-w-0">
                <p className="text-sm font-semibold text-slate-900">{cardLabel(card)}</p>
                <p className="text-xs text-slate-500">{card.behavior}</p>
              </div>
            </div>
            <ArchiButton
              variant="ghost"
              size="sm"
              aria-label={`Remove ${cardLabel(card)}`}
              onPress={() => update(cards.filter((c) => c.token !== card.token))}
              leftIcon={<Trash2 className="w-4 h-4 text-slate-400" />}
            >
              <span className="sr-only">Remove</span>
            </ArchiButton>
          </li>
        ))}
      </ul>

      {available.length > 0 && (
        <div className="flex flex-col sm:flex-row gap-2">
          <select className={inputClass} value={toAdd} onChange={(e) => setToAdd(e.target.value)}>
            <option value="">Add a card…</option>
            {available.map((card) => (
              <option key={card.token} value={card.token}>
                {cardLabel(card)} · {card.behavior}
              </option>
            ))}
          </select>
          <ArchiButton isDisabled={!toAdd} onPress={add} leftIcon={<Plus className="w-4 h-4" />}>
            Add
          </ArchiButton>
        </div>
      )}
    </ArchiCard>
  )
}

export const ProfilePage: React.FC = () => {
  const { profile, token, updateDisplayName } = useAuth()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isRestaurant = profile?.roles.includes('RESTAURANT') ?? false
  const restaurants = usePolling(() => catalogApi.restaurants(token!), 60_000, isRestaurant)

  if (!profile) return null
  const restaurantName = restaurants.data?.items.find((r) => r.id === profile.restaurantId)?.name

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) return
    setSaving(true)
    setError(null)
    try {
      await updateDisplayName(name.trim())
      setEditing(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The name could not be saved')
    } finally {
      setSaving(false)
    }
  }

  return (
    <PageContainer>
      <PageHeader title="My account" />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        <ArchiCard>
          <div className="flex items-center gap-4 mb-5">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-emerald-500 to-teal-600 text-white font-extrabold text-xl flex items-center justify-center shrink-0">
              {profile.displayName.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0">
              <p className="text-lg font-bold text-slate-900 truncate">{profile.displayName}</p>
              <p className="text-sm text-slate-500 truncate">{profile.email}</p>
            </div>
          </div>

          {error && <ArchiAlert variant="error" title="The name was not saved" className="mb-4">{error}</ArchiAlert>}

          {editing ? (
            <form onSubmit={save} className="flex flex-col gap-3">
              <ArchiTextField label="Name" value={name} onChange={setName} maxLength={100} isRequired />
              <div className="flex justify-end gap-2">
                <ArchiButton type="button" variant="ghost" size="sm" onPress={() => setEditing(false)} isDisabled={saving}>
                  Cancel
                </ArchiButton>
                <ArchiButton type="submit" size="sm" isLoading={saving} leftIcon={<Check className="w-3.5 h-3.5" />}>
                  Save
                </ArchiButton>
              </div>
            </form>
          ) : (
            <dl className="text-sm divide-y divide-slate-100">
              <div className="py-2 flex justify-between gap-4">
                <dt className="text-slate-500">Account type</dt>
                <dd className="font-semibold text-slate-900">{profile.roles.map((r) => ROLE_LABEL[r]).join(', ')}</dd>
              </div>
              {isRestaurant && (
                <div className="py-2 flex justify-between gap-4">
                  <dt className="text-slate-500">Restaurant</dt>
                  <dd className="font-semibold text-slate-900">{restaurantName ?? '…'}</dd>
                </div>
              )}
              <div className="py-2 flex justify-between gap-4">
                <dt className="text-slate-500">Status</dt>
                <dd className="font-semibold text-emerald-700">{profile.active ? 'Active' : 'Inactive'}</dd>
              </div>
              <div className="pt-3">
                <ArchiButton
                  variant="outline"
                  size="sm"
                  onPress={() => {
                    setName(profile.displayName)
                    setEditing(true)
                  }}
                  leftIcon={<Edit3 className="w-3.5 h-3.5" />}
                >
                  Change name
                </ArchiButton>
              </div>
            </dl>
          )}
        </ArchiCard>

        {profile.roles.includes('USER') && <PaymentMethods profileId={profile.id} />}
      </div>
    </PageContainer>
  )
}
