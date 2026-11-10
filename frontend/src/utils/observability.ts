/**
 * Links from the app to the trace inspector (Jaeger, ADR 003). Every trace of an order carries the span attribute
 * eatsnow.correlation_id (Spec 6.3), so one search returns its whole lifecycle.
 */
export const JAEGER_URL: string = import.meta.env.VITE_JAEGER_URL ?? 'http://localhost:16686'

export function jaegerOrderSearch(correlationId: string): string {
  const tags = encodeURIComponent(JSON.stringify({ 'eatsnow.correlation_id': correlationId }))
  return `${JAEGER_URL}/search?service=orders&tags=${tags}&limit=50&lookback=2d`
}

export function jaegerTrace(traceId: string): string {
  return `${JAEGER_URL}/trace/${traceId}`
}

/** Path of the Jaeger query API through the dev-server proxy (vite.config.ts). */
const JAEGER_API = '/jaeger-api/v3/traces'

/**
 * Trace id of the request that created the order: the only trace whose orders span is `POST /api/v1/orders` and
 * carries the order's correlation id. Looked up rather than remembered, so it survives reloads and "My orders".
 * Returns null when Jaeger is unreachable or the trace has expired.
 */
export async function findCreationTraceId(correlationId: string, createdAt: string): Promise<string | null> {
  const created = new Date(createdAt).getTime()
  const params = new URLSearchParams({
    'query.service_name': 'orders',
    'query.operation_name': 'http post /api/v1/orders',
    'query.attributes': JSON.stringify({ 'eatsnow.correlation_id': correlationId }),
    'query.start_time_min': new Date(created - 60_000).toISOString(),
    'query.start_time_max': new Date(created + 60_000).toISOString(),
    'query.num_traces': '1',
  })
  try {
    const response = await fetch(`${JAEGER_API}?${params}`)
    if (!response.ok) return null
    const body = await response.json()
    return body?.result?.resourceSpans?.[0]?.scopeSpans?.[0]?.spans?.[0]?.traceId ?? null
  } catch {
    return null
  }
}
