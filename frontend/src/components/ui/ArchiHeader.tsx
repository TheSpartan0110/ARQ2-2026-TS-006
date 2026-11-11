import React from 'react'
import { UtensilsCrossed, LogOut, User, Store, ReceiptText, ChefHat, Bike } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { ArchiButton } from './ArchiButton'
import { ArchiBadge } from './ArchiBadge'
import { useAuth } from '../../context/AuthContext'
import { cn } from '../../utils/cn'
import type { Role } from '../../types/auth'

const ROLE_LABEL: Record<Role, string> = {
  USER: 'Customer',
  RESTAURANT: 'Restaurant',
  DRIVER: 'Driver',
  ADMIN: 'Admin',
}

export const ArchiHeader: React.FC = () => {
  const { profile, token, logout } = useAuth()
  const location = useLocation()

  // Signs out of Identity too; Identity then redirects back to the app.
  const handleLogout = () => {
    void logout()
  }

  const isProfileActive = location.pathname === '/profile'

  // Navigation of each role; the routes and the services enforce the same roles.
  const roles = profile?.roles ?? []
  const navItems = [
    ...(roles.includes('USER')
      ? [
          { to: '/restaurants', label: 'Restaurants', icon: <Store className="w-4 h-4 text-emerald-500" /> },
          { to: '/orders', label: 'My orders', icon: <ReceiptText className="w-4 h-4 text-emerald-500" /> },
        ]
      : []),
    ...(roles.includes('RESTAURANT')
      ? [{ to: '/restaurant', label: 'Orders', icon: <ChefHat className="w-4 h-4 text-amber-500" /> }]
      : []),
    ...(roles.includes('DRIVER')
      ? [{ to: '/driver', label: 'Deliveries', icon: <Bike className="w-4 h-4 text-emerald-500" /> }]
      : []),
  ]

  return (
    <header className="sticky top-0 z-40 bg-white/75 backdrop-blur-xl border-b border-slate-200/80 shadow-xs">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
        {/* Brand */}
        <Link to="/" className="flex items-center gap-3 group">
          <div className="relative w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-600 via-emerald-500 to-teal-500 flex items-center justify-center text-white shadow-md shadow-emerald-500/20 group-hover:shadow-lg group-hover:shadow-emerald-500/30 group-hover:scale-105 transition-all duration-200">
            <UtensilsCrossed className="w-5 h-5 transition-transform group-hover:rotate-6" />
            <div className="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-amber-400 border-2 border-white" />
          </div>
          <div className="flex flex-col">
            <span className="font-extrabold text-xl tracking-tight text-slate-900">
              Eats<span className="text-emerald-600">Now</span>
            </span>
            <span className="text-[11px] text-slate-500 font-medium tracking-tight">Food, delivered fast</span>
          </div>
        </Link>

        {/* Navigation & User actions */}
        <div className="flex items-center gap-2.5 sm:gap-3">
          {token &&
            navItems.map((item) => (
              <Link key={item.to} to={item.to}>
                <ArchiButton
                  variant={location.pathname.startsWith(item.to) ? 'secondary' : 'ghost'}
                  size="sm"
                  leftIcon={item.icon}
                >
                  <span className="hidden sm:inline">{item.label}</span>
                </ArchiButton>
              </Link>
            ))}

          {token && profile ? (
            <div className="flex items-center gap-2.5 sm:gap-3 pl-2 sm:pl-3 border-l border-slate-200/80">
              <Link
                to="/profile"
                className={cn(
                  'flex items-center gap-2.5 px-2 py-1 rounded-xl transition-all',
                  isProfileActive ? 'bg-emerald-50 border border-emerald-200/80 shadow-2xs' : 'hover:bg-slate-100/60'
                )}
              >
                <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 text-white font-bold text-xs flex items-center justify-center shadow-xs">
                  {profile.displayName ? profile.displayName.charAt(0).toUpperCase() : 'U'}
                </div>
                <div className="hidden md:flex flex-col text-left">
                  <span className="text-xs font-bold text-slate-800 leading-tight">
                    {profile.displayName}
                  </span>
                  <div className="flex gap-1 mt-0.5">
                    {profile.roles.slice(0, 1).map((role) => (
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
                        className="py-0 px-1.5 text-[9px]"
                      >
                        {ROLE_LABEL[role]}
                      </ArchiBadge>
                    ))}
                  </div>
                </div>
              </Link>

              <ArchiButton
                variant="outline"
                size="sm"
                onPress={handleLogout}
                leftIcon={<LogOut className="w-3.5 h-3.5 text-slate-500" />}
              >
                <span className="hidden sm:inline">Logout</span>
              </ArchiButton>
            </div>
          ) : (
            <Link to="/login">
              <ArchiButton
                variant="primary"
                size="sm"
                leftIcon={<User className="w-4 h-4" />}
              >
                Sign In
              </ArchiButton>
            </Link>
          )}
        </div>
      </div>
    </header>
  )
}
