import type { Profile, ErrorResponse } from '../../types/auth'

export class ApiError extends Error {
  code: string
  correlationId?: string
  status: number

  constructor(status: number, errorData: Partial<ErrorResponse>) {
    super(errorData.message || 'An unexpected API error occurred')
    this.name = 'ApiError'
    this.status = status
    this.code = errorData.code || 'UNKNOWN_ERROR'
    this.correlationId = errorData.correlationId
  }
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const correlationId = crypto.randomUUID()
  const headers = new Headers(options.headers || {})

  headers.set('X-Correlation-ID', correlationId)
  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(endpoint, {
    ...options,
    headers,
  })

  if (!response.ok) {
    let errorData: Partial<ErrorResponse> = {}
    try {
      errorData = await response.json()
    } catch {
      errorData = { message: response.statusText }
    }
    throw new ApiError(response.status, errorData)
  }

  return response.json() as Promise<T>
}

export const authApi = {
  /**
   * Retrieves authenticated user's own profile (Spec 3.2).
   */
  async getProfile(token: string): Promise<Profile> {
    return request<Profile>('/api/v1/profiles/me', {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
      },
    })
  },

  /**
   * Updates authenticated user's displayName (Spec 3.2).
   * Enforces Idempotency-Key: UUID.
   */
  async updateDisplayName(token: string, displayName: string): Promise<Profile> {
    const idempotencyKey = crypto.randomUUID()
    return request<Profile>('/api/v1/profiles/me', {
      method: 'PATCH',
      headers: {
        Authorization: `Bearer ${token}`,
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify({ displayName }),
    })
  },

  /**
   * Retrieves the public JWKS of the Identity OIDC provider (ADR 005).
   */
  async getJwks(): Promise<Record<string, unknown>> {
    return request<Record<string, unknown>>('/oauth2/jwks', {
      method: 'GET',
    })
  },
}

