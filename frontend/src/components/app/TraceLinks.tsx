import React from 'react'
import { Activity } from 'lucide-react'
import { jaegerOrderSearch, jaegerTrace } from '../../utils/observability'

/**
 * Shows the workflow correlation id of an order and opens every trace of its lifecycle in Jaeger (Feature 5).
 */
export const TraceLinks: React.FC<{ correlationId: string; traceId?: string | null }> = ({ correlationId, traceId }) => (
  <div className="flex flex-col gap-1.5 text-xs">
    <div className="text-slate-500">
      Correlation ID <code className="font-mono text-slate-700 break-all">{correlationId}</code>
    </div>
    <div className="flex flex-wrap gap-3">
      <a
        href={jaegerOrderSearch(correlationId)}
        target="_blank"
        rel="noreferrer"
        className="inline-flex items-center gap-1 font-semibold text-emerald-700 hover:text-emerald-800"
      >
        <Activity className="w-3.5 h-3.5" />
        All traces of this order (Jaeger)
      </a>
      {traceId && (
        <a
          href={jaegerTrace(traceId)}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-1 font-semibold text-slate-600 hover:text-slate-800"
        >
          Creation trace
        </a>
      )}
    </div>
  </div>
)
