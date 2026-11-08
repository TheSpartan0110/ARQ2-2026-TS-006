import React, { createContext, useContext, useState, useEffect, useCallback } from 'react'
import type { User } from 'oidc-client-ts'
import type { Profile } from '../types/auth'
import { authApi, ApiError } from '../services/api/authApi'
import { userManager } from '../auth/oidc'

interface AuthContextType {
  token: string | null
  profile: Profile | null
  isLoading: boolean
  error: string | null
  /** Redirects to the Identity login page (authorization code + PKCE). */
  login: () => Promise<void>
  /** Finishes the login on /callback: exchanges the code for tokens. */
  completeLogin: () => Promise<void>
  logout: () => Promise<void>
  refreshProfile: () => Promise<void>
  updateDisplayName: (displayName: string) => Promise<void>
  clearError: () => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

function describe(err: unknown, fallback: string): string {
  if (err instanceof ApiError) return `${err.code}: ${err.message}`
  if (err instanceof Error) return err.message
  return fallback
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(null)
  const [profile, setProfile] = useState<Profile | null>(null)
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  const adoptUser = useCallback(async (user: User | null) => {
    if (!user || user.expired) {
      setToken(null)
      setProfile(null)
      return
    }
    setToken(user.access_token)
    setProfile(await authApi.getProfile(user.access_token))
  }, [])

  useEffect(() => {
    async function initAuth() {
      try {
        await adoptUser(await userManager.getUser())
      } catch {
        await userManager.removeUser()
        setToken(null)
        setProfile(null)
      } finally {
        setIsLoading(false)
      }
    }

    // The access token lasts 15 minutes. Renew through Identity: no password prompt while its session lives.
    const onExpired = () => {
      void userManager.signinRedirect({ state: window.location.pathname })
    }
    userManager.events.addAccessTokenExpired(onExpired)
    void initAuth()
    return () => userManager.events.removeAccessTokenExpired(onExpired)
  }, [adoptUser])

  const login = async () => {
    setError(null)
    await userManager.signinRedirect()
  }

  const completeLogin = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      await adoptUser(await userManager.signinRedirectCallback())
    } catch (err: unknown) {
      setError(describe(err, 'The sign-in could not be completed.'))
      throw err
    } finally {
      setIsLoading(false)
    }
  }, [adoptUser])

  const logout = async () => {
    const user = await userManager.getUser()
    setToken(null)
    setProfile(null)
    setError(null)
    // Ends the Identity session too (OIDC RP-initiated logout), then returns to the app.
    await userManager.signoutRedirect({ id_token_hint: user?.id_token })
  }

  const refreshProfile = async () => {
    if (!token) return
    try {
      setProfile(await authApi.getProfile(token))
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(`${err.code}: ${err.message}`)
      }
      throw err
    }
  }

  const updateDisplayName = async (newDisplayName: string) => {
    if (!token) return
    setIsLoading(true)
    setError(null)
    try {
      setProfile(await authApi.updateDisplayName(token, newDisplayName))
    } catch (err: unknown) {
      setError(err instanceof ApiError ? `${err.code}: ${err.message}` : 'Failed to update display name.')
      throw err
    } finally {
      setIsLoading(false)
    }
  }

  const clearError = () => setError(null)

  return (
    <AuthContext.Provider
      value={{
        token,
        profile,
        isLoading,
        error,
        login,
        completeLogin,
        logout,
        refreshProfile,
        updateDisplayName,
        clearError,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
