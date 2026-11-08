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
