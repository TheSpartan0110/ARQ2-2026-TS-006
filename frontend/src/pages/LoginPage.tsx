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
  ArchiBadge,
} from '../components/ui'
import { useAuth } from '../context/AuthContext'
import {
  ShieldCheck,
  Mail,
  Lock,
  User,
  UtensilsCrossed,
  Bike,
  ShieldAlert,
  Sparkles,
  ArrowRight,
  Info,
} from 'lucide-react'

interface DemoPersona {
  id: string
  title: string
  roleName: 'USER' | 'RESTAURANT' | 'DRIVER' | 'ADMIN'
  email: string
  pass: string
  icon: React.ReactNode
  description: string
  badgeVariant: 'user' | 'restaurant' | 'driver' | 'admin'
}

const DEMO_PERSONAS: DemoPersona[] = [
  {
    id: 'customer',
    title: 'Customer',
    roleName: 'USER',
    email: 'alex@example.test',
    pass: 'password123',
    icon: <User className="w-4 h-4 text-blue-600" />,
    description: 'Browse menus, place orders, track delivery',
    badgeVariant: 'user',
  },
  {
    id: 'restaurant',
    title: 'Restaurant Owner',
    roleName: 'RESTAURANT',
    email: 'elena@example.test',
    pass: 'password123',
    icon: <UtensilsCrossed className="w-4 h-4 text-amber-600" />,
    description: 'Manage menus, accept orders, prep status',
    badgeVariant: 'restaurant',
  },
  {
    id: 'driver',
    title: 'Courier Driver',
    roleName: 'DRIVER',
    email: 'sam@example.test',
    pass: 'password123',
    icon: <Bike className="w-4 h-4 text-emerald-600" />,
    description: 'Pickup packages, real-time dispatch GPS',
    badgeVariant: 'driver',
  },
  {
    id: 'admin',
    title: 'Platform Admin',
    roleName: 'ADMIN',
    email: 'admin@eatsnow.test',
    pass: 'password123',
    icon: <ShieldAlert className="w-4 h-4 text-purple-600" />,
    description: 'Full platform management & audits',
    badgeVariant: 'admin',
  },
]

export const LoginPage: React.FC = () => {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [selectedPersonaId, setSelectedPersonaId] = useState<string | null>(null)
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

  const handleSelectPersona = (persona: DemoPersona) => {
    clearError()
    setSelectedPersonaId(persona.id)
    setEmail(persona.email)
    setPassword(persona.pass)
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
            Select a verified platform persona below to test authentication, JWT validation, and RBAC authorization tokens.
          </p>
        </div>

        {/* Main Grid: Persona Selectors + Login Card */}
        <div className="w-full grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          
          {/* Left Column: Quick Test Personas (5 cols) */}
          <div className="lg:col-span-5 flex flex-col gap-3">
            <div className="flex items-center justify-between pb-1">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-amber-500" />
                1-Click Demo Personas
              </span>
              <span className="text-[11px] text-slate-400 font-medium">Click to populate</span>
            </div>

            <div className="grid grid-cols-1 gap-2.5">
              {DEMO_PERSONAS.map((persona) => {
                const isSelected = selectedPersonaId === persona.id
                return (
                  <button
                    key={persona.id}
                    type="button"
                    onClick={() => handleSelectPersona(persona)}
                    className={`group text-left p-3.5 rounded-2xl border transition-all duration-200 cursor-pointer flex items-center justify-between gap-3 ${
                      isSelected
                        ? 'bg-emerald-50/90 border-emerald-500 shadow-sm ring-2 ring-emerald-500/20'
                        : 'bg-white/80 hover:bg-white border-slate-200/80 hover:border-slate-300 shadow-2xs hover:shadow-xs'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div
                        className={`w-9 h-9 rounded-xl flex items-center justify-center transition-colors ${
                          isSelected ? 'bg-emerald-600 text-white' : 'bg-slate-100 group-hover:bg-slate-200/70'
                        }`}
                      >
                        {persona.icon}
                      </div>
                      <div className="flex flex-col">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-bold text-slate-900 group-hover:text-emerald-700 transition-colors">
                            {persona.title}
                          </span>
                          <ArchiBadge variant={persona.badgeVariant} className="text-[9px] py-0 px-1.5">
                            {persona.roleName}
                          </ArchiBadge>
                        </div>
                        <span className="text-[11px] text-slate-500 line-clamp-1">
                          {persona.description}
                        </span>
                      </div>
                    </div>

                    <ArrowRight
                      className={`w-4 h-4 transition-transform ${
                        isSelected
                          ? 'text-emerald-600 translate-x-0.5'
                          : 'text-slate-300 group-hover:text-slate-500 group-hover:translate-x-0.5'
                      }`}
                    />
                  </button>
                )
              })}
            </div>

            <div className="p-3 rounded-xl bg-slate-100/70 border border-slate-200/60 flex items-start gap-2.5 text-xs text-slate-500">
              <Info className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
              <span>
                Tokens are signed using asymmetric <strong>RS256</strong> with key rotation and published via <strong>/.well-known/jwks.json</strong>.
              </span>
            </div>
          </div>

          {/* Right Column: Sign In Card (7 cols) */}
          <div className="lg:col-span-7">
            <ArchiCard className="shadow-lg shadow-slate-200/50 border-slate-200/90">
              <ArchiCardHeader className="pb-4">
                <ArchiCardTitle className="text-xl sm:text-2xl font-bold">
                  Authenticate Account
                </ArchiCardTitle>
                <ArchiCardDescription>
                  Enter credentials to issue a cryptographically signed Bearer JWT token.
                </ArchiCardDescription>
              </ArchiCardHeader>

              <ArchiCardContent>
                {error && (
                  <ArchiAlert variant="error" title="Authentication Failed" className="mb-5">
                    <p className="font-medium">{error}</p>
                    <p className="text-[11px] text-rose-800 mt-1">
                      If the connection was refused, verify that the Identity Service is running on port 8082 (<code className="bg-rose-100 px-1 py-0.5 rounded font-mono">docker compose up -d postgres identity</code>).
                    </p>
                  </ArchiAlert>
                )}

                <form onSubmit={handleSubmit} className="flex flex-col gap-4.5">
                  <ArchiTextField
                    label="Email Address"
                    type="email"
                    placeholder="e.g. alex@example.test"
                    value={email}
                    onChange={(val) => {
                      setEmail(val)
                      setSelectedPersonaId(null)
                    }}
                    leftIcon={<Mail className="w-4 h-4" />}
                    isRequired
                  />

                  <ArchiTextField
                    label="Account Password"
                    type="password"
                    placeholder="Enter your password"
                    value={password}
                    onChange={(val) => {
                      setPassword(val)
                      setSelectedPersonaId(null)
                    }}
                    leftIcon={<Lock className="w-4 h-4" />}
                    isRequired
                  />

                  <div className="pt-2 flex flex-col gap-3">
                    <ArchiButton
                      type="submit"
                      variant="primary"
                      size="lg"
                      isLoading={isSubmitting}
                      className="w-full"
                      rightIcon={<ArrowRight className="w-4 h-4" />}
                    >
                      Sign In to Platform
                    </ArchiButton>

                    <p className="text-center text-xs text-slate-400">
                      Standardized under <strong>ADR 005</strong> • X-Correlation-ID injected on every request
                    </p>
                  </div>
                </form>
              </ArchiCardContent>
            </ArchiCard>
          </div>

        </div>
      </div>
    </div>
  )
}
