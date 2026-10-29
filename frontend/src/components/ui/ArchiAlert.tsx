import React from 'react'
import { AlertCircle, CheckCircle2, Info } from 'lucide-react'
import { cn } from '../../utils/cn'

export interface ArchiAlertProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'error' | 'success' | 'info'
  title?: string
  children: React.ReactNode
}

export const ArchiAlert: React.FC<ArchiAlertProps> = ({
  variant = 'info',
  title,
  className,
  children,
  ...props
}) => {
  const icons = {
    error: <AlertCircle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />,
    success: <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />,
    info: <Info className="w-5 h-5 text-blue-600 shrink-0 mt-0.5" />,
  }

  const styles = {
    error: 'bg-rose-50 border-rose-200 text-rose-900',
    success: 'bg-emerald-50 border-emerald-200 text-emerald-900',
    info: 'bg-blue-50 border-blue-200 text-blue-900',
  }

  return (
    <div
      className={cn(
        'flex gap-3 p-4 rounded-xl border text-sm',
        styles[variant],
        className
      )}
      {...props}
    >
      {icons[variant]}
      <div className="flex flex-col gap-0.5">
        {title && <span className="font-semibold text-slate-900">{title}</span>}
        <div>{children}</div>
      </div>
    </div>
  )
}
