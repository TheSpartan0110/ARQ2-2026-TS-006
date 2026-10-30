import React from 'react'
import { AlertCircle, CheckCircle2, Info, AlertTriangle } from 'lucide-react'
import { cn } from '../../utils/cn'

export interface ArchiAlertProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'info' | 'success' | 'warning' | 'error'
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
  const styles = {
    info: {
      container: 'bg-sky-50/90 border-sky-200/80 text-sky-900',
      icon: <Info className="w-5 h-5 text-sky-600 shrink-0" />,
    },
    success: {
      container: 'bg-emerald-50/90 border-emerald-200/80 text-emerald-950',
      icon: <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0" />,
    },
    warning: {
      container: 'bg-amber-50/90 border-amber-200/80 text-amber-950',
      icon: <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0" />,
    },
    error: {
      container: 'bg-rose-50/90 border-rose-200/80 text-rose-950',
      icon: <AlertCircle className="w-5 h-5 text-rose-600 shrink-0" />,
    },
  }

  const current = styles[variant]

  return (
    <div
      role="alert"
      className={cn(
        'flex items-start gap-3 p-4 rounded-xl border backdrop-blur-md shadow-xs animate-in fade-in slide-in-from-top-1 duration-200',
        current.container,
        className
      )}
      {...props}
    >
      <div className="pt-0.5">{current.icon}</div>
      <div className="flex flex-col gap-0.5 text-sm leading-relaxed">
        {title && <span className="font-bold text-slate-900">{title}</span>}
        <div className="text-slate-700 text-xs sm:text-sm">{children}</div>
      </div>
    </div>
  )
}
