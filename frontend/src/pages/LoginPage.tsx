import React from 'react'
import { Navigate } from 'react-router-dom'
import { ArrowRight, UserPlus, UtensilsCrossed } from 'lucide-react'
import { ArchiAlert, ArchiButton, ArchiCard } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { REGISTRATION_URL } from '../auth/oidc'

/** Sign-in goes to the Identity login page (OIDC authorization code + PKCE, ADR 005). */
export const LoginPage: React.FC = () => {
  const { token, login, error } = useAuth()

  if (token) {
    return <Navigate to="/" replace />
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center p-4">
      <ArchiCard className="w-full max-w-md text-center">
        <div className="mx-auto w-14 h-14 rounded-2xl bg-gradient-to-tr from-emerald-600 to-teal-500 flex items-center justify-center text-white shadow-md mb-4">
          <UtensilsCrossed className="w-7 h-7" />
        </div>
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Welcome to EatsNow</h1>
        <p className="text-sm text-slate-500 mt-1 mb-6">Order from your favorite restaurants and track it live.</p>

        {error && (
          <ArchiAlert variant="error" title="Sign-in failed" className="mb-4 text-left">
            {error}
          </ArchiAlert>
        )}

        <div className="flex flex-col gap-3">
          <ArchiButton size="lg" className="w-full" onPress={() => void login()} rightIcon={<ArrowRight className="w-4 h-4" />}>
            Sign in
          </ArchiButton>
          <a
            href={REGISTRATION_URL}
            className="inline-flex items-center justify-center gap-2 w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
          >
            <UserPlus className="w-4 h-4" />
            Create an account
          </a>
        </div>
        <p className="text-xs text-slate-400 mt-5">
          Restaurants and drivers sign in with the account created for them by an administrator.
        </p>
      </ArchiCard>
    </div>
  )
}
