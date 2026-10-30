export type Role = 'USER' | 'ADMIN' | 'RESTAURANT' | 'DRIVER'

export interface Profile {
  id: string
  displayName: string
  email: string
  roles: Role[]
  restaurantId?: string | null
  driverId?: string | null
  active: boolean
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface ErrorResponse {
  code: string
  message: string
  correlationId: string
  details?: Record<string, unknown> | null
}
