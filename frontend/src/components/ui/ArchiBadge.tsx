import React from 'react'
import { cn } from '../../utils/cn'

export interface ArchiBadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'user' | 'admin' | 'restaurant' | 'driver' | 'neutral' | 'success'
  children: React.ReactNode
}

export const ArchiBadge: React.FC<ArchiBadgeProps> = ({
  variant = 'neutral',
  className,
  children,
  ...props
}) => {
  const variantStyles = {
    user: 'bg-blue-50 text-blue-700 border-blue-200',
    admin: 'bg-purple-50 text-purple-700 border-purple-200',
    restaurant: 'bg-amber-50 text-amber-700 border-amber-200',
    driver: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    neutral: 'bg-slate-100 text-slate-700 border-slate-200',
    success: 'bg-green-50 text-green-700 border-green-200',
  }

  return (
    <span
      className={cn(
        'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border',
        variantStyles[variant],
        className
      )}
      {...props}
    >
      {children}
    </span>
  )
}
