import React, { useEffect, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArchiAlert } from '../components/ui'
import { useAuth } from '../context/AuthContext'

/** Redirect target of the OIDC login (`/callback`): exchanges the authorization code for tokens. */
export const CallbackPage: React.FC = () => {
  const { completeLogin, error } = useAuth()
  const navigate = useNavigate()
  const started = useRef(false)

  useEffect(() => {
    // The code is single-use: guard against React StrictMode running the effect twice.
    if (started.current) return
    started.current = true
    completeLogin()
      .then(() => navigate('/', { replace: true }))                  // each role lands on its own home
      .catch(() => undefined)
  }, [completeLogin, navigate])

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center p-4">
      {error ? (
        <ArchiAlert variant="error" title="Sign-in failed" className="max-w-md">
          <p className="font-medium">{error}</p>
          <p className="text-[11px] mt-2">
            <Link to="/login" className="underline">Try again</Link>
          </p>
        </ArchiAlert>
      ) : (
        <p className="text-sm text-slate-500">Completing sign-in…</p>
      )}
    </div>
  )
}
