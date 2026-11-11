import React from 'react'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { ArchiHeader } from './components/ui'
import { AppRoutes } from './routes/AppRoutes'
import { UtensilsCrossed } from 'lucide-react'

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <div className="min-h-screen flex flex-col selection:bg-emerald-500 selection:text-white">
          <ArchiHeader />
          <main className="flex-1">
            <AppRoutes />
          </main>
          <footer className="bg-slate-900 text-slate-400 py-8 text-sm mt-12">
            <div className="max-w-6xl mx-auto px-4 sm:px-6 flex flex-col sm:flex-row items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <UtensilsCrossed className="w-4 h-4 text-emerald-400" />
                <span className="font-bold text-white">EatsNow</span>
                <span>· Your favorite restaurants, at your door.</span>
              </div>
              <span>© {new Date().getFullYear()} EatsNow</span>
            </div>
          </footer>
        </div>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
