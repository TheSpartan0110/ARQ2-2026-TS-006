import React, { createContext, useContext, useState, useEffect } from 'react'
import type { Profile } from '../types/auth'
import { authApi, ApiError } from '../services/api/authApi'

interface AuthContextType {
  token: string | null
  profile: Profile | null
  isLoading: boolean
  error: string | null
  login: (email: string, pass: string) => Promise<void>
  logout: () => void
  refreshProfile: () => Promise<void>
  updateDisplayName: (displayName: string) => Promise<void>
  clearError: () => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

const TOKEN_STORAGE_KEY = 'eatsnow_access_token'

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_STORAGE_KEY))
  const [profile, setProfile] = useState<Profile | null>(null)
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    async function initAuth() {
      const storedToken = localStorage.getItem(TOKEN_STORAGE_KEY)
      if (storedToken) {
        try {
          const userProfile = await authApi.getProfile(storedToken)
          setProfile(userProfile)
          setToken(storedToken)
        } catch {
          // Token expired or invalid
          localStorage.removeItem(TOKEN_STORAGE_KEY)
          setToken(null)
          setProfile(null)
        }
      }
      setIsLoading(false)
    }

    initAuth()
  }, [])

  const login = async (email: string, pass: string) => {
    setIsLoading(true)
    setError(null)
    try {
      const authResponse = await authApi.login(email, pass)
      const accessToken = authResponse.accessToken
      localStorage.setItem(TOKEN_STORAGE_KEY, accessToken)
      setToken(accessToken)

      const userProfile = await authApi.getProfile(accessToken)
      setProfile(userProfile)
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(`${err.code}: ${err.message}`)
      } else if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Failed to authenticate. Please check server connection.')
      }
      throw err
    } finally {
      setIsLoading(false)
    }
  }

  const logout = () => {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    setToken(null)
    setProfile(null)
    setError(null)
  }

  const refreshProfile = async () => {
    if (!token) return
    try {
      const refreshed = await authApi.getProfile(token)
      setProfile(refreshed)
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
      const updated = await authApi.updateDisplayName(token, newDisplayName)
      setProfile(updated)
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(`${err.code}: ${err.message}`)
      } else {
        setError('Failed to update display name.')
      }
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

