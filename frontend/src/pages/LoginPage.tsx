import React from 'react'
import { Navigate } from 'react-router-dom'
import {
  ArchiButton,
  ArchiCard,
  ArchiCardHeader,
  ArchiCardTitle,
  ArchiCardDescription,
  ArchiCardContent,
  ArchiAlert,
  ArchiBadge,
} from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { REGISTRATION_URL } from '../auth/oidc'
import { ShieldCheck, User, UtensilsCrossed, Bike, ShieldAlert, ArrowRight, Info, UserPlus } from 'lucide-react'

interface DemoPersona {
  id: string
  title: string
  roleName: 'USER' | 'RESTAURANT' | 'DRIVER' | 'ADMIN'
  email: string
  icon: React.ReactNode
  description: string
  badgeVariant: 'user' | 'restaurant' | 'driver' | 'admin'
}

/** Seeded accounts (password `password123`), typed on the Identity login page. */
const DEMO_PERSONAS: DemoPersona[] = [
  {
    id: 'customer',
    title: 'Customer',
    roleName: 'USER',
    email: 'alex@example.test',
    icon: <User className="w-4 h-4 text-blue-600" />,
    description: 'Browse menus, place orders, track delivery',
    badgeVariant: 'user',
  },
  {
    id: 'restaurant',
    title: 'Restaurant Owner',
    roleName: 'RESTAURANT',
    email: 'elena@example.test',
    icon: <UtensilsCrossed className="w-4 h-4 text-amber-600" />,
    description: 'Accept orders and update preparation status',
    badgeVariant: 'restaurant',
  },
  {
    id: 'driver',
    title: 'Courier Driver',
    roleName: 'DRIVER',
    email: 'sam@example.test',
    icon: <Bike className="w-4 h-4 text-emerald-600" />,
    description: 'Pick up and deliver assigned orders',
    badgeVariant: 'driver',
  },
  {
    id: 'admin',
    title: 'Platform Admin',
    roleName: 'ADMIN',
    email: 'admin@eatsnow.test',
    icon: <ShieldAlert className="w-4 h-4 text-purple-600" />,
    description: 'Provision restaurant and driver accounts',
    badgeVariant: 'admin',
  },
]

export const LoginPage: React.FC = () => {
  const { token, login, error } = useAuth()

  if (token) {
    return <Navigate to="/profile" replace />
  }

  return (
    <div className="relative min-h-[calc(100vh-4rem)] flex flex-col items-center justify-center p-4 sm:p-8 overflow-hidden">
      {/* Ambient glowing background orbs */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[350px] bg-gradient-to-tr from-emerald-400/15 to-teal-400/15 rounded-full blur-3xl pointer-events-none -z-10" />
      <div className="absolute bottom-10 right-1/4 w-[400px] h-[300px] bg-gradient-to-tr from-amber-400/10 to-orange-400/10 rounded-full blur-3xl pointer-events-none -z-10" />

      <div className="w-full max-w-4xl flex flex-col items-center gap-8 z-10">
        {/* Header Eyebrow */}
        <div className="text-center flex flex-col items-center gap-2 max-w-xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-50 border border-emerald-200/80 text-emerald-800 text-xs font-semibold shadow-2xs">
            <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
            <span>Identity & Access Microservice (OAuth2 / OIDC)</span>
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight text-slate-900">
            Welcome to <span className="bg-gradient-to-r from-emerald-600 to-teal-600 bg-clip-text text-transparent">EatsNow</span>
          </h1>
          <p className="text-sm sm:text-base text-slate-500 leading-relaxed">
            Sign in on the EatsNow Identity page. This app never sees your password: it receives a signed access token.
          </p>
        </div>

        <div className="w-full grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Left Column: seeded demo accounts (5 cols) */}
          <div className="lg:col-span-5 flex flex-col gap-3">
            <div className="flex items-center justify-between pb-1">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-400">Demo accounts</span>
              <span className="text-[11px] text-slate-400 font-medium">Password: <code>password123</code></span>
            </div>

            <div className="grid grid-cols-1 gap-2.5">
              {DEMO_PERSONAS.map((persona) => (
                <div
                  key={persona.id}
                  className="p-3.5 rounded-2xl border bg-white/80 border-slate-200/80 shadow-2xs flex items-center gap-3"
                >
                  <div className="w-9 h-9 rounded-xl flex items-center justify-center bg-slate-100">{persona.icon}</div>
                  <div className="flex flex-col min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold text-slate-900">{persona.title}</span>
                      <ArchiBadge variant={persona.badgeVariant} className="text-[9px] py-0 px-1.5">
                        {persona.roleName}
                      </ArchiBadge>
                    </div>
                    <span className="text-[11px] text-slate-500 font-mono truncate">{persona.email}</span>
                    <span className="text-[11px] text-slate-500 line-clamp-1">{persona.description}</span>
                  </div>
                </div>
              ))}
            </div>

            <div className="p-3 rounded-xl bg-slate-100/70 border border-slate-200/60 flex items-start gap-2.5 text-xs text-slate-500">
              <Info className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
              <span>
                Access tokens are RS256 JWTs published through <strong>/oauth2/jwks</strong> and validated locally by the
                Gateway and every service.
              </span>
            </div>
          </div>

          {/* Right Column: Sign In Card (7 cols) */}
          <div className="lg:col-span-7">
            <ArchiCard className="shadow-lg shadow-slate-200/50 border-slate-200/90">
              <ArchiCardHeader className="pb-4">
                <ArchiCardTitle className="text-xl sm:text-2xl font-bold">Sign in</ArchiCardTitle>
                <ArchiCardDescription>
                  OpenID Connect authorization-code flow with PKCE, served by Spring Authorization Server (ADR 005).
                </ArchiCardDescription>
              </ArchiCardHeader>

              <ArchiCardContent>
                {error && (
                  <ArchiAlert variant="error" title="Authentication Failed" className="mb-5">
                    <p className="font-medium">{error}</p>
                    <p className="text-[11px] text-rose-800 mt-1">
                      Verify that the Gateway (port 8090) and the Identity Service are running
                      (<code className="bg-rose-100 px-1 py-0.5 rounded font-mono">docker compose up -d</code>).
                    </p>
                  </ArchiAlert>
                )}

                <div className="flex flex-col gap-3">
                  <ArchiButton
                    variant="primary"
                    size="lg"
                    className="w-full"
                    onPress={() => void login()}
                    rightIcon={<ArrowRight className="w-4 h-4" />}
                  >
                    Sign in with EatsNow
                  </ArchiButton>
                  <a
                    href={REGISTRATION_URL}
                    className="inline-flex items-center justify-center gap-2 w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    <UserPlus className="w-4 h-4" />
                    New customer? Create an account
                  </a>
                  <p className="text-center text-xs text-slate-400">
                    Restaurant and driver accounts are created by an administrator and activated by email.
                  </p>
                </div>
              </ArchiCardContent>
            </ArchiCard>
          </div>
        </div>
      </div>
    </div>
  )
}
