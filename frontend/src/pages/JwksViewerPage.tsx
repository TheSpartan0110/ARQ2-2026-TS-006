import React, { useEffect, useState } from 'react'
import {
  ArchiCard,
  ArchiCardHeader,
  ArchiCardTitle,
  ArchiCardDescription,
  ArchiCardContent,
  ArchiButton,
  ArchiAlert,
} from '../components/ui'
import { authApi } from '../services/api/authApi'
import { KeyRound, RefreshCw, ShieldCheck } from 'lucide-react'

export const JwksViewerPage: React.FC = () => {
  const [jwksData, setJwksData] = useState<Record<string, unknown> | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  const fetchJwks = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await authApi.getJwks()
      setJwksData(data)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to fetch JWKS keys')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchJwks()
  }, [])

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-10 flex flex-col gap-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight text-slate-900 flex items-center gap-2.5">
            <KeyRound className="w-8 h-8 text-emerald-600" />
            Public JWKS Key Registry
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Endpoint: <code className="bg-slate-100 px-1.5 py-0.5 rounded font-mono">/.well-known/jwks.json</code> (ADR 005)
          </p>
        </div>

        <ArchiButton variant="outline" size="sm" onPress={fetchJwks} isLoading={loading}>
          <RefreshCw className="w-4 h-4" />
          <span>Refresh Keys</span>
        </ArchiButton>
      </div>

      <div className="bg-emerald-50/70 border border-emerald-200/80 rounded-2xl p-5 flex items-start gap-3.5 text-sm text-emerald-950">
        <ShieldCheck className="w-6 h-6 text-emerald-600 shrink-0 mt-0.5" />
        <div className="flex flex-col gap-1">
          <span className="font-bold text-slate-900">Asymmetric Cryptography Architecture</span>
          <p className="text-xs text-slate-600 leading-relaxed">
            The API Gateway and all microservices download these public keys to verify JWT signatures locally.
            Identity Service never receives synchronous verification calls per request, avoiding any single point of failure.
          </p>
        </div>
      </div>

      {error && <ArchiAlert variant="error">{error}</ArchiAlert>}

      <ArchiCard className="border-slate-200 shadow-sm">
        <ArchiCardHeader>
          <ArchiCardTitle className="text-lg">Published JWK Set (RFC 7517)</ArchiCardTitle>
          <ArchiCardDescription>
            Live response from Identity & Access Service
          </ArchiCardDescription>
        </ArchiCardHeader>

        <ArchiCardContent>
          {loading ? (
            <div className="py-12 flex justify-center items-center text-sm text-slate-400">
              Fetching published keys...
            </div>
          ) : jwksData ? (
            <div className="bg-slate-900 text-emerald-400 p-4 rounded-xl font-mono text-xs overflow-x-auto max-h-[500px] leading-relaxed">
              <pre>{JSON.stringify(jwksData, null, 2)}</pre>
            </div>
          ) : null}
        </ArchiCardContent>
      </ArchiCard>
    </div>
  )
}
