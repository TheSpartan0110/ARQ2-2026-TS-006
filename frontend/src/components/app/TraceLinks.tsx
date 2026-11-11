import React, { useEffect, useState } from 'react'
import { Activity } from 'lucide-react'
import { findCreationTraceId, jaegerOrderSearch, jaegerTrace } from '../../utils/observability'

/**
 * Shows the workflow correlation id of an order and opens every trace of its lifecycle in Jaeger (Feature 5).
 */
export const TraceLinks: React.FC<{ correlationId: string; createdAt: string }> = ({ correlationId, createdAt }) => {
  const [creationTraceId, setCreationTraceId] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    void findCreationTraceId(correlationId, createdAt).then((id) => {
      if (active) setCreationTraceId(id)
    })
    return () => {
      active = false
    }
  }, [correlationId, createdAt])

  return (
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
        {creationTraceId && (
          <a
            href={jaegerTrace(creationTraceId)}
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
}
