import React from 'react'
import { UtensilsCrossed, LogOut, KeyRound, User } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { ArchiButton } from './ArchiButton'
import { ArchiBadge } from './ArchiBadge'
import { useAuth } from '../../context/AuthContext'

export const ArchiHeader: React.FC = () => {
  const { profile, token, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <header className="sticky top-0 z-40 bg-white/80 backdrop-blur-md border-b border-slate-200">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
        {/* Brand */}
        <Link to="/" className="flex items-center gap-2.5 group">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-600 to-teal-500 flex items-center justify-center text-white shadow-sm shadow-emerald-500/20 group-hover:scale-105 transition-transform">
            <UtensilsCrossed className="w-5 h-5" />
          </div>
          <div className="flex flex-col">
            <span className="font-extrabold text-lg tracking-tight text-slate-900 flex items-center gap-1.5">
              EatsNow <span className="text-xs font-semibold px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-800">Demo</span>
            </span>
            <span className="text-xs text-slate-400 font-medium">Arquitectos del Caos UI</span>
          </div>
        </Link>

        {/* Navigation & User actions */}
        <div className="flex items-center gap-3">
          <Link to="/jwks">
            <ArchiButton variant="ghost" size="sm" className="hidden sm:inline-flex">
              <KeyRound className="w-4 h-4 text-slate-500" />
              <span>JWKS Keys</span>
            </ArchiButton>
          </Link>

          {token && profile ? (
            <div className="flex items-center gap-3 pl-3 border-l border-slate-200">
              <div className="hidden sm:flex flex-col text-right">
                <span className="text-sm font-semibold text-slate-800">{profile.displayName}</span>
                <div className="flex gap-1 justify-end">
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
              <ArchiButton variant="outline" size="sm" onPress={handleLogout}>
                <LogOut className="w-4 h-4" />
                <span className="hidden sm:inline">Logout</span>
              </ArchiButton>
            </div>
          ) : (
            <Link to="/login">
              <ArchiButton variant="primary" size="sm">
                <User className="w-4 h-4" />
                <span>Login</span>
              </ArchiButton>
            </Link>
          )}
        </div>
      </div>
    </header>
  )
}
