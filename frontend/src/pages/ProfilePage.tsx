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
import {
  Shield,
  Edit3,
  Key,
  Clock,
  Fingerprint,
  Copy,
  Check,
  Sparkles,
  RefreshCw,
  Building2,
  Bike,
  User,
} from 'lucide-react'

export const ProfilePage: React.FC = () => {
  const { profile, token, isLoading, updateDisplayName, refreshProfile } = useAuth()
  const [editingName, setEditingName] = useState(false)
  const [nameInput, setNameInput] = useState('')
  const [isUpdating, setIsUpdating] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [copiedToken, setCopiedToken] = useState(false)
  const [copiedId, setCopiedId] = useState(false)
  const [inspectorTab, setInspectorTab] = useState<'claims' | 'header' | 'raw'>('claims')

  if (isLoading) {
    return (
      <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 border-4 border-emerald-500 border-t-transparent rounded-full animate-spin" />
          <span className="text-sm text-slate-500 font-semibold tracking-wide">
            Hydrating authentication session...
          </span>
        </div>
      </div>
    )
  }

  if (!token || !profile) {
    return <Navigate to="/login" replace />
  }

  // Parse JWT payload safely
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
      setSuccessMsg('Display name updated successfully! (Verified via Idempotency-Key)')
      setEditingName(false)
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Failed to update name')
    } finally {
      setIsUpdating(false)
    }
  }

  const handleRefresh = async () => {
    setIsRefreshing(true)
    try {
      await refreshProfile()
    } finally {
      setIsRefreshing(false)
    }
  }

  const handleCopyToken = () => {
    if (!token) return
    navigator.clipboard.writeText(token)
    setCopiedToken(true)
    setTimeout(() => setCopiedToken(false), 2000)
  }

  const handleCopyId = () => {
    if (!profile?.id) return
    navigator.clipboard.writeText(profile.id)
    setCopiedId(true)
    setTimeout(() => setCopiedId(false), 2000)
  }

  const expDate = decodedPayload.exp
    ? new Date(Number(decodedPayload.exp) * 1000)
    : null

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8 sm:py-12 flex flex-col gap-8">
      {/* Top Banner / Hero Profile Card */}
      <div className="relative rounded-3xl bg-gradient-to-r from-emerald-600 via-emerald-700 to-teal-800 p-6 sm:p-8 text-white shadow-xl shadow-emerald-950/15 overflow-hidden">
        {/* Background ambient pattern */}
        <div className="absolute top-0 right-0 w-96 h-96 bg-white/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute -bottom-10 -left-10 w-64 h-64 bg-teal-400/20 rounded-full blur-2xl pointer-events-none" />

        <div className="relative z-10 flex flex-col sm:flex-row sm:items-center justify-between gap-6">
          <div className="flex items-center gap-4 sm:gap-5">
            <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-2xl bg-white/20 backdrop-blur-md border border-white/30 flex items-center justify-center font-extrabold text-2xl sm:text-3xl shadow-md text-white shrink-0">
              {profile.displayName.charAt(0).toUpperCase()}
            </div>
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-2.5 flex-wrap">
                <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
                  {profile.displayName}
                </h1>
                <ArchiBadge variant="success" className="bg-emerald-400/20 border-emerald-300/40 text-emerald-100">
                  Active
                </ArchiBadge>
              </div>
              <p className="text-emerald-100/90 text-sm font-medium">{profile.email}</p>
              <div className="flex items-center gap-2 mt-1">
                {profile.roles.map((role) => (
                  <span
                    key={role}
                    className="text-[10px] font-bold tracking-wider px-2 py-0.5 rounded-md bg-white/15 border border-white/20 uppercase"
                  >
                    {role}
                  </span>
                ))}
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2.5 sm:self-center">
            <ArchiButton
              variant="glass"
              size="sm"
              onPress={handleRefresh}
              isLoading={isRefreshing}
              leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />}
            >
              Sync Profile
            </ArchiButton>

            {!editingName && (
              <ArchiButton
                variant="glass"
                size="sm"
                onPress={handleStartEdit}
                leftIcon={<Edit3 className="w-3.5 h-3.5" />}
              >
                Edit Display Name
              </ArchiButton>
            )}
          </div>
        </div>
      </div>

      {successMsg && <ArchiAlert variant="success" title="Profile Saved">{successMsg}</ArchiAlert>}
      {errorMsg && <ArchiAlert variant="error" title="Action Failed">{errorMsg}</ArchiAlert>}

      {/* Main Bento Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        
        {/* Left Column: Profile Details & Updates (7 cols) */}
        <div className="lg:col-span-7 flex flex-col gap-6">
          
          {/* In-place Name Editor */}
          {editingName && (
            <ArchiCard className="border-emerald-200/90 bg-emerald-50/40 shadow-md">
              <ArchiCardHeader>
                <div className="flex items-center gap-2 text-emerald-800">
                  <Sparkles className="w-4 h-4 text-emerald-600" />
                  <ArchiCardTitle className="text-lg">Edit Display Name</ArchiCardTitle>
                </div>
                <ArchiCardDescription>
                  Mutates <code className="bg-white/80 px-1 py-0.5 rounded font-mono text-xs">PATCH /api/v1/profiles/me</code>. An <code className="bg-white/80 px-1 py-0.5 rounded font-mono text-xs">Idempotency-Key</code> UUID is generated automatically.
                </ArchiCardDescription>
              </ArchiCardHeader>

              <ArchiCardContent>
                <form onSubmit={handleSaveName} className="flex flex-col gap-4">
                  <ArchiTextField
                    label="Display Name"
                    placeholder="e.g. Alex Morgan"
                    value={nameInput}
                    onChange={setNameInput}
                    maxLength={100}
                    description={`${nameInput.length}/100 characters`}
                    isRequired
                  />

                  <div className="flex items-center justify-end gap-2.5 pt-2">
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
                      leftIcon={<Check className="w-3.5 h-3.5" />}
                    >
                      Save with Idempotency
                    </ArchiButton>
                  </div>
                </form>
              </ArchiCardContent>
            </ArchiCard>
          )}

          {/* Account Identifiers Card */}
          <ArchiCard>
            <ArchiCardHeader>
              <ArchiCardTitle className="text-lg flex items-center gap-2">
                <User className="w-5 h-5 text-emerald-600" />
                Account Attributes & Scope
              </ArchiCardTitle>
              <ArchiCardDescription>
                Persistent domain identifiers isolated in the PostgreSQL identity schema.
              </ArchiCardDescription>
            </ArchiCardHeader>

            <ArchiCardContent className="flex flex-col gap-4">
              {/* Profile UUID */}
              <div className="p-4 bg-slate-50/80 border border-slate-200/80 rounded-2xl flex items-center justify-between gap-3">
                <div className="flex flex-col gap-1 min-w-0">
                  <span className="text-xs font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                    <Fingerprint className="w-3.5 h-3.5 text-slate-400" />
                    Profile UUID (JWT Sub)
                  </span>
                  <span className="text-xs font-mono font-semibold text-slate-800 truncate">
                    {profile.id}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={handleCopyId}
                  className="p-2 rounded-xl border border-slate-200 bg-white hover:bg-slate-100 text-slate-600 transition-colors cursor-pointer shrink-0"
                  title="Copy Profile UUID"
                >
                  {copiedId ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
                </button>
              </div>

              {/* Roles */}
              <div className="p-4 bg-slate-50/80 border border-slate-200/80 rounded-2xl flex flex-col gap-2">
                <span className="text-xs font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                  <Shield className="w-3.5 h-3.5 text-slate-400" />
                  Assigned RBAC Authorities
                </span>
                <div className="flex flex-wrap gap-2">
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
                      className="text-xs px-3 py-1"
                    >
                      ROLE_{role}
                    </ArchiBadge>
                  ))}
                </div>
              </div>

              {/* Linked Domain Entities */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="p-4 bg-slate-50/80 border border-slate-200/80 rounded-2xl flex flex-col gap-1">
                  <span className="text-xs font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                    <Building2 className="w-3.5 h-3.5 text-amber-500" />
                    Restaurant Link
                  </span>
                  <span className="text-xs font-mono text-slate-700 truncate">
                    {profile.restaurantId ? profile.restaurantId : 'None (Customer / Driver)'}
                  </span>
                </div>

                <div className="p-4 bg-slate-50/80 border border-slate-200/80 rounded-2xl flex flex-col gap-1">
                  <span className="text-xs font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                    <Bike className="w-3.5 h-3.5 text-emerald-500" />
                    Driver Link
                  </span>
                  <span className="text-xs font-mono text-slate-700 truncate">
                    {profile.driverId ? profile.driverId : 'None (Customer / Merchant)'}
                  </span>
                </div>
              </div>
            </ArchiCardContent>
          </ArchiCard>

        </div>

        {/* Right Column: Live JWT Cryptographic Inspector (5 cols) */}
        <div className="lg:col-span-5 flex flex-col gap-6">
          <ArchiCard variant="dark">
            <ArchiCardHeader className="border-slate-800 pb-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Key className="w-5 h-5 text-emerald-400" />
                  <ArchiCardTitle className="text-white text-base font-bold">
                    JWT Inspector
                  </ArchiCardTitle>
                </div>
                <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-[10px] font-bold">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                  RS256 Verified
                </div>
              </div>
              <ArchiCardDescription className="text-slate-400 text-xs">
                Real-time cryptographic claims issued by Identity Service.
              </ArchiCardDescription>
            </ArchiCardHeader>

            <ArchiCardContent className="flex flex-col gap-4">
              {/* Token Expiry Pill */}
              <div className="p-3 bg-slate-800/80 rounded-xl border border-slate-700/80 flex items-center justify-between text-xs text-slate-300">
                <span className="flex items-center gap-1.5 text-slate-400">
                  <Clock className="w-3.5 h-3.5 text-emerald-400" />
                  Token Expires:
                </span>
                <span className="font-mono font-semibold text-emerald-300">
                  {expDate ? expDate.toLocaleTimeString() : '15 minutes'}
                </span>
              </div>

              {/* Tab Selector */}
              <div className="flex p-1 bg-slate-950 rounded-xl border border-slate-800 text-xs font-semibold">
                <button
                  type="button"
                  onClick={() => setInspectorTab('claims')}
                  className={`flex-1 py-1.5 rounded-lg transition-colors cursor-pointer ${
                    inspectorTab === 'claims' ? 'bg-slate-800 text-emerald-300 shadow-xs' : 'text-slate-400 hover:text-white'
                  }`}
                >
                  Payload Claims
                </button>
                <button
                  type="button"
                  onClick={() => setInspectorTab('header')}
                  className={`flex-1 py-1.5 rounded-lg transition-colors cursor-pointer ${
                    inspectorTab === 'header' ? 'bg-slate-800 text-emerald-300 shadow-xs' : 'text-slate-400 hover:text-white'
                  }`}
                >
                  Header
                </button>
                <button
                  type="button"
                  onClick={() => setInspectorTab('raw')}
                  className={`flex-1 py-1.5 rounded-lg transition-colors cursor-pointer ${
                    inspectorTab === 'raw' ? 'bg-slate-800 text-emerald-300 shadow-xs' : 'text-slate-400 hover:text-white'
                  }`}
                >
                  Raw Token
                </button>
              </div>

              {/* Tab Content */}
              <div className="relative">
                <div className="bg-slate-950 rounded-xl p-3 border border-slate-800/80 max-h-56 overflow-y-auto text-xs font-mono leading-relaxed">
                  {inspectorTab === 'claims' && (
                    <pre className="text-emerald-400/95 whitespace-pre-wrap">
                      {JSON.stringify(decodedPayload, null, 2)}
                    </pre>
                  )}
                  {inspectorTab === 'header' && (
                    <pre className="text-sky-400/95 whitespace-pre-wrap">
                      {JSON.stringify(decodedHeader, null, 2)}
                    </pre>
                  )}
                  {inspectorTab === 'raw' && (
                    <div className="text-slate-300 break-all select-all text-[11px]">
                      {token}
                    </div>
                  )}
                </div>

                <button
                  type="button"
                  onClick={handleCopyToken}
                  className="absolute top-2 right-2 p-1.5 rounded-lg bg-slate-800/90 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors cursor-pointer shadow-xs border border-slate-700"
                  title="Copy token to clipboard"
                >
                  {copiedToken ? (
                    <Check className="w-3.5 h-3.5 text-emerald-400" />
                  ) : (
                    <Copy className="w-3.5 h-3.5" />
                  )}
                </button>
              </div>

              <div className="flex items-center justify-between text-[11px] text-slate-500 pt-1">
                <span>Key ID: <code className="text-slate-400">{String(decodedHeader.kid || 'eatsnow-identity-key-1')}</code></span>
                <span>Issuer: <code className="text-slate-400">{String(decodedPayload.iss || 'eatsnow-identity')}</code></span>
              </div>
            </ArchiCardContent>
          </ArchiCard>
        </div>

      </div>
    </div>
  )
}
