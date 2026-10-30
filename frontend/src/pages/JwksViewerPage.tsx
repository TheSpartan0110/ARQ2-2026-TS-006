import React, { useEffect, useState } from 'react'
import {
  ArchiCard,
  ArchiCardHeader,
  ArchiCardTitle,
  ArchiCardDescription,
  ArchiCardContent,
  ArchiButton,
  ArchiAlert,
  ArchiBadge,
} from '../components/ui'
import { authApi } from '../services/api/authApi'
import { KeyRound, RefreshCw, ShieldCheck, Copy, Check, Lock, Cpu } from 'lucide-react'

interface JwkKey {
  kty?: string
  use?: string
  alg?: string
  kid?: string
  n?: string
  e?: string
  [key: string]: unknown
}

interface JwksPayload {
  keys?: JwkKey[]
  [key: string]: unknown
}

export const JwksViewerPage: React.FC = () => {
  const [jwksData, setJwksData] = useState<JwksPayload | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState<boolean>(false)

  const fetchJwks = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = (await authApi.getJwks()) as JwksPayload
      setJwksData(data)
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : 'Failed to fetch JWKS keys. Ensure Identity Service is running on port 8082.'
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchJwks()
  }, [])

  const handleCopyJson = () => {
    if (!jwksData) return
    navigator.clipboard.writeText(JSON.stringify(jwksData, null, 2))
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const primaryKey = jwksData?.keys?.[0]

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8 sm:py-12 flex flex-col gap-8">
      {/* Page Title & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 border border-emerald-200/80 flex items-center justify-center shadow-xs">
              <KeyRound className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-slate-900">
                Public JWKS Key Registry
              </h1>
              <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
                Standard RFC 7517 endpoint: <code className="bg-slate-100 px-1.5 py-0.5 rounded font-mono text-emerald-700">/.well-known/jwks.json</code>
              </p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <ArchiButton
            variant="outline"
            size="sm"
            onPress={fetchJwks}
            isLoading={loading}
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />}
          >
            Refresh Registry
          </ArchiButton>
        </div>
      </div>

      {/* Architecture Explainer Banner */}
      <div className="bg-gradient-to-r from-emerald-50 via-teal-50 to-slate-50 border border-emerald-200/80 rounded-2xl p-5 flex items-start gap-4 text-sm shadow-xs">
        <ShieldCheck className="w-6 h-6 text-emerald-600 shrink-0 mt-0.5" />
        <div className="flex flex-col gap-1">
          <span className="font-bold text-slate-900">Stateless Asymmetric Verification (ADR 005)</span>
          <p className="text-xs sm:text-sm text-slate-600 leading-relaxed">
            The API Gateway and all microservices (Catalog, Orders, Payments, etc.) fetch these public RSA keys to verify bearer JWTs locally. No synchronous RPC or database lookup is needed to validate user tokens.
          </p>
        </div>
      </div>

      {error && (
        <ArchiAlert variant="error" title="Connection Refused / Failed">
          <p>{error}</p>
          <p className="text-xs text-rose-800 mt-1">
            Make sure the Identity Service container is started: <code className="bg-rose-100 px-1 py-0.5 rounded font-mono">docker compose up -d postgres identity</code>
          </p>
        </ArchiAlert>
      )}

      {/* Primary Key Summary Metrics */}
      {primaryKey && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <div className="p-4 bg-white/85 backdrop-blur-md border border-slate-200/80 rounded-2xl shadow-xs flex flex-col gap-1">
            <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1">
              <Lock className="w-3.5 h-3.5 text-emerald-500" />
              Algorithm
            </span>
            <span className="text-base font-bold text-slate-900 font-mono">
              {primaryKey.alg || 'RS256'}
            </span>
            <span className="text-[10px] text-slate-400">RSA SHA-256</span>
          </div>

          <div className="p-4 bg-white/85 backdrop-blur-md border border-slate-200/80 rounded-2xl shadow-xs flex flex-col gap-1">
            <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1">
              <Cpu className="w-3.5 h-3.5 text-blue-500" />
              Key Type
            </span>
            <span className="text-base font-bold text-slate-900 font-mono">
              {primaryKey.kty || 'RSA'}
            </span>
            <span className="text-[10px] text-slate-400">Public Key Modulus</span>
          </div>

          <div className="p-4 bg-white/85 backdrop-blur-md border border-slate-200/80 rounded-2xl shadow-xs flex flex-col gap-1">
            <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
              Intended Use
            </span>
            <div className="mt-0.5">
              <ArchiBadge variant="success" className="text-xs">
                {primaryKey.use === 'sig' ? 'Signature (sig)' : String(primaryKey.use)}
              </ArchiBadge>
            </div>
            <span className="text-[10px] text-slate-400 mt-0.5">Token validation</span>
          </div>

          <div className="p-4 bg-white/85 backdrop-blur-md border border-slate-200/80 rounded-2xl shadow-xs flex flex-col gap-1">
            <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
              Key ID (kid)
            </span>
            <span className="text-xs font-mono font-bold text-slate-800 truncate" title={primaryKey.kid}>
              {primaryKey.kid || 'eatsnow-identity-key-1'}
            </span>
            <span className="text-[10px] text-emerald-600 font-semibold">Active Rotation Key</span>
          </div>
        </div>
      )}

      {/* Raw JWK Set Code Display */}
      <ArchiCard className="shadow-md">
        <ArchiCardHeader className="flex flex-row items-center justify-between pb-3">
          <div>
            <ArchiCardTitle className="text-lg">Published JWK Set Payload</ArchiCardTitle>
            <ArchiCardDescription>
              JSON representation consumed by Spring Security Resource Servers.
            </ArchiCardDescription>
          </div>

          {jwksData && (
            <button
              type="button"
              onClick={handleCopyJson}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold shadow-2xs transition-colors cursor-pointer"
            >
              {copied ? (
                <>
                  <Check className="w-3.5 h-3.5 text-emerald-600" />
                  <span className="text-emerald-700">Copied JSON</span>
                </>
              ) : (
                <>
                  <Copy className="w-3.5 h-3.5 text-slate-500" />
                  <span>Copy JWKS</span>
                </>
              )}
            </button>
          )}
        </ArchiCardHeader>

        <ArchiCardContent>
          {loading ? (
            <div className="py-16 flex flex-col items-center justify-center gap-2 text-sm text-slate-400">
              <div className="w-8 h-8 border-3 border-emerald-500 border-t-transparent rounded-full animate-spin" />
              <span>Querying Identity JWKS endpoint...</span>
            </div>
          ) : jwksData ? (
            <div className="relative rounded-2xl bg-slate-950 p-4 border border-slate-800/80 shadow-inner overflow-hidden">
              <pre className="text-emerald-400 font-mono text-xs leading-relaxed overflow-x-auto max-h-[460px] select-all">
                {JSON.stringify(jwksData, null, 2)}
              </pre>
            </div>
          ) : (
            <div className="py-12 text-center text-slate-400 text-sm">
              No keys available. Ensure the backend is reachable.
            </div>
          )}
        </ArchiCardContent>
      </ArchiCard>
    </div>
  )
}
