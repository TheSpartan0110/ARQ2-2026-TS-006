import React from 'react'
import { cn } from '../../utils/cn'

export interface ArchiBadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'user' | 'admin' | 'restaurant' | 'driver' | 'neutral' | 'success' | 'warning'
  showDot?: boolean
  children: React.ReactNode
}

export const ArchiBadge: React.FC<ArchiBadgeProps> = ({
  variant = 'neutral',
  showDot = true,
  className,
  children,
  ...props
}) => {
  const variantStyles = {
    user: 'bg-blue-50/90 text-blue-700 border-blue-200/80 shadow-xs',
    admin: 'bg-purple-50/90 text-purple-700 border-purple-200/80 shadow-xs',
    restaurant: 'bg-amber-50/90 text-amber-700 border-amber-200/80 shadow-xs',
    driver: 'bg-emerald-50/90 text-emerald-700 border-emerald-200/80 shadow-xs',
    neutral: 'bg-slate-100/90 text-slate-700 border-slate-200/80 shadow-xs',
    success: 'bg-emerald-50/90 text-emerald-700 border-emerald-200/80 shadow-xs',
    warning: 'bg-orange-50/90 text-orange-700 border-orange-200/80 shadow-xs',
  }

  const dotStyles = {
    user: 'bg-blue-500',
    admin: 'bg-purple-500',
    restaurant: 'bg-amber-500',
    driver: 'bg-emerald-500',
    neutral: 'bg-slate-400',
    success: 'bg-emerald-500',
    warning: 'bg-orange-500',
  }

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border transition-colors',
        variantStyles[variant],
        className
      )}
      {...props}
    >
      {showDot && (
        <span className={cn('w-1.5 h-1.5 rounded-full shrink-0', dotStyles[variant])} />
      )}
      {children}
    </span>
  )
}
