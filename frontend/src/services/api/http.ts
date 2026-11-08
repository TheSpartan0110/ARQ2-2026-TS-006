import type { ErrorResponse } from '../../types/auth'

export class ApiError extends Error {
  code: string
  correlationId?: string
  status: number
  details?: Record<string, unknown> | null

  constructor(status: number, errorData: Partial<ErrorResponse>) {
    super(errorData.message || 'An unexpected API error occurred')
    this.name = 'ApiError'
    this.status = status
    this.code = errorData.code || 'UNKNOWN_ERROR'
    this.correlationId = errorData.correlationId
    this.details = errorData.details
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH'
  token?: string | null
  body?: unknown
  /** Mutating endpoints require Idempotency-Key: UUID (Spec 3.1). */
  idempotent?: boolean
}

export interface ApiResponse<T> {
  data: T
  /** X-Trace-Id of the request: opens this exact trace in Jaeger (Spec 6.1). */
  traceId: string | null
  correlationId: string | null
}

/**
 * Calls the API Gateway (same origin through the dev-server proxy). Every request carries a fresh X-Correlation-ID
 * (Spec 3.1); errors are thrown as ApiError with the Error schema of Spec 2.1.
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  const headers = new Headers()
  headers.set('X-Correlation-ID', crypto.randomUUID())
  if (options.token) headers.set('Authorization', `Bearer ${options.token}`)
  if (options.idempotent) headers.set('Idempotency-Key', crypto.randomUUID())
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')

  const response = await fetch(path, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
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

  return {
    data: (await response.json()) as T,
    traceId: response.headers.get('X-Trace-Id'),
    correlationId: response.headers.get('X-Correlation-ID'),
  }
}

/** Shorthand when only the body matters. */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return (await apiRequest<T>(path, options)).data
}

/** Human-readable message for an error thrown by the API layer. */
export function describeError(err: unknown, fallback = 'Something went wrong.'): string {
  if (err instanceof ApiError) return `${err.code}: ${err.message}`
  if (err instanceof Error) return err.message
  return fallback
}
