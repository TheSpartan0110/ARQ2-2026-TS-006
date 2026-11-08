import type { Profile } from '../../types/auth'
import { api } from './http'

export { ApiError } from './http'

export const authApi = {
  /**
   * Retrieves authenticated user's own profile (Spec 3.2).
   */
  async getProfile(token: string): Promise<Profile> {
    return api<Profile>('/api/v1/profiles/me', { token })
  },

  /**
   * Updates authenticated user's displayName (Spec 3.2).
   * Enforces Idempotency-Key: UUID.
   */
  async updateDisplayName(token: string, displayName: string): Promise<Profile> {
    return api<Profile>('/api/v1/profiles/me', { method: 'PATCH', token, body: { displayName }, idempotent: true })
  },

  /**
   * Retrieves the public JWKS of the Identity OIDC provider (ADR 005).
   */
  async getJwks(): Promise<Record<string, unknown>> {
    return api<Record<string, unknown>>('/oauth2/jwks')
  },
}
