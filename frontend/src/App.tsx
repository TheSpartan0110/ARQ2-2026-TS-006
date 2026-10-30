import React from 'react'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { ArchiHeader } from './components/ui'
import { AppRoutes } from './routes/AppRoutes'
import { UtensilsCrossed, Shield, Activity } from 'lucide-react'

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <div className="min-h-screen flex flex-col selection:bg-emerald-500 selection:text-white">
          <ArchiHeader />
          <main className="flex-1">
            <AppRoutes />
          </main>
          <footer className="border-t border-slate-200/80 bg-white/60 backdrop-blur-md py-6 text-xs text-slate-500">
            <div className="max-w-6xl mx-auto px-4 sm:px-6 flex flex-col sm:flex-row items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <UtensilsCrossed className="w-3.5 h-3.5 text-emerald-600" />
                <span className="font-semibold text-slate-700">EatsNow Platform</span>
                <span className="text-slate-300">•</span>
                <span>Case 6: Delivery & Order Orchestration</span>
              </div>
              <div className="flex items-center gap-4 text-slate-400">
                <span className="flex items-center gap-1">
                  <Shield className="w-3 h-3 text-emerald-500" />
                  OAuth2 / RS256 JWT
                </span>
                <span className="flex items-center gap-1">
                  <Activity className="w-3 h-3 text-teal-500" />
                  Hexagonal Microservices
                </span>
              </div>
            </div>
          </footer>
        </div>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
