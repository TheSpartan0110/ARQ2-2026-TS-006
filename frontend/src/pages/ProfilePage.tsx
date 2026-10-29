import React, { useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  ArchiCard,
  ArchiCardHeader,
  ArchiCardTitle,
  ArchiCardDescription,
  ArchiCardContent,
  ArchiButton,
  ArchiTextField,
  ArchiBadge,
  ArchiAlert,
} from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { Shield, Edit3, Key, Clock, Fingerprint } from 'lucide-react'

export const ProfilePage: React.FC = () => {
  const { profile, token, isLoading, updateDisplayName } = useAuth()
  const [editingName, setEditingName] = useState(false)
  const [nameInput, setNameInput] = useState('')
  const [isUpdating, setIsUpdating] = useState(false)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  if (isLoading) {
    return (
      <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center">
        <div className="flex flex-col items-center gap-2">
          <div className="w-8 h-8 border-4 border-emerald-500 border-t-transparent rounded-full animate-spin" />
          <span className="text-sm text-slate-500 font-medium">Loading session...</span>
        </div>
      </div>
    )
  }

  if (!token || !profile) {
    return <Navigate to="/login" replace />
  }

  // Parse JWT payload safely for debugging/inspection
  let decodedPayload: Record<string, unknown> = {}
  let decodedHeader: Record<string, unknown> = {}
  try {
    const parts = token.split('.')
    if (parts.length === 3) {
      decodedHeader = JSON.parse(atob(parts[0]))
      decodedPayload = JSON.parse(atob(parts[1]))
    }
  } catch {
    // ignore parse error
  }

  const handleStartEdit = () => {
    setNameInput(profile.displayName)
    setEditingName(true)
    setSuccessMsg(null)
    setErrorMsg(null)
  }

  const handleSaveName = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!nameInput.trim()) return

    setIsUpdating(true)
    setErrorMsg(null)
    setSuccessMsg(null)
    try {
      await updateDisplayName(nameInput.trim())
      setSuccessMsg('Display name updated successfully! (Idempotency-Key validated)')
      setEditingName(false)
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Failed to update name')
    } finally {
      setIsUpdating(false)
    }
  }

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-10 flex flex-col gap-8">
      {/* Page Title */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight text-slate-900">User Profile</h1>
          <p className="text-sm text-slate-500 mt-1">
            Authenticated via Identity & Access Service (Spec 3.2 & ADR 005)
          </p>
        </div>
        <div className="flex items-center gap-2">
          <ArchiBadge variant={profile.active ? 'success' : 'neutral'}>
            {profile.active ? 'Active Profile' : 'Inactive'}
          </ArchiBadge>
        </div>
      </div>

      {successMsg && <ArchiAlert variant="success">{successMsg}</ArchiAlert>}
      {errorMsg && <ArchiAlert variant="error">{errorMsg}</ArchiAlert>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Profile Card */}
        <div className="lg:col-span-2 flex flex-col gap-6">
          <ArchiCard>
            <ArchiCardHeader>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-2xl bg-emerald-100 text-emerald-700 flex items-center justify-center font-bold text-lg">
                    {profile.displayName.charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <ArchiCardTitle>{profile.displayName}</ArchiCardTitle>
                    <ArchiCardDescription>{profile.email}</ArchiCardDescription>
                  </div>
                </div>

                {!editingName && (
                  <ArchiButton variant="outline" size="sm" onPress={handleStartEdit}>
                    <Edit3 className="w-4 h-4" />
                    <span>Edit Name</span>
                  </ArchiButton>
                )}
              </div>
            </ArchiCardHeader>

            <ArchiCardContent>
              {editingName ? (
                <form onSubmit={handleSaveName} className="flex flex-col gap-4 p-4 bg-slate-50 rounded-xl mb-4">
                  <ArchiTextField
                    label="New Display Name"
                    description="1 to 100 characters. Roles, email and IDs remain immutable here."
                    value={nameInput}
                    onChange={setNameInput}
                    isRequired
                  />
                  <div className="flex gap-2 justify-end">
                    <ArchiButton
                      type="button"
                      variant="ghost"
                      size="sm"
                      onPress={() => setEditingName(false)}
                      isDisabled={isUpdating}
                    >
                      Cancel
                    </ArchiButton>
                    <ArchiButton
                      type="submit"
                      variant="primary"
                      size="sm"
                      isLoading={isUpdating}
                    >
                      Save with Idempotency
                    </ArchiButton>
                  </div>
                </form>
              ) : null}

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="p-3.5 bg-slate-50 border border-slate-100 rounded-xl flex flex-col gap-1">
                  <span className="text-xs font-semibold text-slate-500 uppercase flex items-center gap-1.5">
                    <Fingerprint className="w-3.5 h-3.5 text-slate-400" />
                    Profile UUID (Sub)
                  </span>
                  <span className="text-xs font-mono font-medium text-slate-800 break-all">
                    {profile.id}
                  </span>
                </div>

                <div className="p-3.5 bg-slate-50 border border-slate-100 rounded-xl flex flex-col gap-1">
                  <span className="text-xs font-semibold text-slate-500 uppercase flex items-center gap-1.5">
                    <Shield className="w-3.5 h-3.5 text-slate-400" />
                    Assigned Roles
                  </span>
                  <div className="flex flex-wrap gap-1.5 mt-0.5">
                    {profile.roles.map((role) => (
                      <ArchiBadge
                        key={role}
                        variant={
                          role === 'ADMIN'
                            ? 'admin'
                            : role === 'RESTAURANT'
                            ? 'restaurant'
                            : role === 'DRIVER'
                            ? 'driver'
                            : 'user'
                        }
                      >
                        {role}
                      </ArchiBadge>
                    ))}
                  </div>
                </div>

                {profile.restaurantId && (
                  <div className="p-3.5 bg-amber-50/50 border border-amber-200/60 rounded-xl flex flex-col gap-1">
                    <span className="text-xs font-semibold text-amber-700 uppercase">
                      Restaurant ID Link
                    </span>
                    <span className="text-xs font-mono font-medium text-amber-900 break-all">
                      {profile.restaurantId}
                    </span>
                  </div>
                )}

                {profile.driverId && (
                  <div className="p-3.5 bg-emerald-50/50 border border-emerald-200/60 rounded-xl flex flex-col gap-1">
                    <span className="text-xs font-semibold text-emerald-700 uppercase">
                      Driver ID Link
                    </span>
                    <span className="text-xs font-mono font-medium text-emerald-900 break-all">
                      {profile.driverId}
                    </span>
                  </div>
                )}
              </div>
            </ArchiCardContent>
          </ArchiCard>
        </div>

        {/* Live Token Inspection */}
        <div className="flex flex-col gap-6">
          <ArchiCard className="bg-slate-900 text-white border-slate-800">
            <ArchiCardHeader className="border-slate-800">
              <div className="flex items-center gap-2">
                <Key className="w-5 h-5 text-emerald-400" />
                <h3 className="font-bold text-base text-white">Live JWT Claims</h3>
              </div>
              <p className="text-xs text-slate-400">
                Issued RS256 token inspected in real time
              </p>
            </ArchiCardHeader>

            <ArchiCardContent className="flex flex-col gap-3">
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span className="flex items-center gap-1">
                  <Clock className="w-3.5 h-3.5 text-slate-500" />
                  Algorithm:
                </span>
                <span className="font-mono text-emerald-400">{String(decodedHeader.alg || 'RS256')}</span>
              </div>

              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>Key ID:</span>
                <span className="font-mono text-slate-300">{String(decodedHeader.kid || 'eatsnow-identity-key-1')}</span>
              </div>

              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>Issuer (iss):</span>
                <span className="font-mono text-slate-300">{String(decodedPayload.iss || 'eatsnow-identity')}</span>
              </div>

              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>Expires:</span>
                <span className="font-mono text-slate-300">
                  {decodedPayload.exp
                    ? new Date(Number(decodedPayload.exp) * 1000).toLocaleTimeString()
                    : '15 min'}
                </span>
              </div>

              <div className="mt-2 pt-3 border-t border-slate-800">
                <span className="text-[11px] uppercase font-semibold text-slate-400 block mb-1">
                  Raw Bearer Token:
                </span>
                <div className="bg-slate-950 p-2.5 rounded-lg text-[10px] font-mono text-emerald-400/90 break-all max-h-32 overflow-y-auto select-all">
                  {token}
                </div>
              </div>
            </ArchiCardContent>
          </ArchiCard>
        </div>
      </div>
    </div>
  )
}
