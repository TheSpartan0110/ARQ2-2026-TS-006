import React from 'react'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { ArchiHeader } from './components/ui'
import { AppRoutes } from './routes/AppRoutes'

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col">
          <ArchiHeader />
          <main className="flex-1">
            <AppRoutes />
          </main>
        </div>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
