import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import type { Role } from '../types/auth'
import { LoginPage } from '../pages/LoginPage'
import { CallbackPage } from '../pages/CallbackPage'
import { ProfilePage } from '../pages/ProfilePage'
import { RestaurantsPage } from '../pages/customer/RestaurantsPage'
import { RestaurantMenuPage } from '../pages/customer/RestaurantMenuPage'
import { MyOrdersPage } from '../pages/customer/MyOrdersPage'
import { OrderTrackingPage } from '../pages/customer/OrderTrackingPage'
import { RestaurantInboxPage } from '../pages/restaurant/RestaurantInboxPage'
import { DriverPage } from '../pages/driver/DriverPage'

/** Landing page of each role. */
export function homeFor(roles: Role[]): string {
  if (roles.includes('USER')) return '/restaurants'
  if (roles.includes('RESTAURANT')) return '/restaurant'
  if (roles.includes('DRIVER')) return '/driver'
  return '/profile'
}

/** Signed-in users only; with `role`, only that role (the services enforce it too, Spec 3.1). */
const RequireRole: React.FC<{ role?: Role; children: React.ReactNode }> = ({ role, children }) => {
  const { token, profile, isLoading } = useAuth()
  if (isLoading) return <p className="p-8 text-sm text-slate-500">Loading…</p>
  if (!token || !profile) return <Navigate to="/login" replace />
  if (role && !profile.roles.includes(role)) return <Navigate to={homeFor(profile.roles)} replace />
  return <>{children}</>
}

const Home: React.FC = () => {
  const { token, profile, isLoading } = useAuth()
  if (isLoading) return <p className="p-8 text-sm text-slate-500">Loading…</p>
  if (!token || !profile) return <Navigate to="/login" replace />
  return <Navigate to={homeFor(profile.roles)} replace />
}

export const AppRoutes: React.FC = () => {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/callback" element={<CallbackPage />} />

      <Route path="/restaurants" element={<RequireRole role="USER"><RestaurantsPage /></RequireRole>} />
      <Route path="/restaurants/:restaurantId" element={<RequireRole role="USER"><RestaurantMenuPage /></RequireRole>} />
      <Route path="/orders" element={<RequireRole role="USER"><MyOrdersPage /></RequireRole>} />
      <Route path="/orders/:orderId" element={<RequireRole role="USER"><OrderTrackingPage /></RequireRole>} />

      <Route path="/restaurant" element={<RequireRole role="RESTAURANT"><RestaurantInboxPage /></RequireRole>} />
      <Route path="/driver" element={<RequireRole role="DRIVER"><DriverPage /></RequireRole>} />

      <Route path="/profile" element={<RequireRole><ProfilePage /></RequireRole>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
