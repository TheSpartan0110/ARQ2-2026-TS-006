import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArchiButton,
  ArchiTextField,
  ArchiCard,
  ArchiCardHeader,
  ArchiCardTitle,
  ArchiCardDescription,
  ArchiCardContent,
  ArchiAlert,
} from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { ShieldCheck, Sparkles, User, ShieldAlert, Bike } from 'lucide-react'

export const LoginPage: React.FC = () => {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const { login, error, clearError } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!email || !password) return

    setIsSubmitting(true)
    try {
      await login(email, password)
      navigate('/profile')
    } catch {
      // error handled in AuthContext
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleQuickFill = (testEmail: string, testPass: string) => {
    clearError()
    setEmail(testEmail)
    setPassword(testPass)
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center p-4 sm:p-6">
      <div className="w-full max-w-md flex flex-col gap-6">
        <ArchiCard className="shadow-xl border-slate-200">
          <ArchiCardHeader className="text-center pb-6">
            <div className="w-12 h-12 mx-auto rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center mb-2">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <ArchiCardTitle className="text-2xl font-bold">Sign In to EatsNow</ArchiCardTitle>
            <ArchiCardDescription>
              Authenticate via OIDC / JWT with the Identity & Access Service
            </ArchiCardDescription>
          </ArchiCardHeader>

          <ArchiCardContent>
            {error && (
              <ArchiAlert variant="error" title="Authentication Error" className="mb-4">
                {error}
              </ArchiAlert>
            )}

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <ArchiTextField
                label="Email Address"
                type="email"
                placeholder="name@example.test"
                value={email}
                onChange={setEmail}
                isRequired
              />

              <ArchiTextField
                label="Password"
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={setPassword}
                isRequired
              />

              <ArchiButton
                type="submit"
                variant="primary"
                size="lg"
                isLoading={isSubmitting}
                className="w-full mt-2"
              >
                Sign In
              </ArchiButton>
            </form>
          </ArchiCardContent>
        </ArchiCard>

        {/* Quick Test Accounts Card */}
        <div className="bg-slate-50 border border-slate-200/90 rounded-2xl p-5 text-sm">
          <div className="flex items-center gap-2 font-semibold text-slate-800 mb-2">
            <Sparkles className="w-4 h-4 text-emerald-600" />
            <span>Seed Test Accounts (Spec 3.2):</span>
          </div>
          <p className="text-xs text-slate-500 mb-3">
            Click to fill pre-seeded test credentials directly:
          </p>

          <div className="flex flex-col gap-2">
            <button
              type="button"
              onClick={() => handleQuickFill('alex@example.test', 'password123')}
              className="flex items-center justify-between p-2.5 bg-white border border-slate-200 rounded-xl hover:border-emerald-300 hover:bg-emerald-50/50 transition-all text-left group cursor-pointer"
            >
              <div className="flex items-center gap-2.5">
                <div className="w-7 h-7 rounded-lg bg-blue-100 text-blue-700 flex items-center justify-center font-bold text-xs">
                  <User className="w-4 h-4" />
                </div>
                <div>
                  <div className="font-medium text-slate-800 text-xs">Alex (Customer)</div>
                  <div className="text-[11px] text-slate-500 font-mono">alex@example.test</div>
                </div>
              </div>
              <span className="text-[11px] font-semibold text-blue-700 bg-blue-50 px-2 py-0.5 rounded-full border border-blue-200">
                USER
              </span>
            </button>

            <button
              type="button"
              onClick={() => handleQuickFill('admin@eatsnow.test', 'password123')}
              className="flex items-center justify-between p-2.5 bg-white border border-slate-200 rounded-xl hover:border-purple-300 hover:bg-purple-50/50 transition-all text-left group cursor-pointer"
            >
              <div className="flex items-center gap-2.5">
                <div className="w-7 h-7 rounded-lg bg-purple-100 text-purple-700 flex items-center justify-center font-bold text-xs">
                  <ShieldAlert className="w-4 h-4" />
                </div>
                <div>
                  <div className="font-medium text-slate-800 text-xs">Admin Operator</div>
                  <div className="text-[11px] text-slate-500 font-mono">admin@eatsnow.test</div>
                </div>
              </div>
              <span className="text-[11px] font-semibold text-purple-700 bg-purple-50 px-2 py-0.5 rounded-full border border-purple-200">
                ADMIN
              </span>
            </button>

            <button
              type="button"
              onClick={() => handleQuickFill('sam@example.test', 'password123')}
              className="flex items-center justify-between p-2.5 bg-white border border-slate-200 rounded-xl hover:border-emerald-300 hover:bg-emerald-50/50 transition-all text-left group cursor-pointer"
            >
              <div className="flex items-center gap-2.5">
                <div className="w-7 h-7 rounded-lg bg-emerald-100 text-emerald-700 flex items-center justify-center font-bold text-xs">
                  <Bike className="w-4 h-4" />
                </div>
                <div>
                  <div className="font-medium text-slate-800 text-xs">Sam (Driver)</div>
                  <div className="text-[11px] text-slate-500 font-mono">sam@example.test</div>
                </div>
              </div>
              <span className="text-[11px] font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200">
                DRIVER
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
