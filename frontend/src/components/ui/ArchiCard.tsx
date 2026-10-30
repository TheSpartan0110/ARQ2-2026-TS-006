import React from 'react'
import { cn } from '../../utils/cn'

export interface ArchiCardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'glass' | 'dark' | 'interactive'
  hasHighlight?: boolean
  children: React.ReactNode
}

export const ArchiCard: React.FC<ArchiCardProps> = ({
  variant = 'glass',
  hasHighlight = true,
  className,
  children,
  ...props
}) => {
  const variantStyles = {
    glass:
      'bg-white/85 backdrop-blur-xl border border-slate-200/80 shadow-sm shadow-slate-200/50 hover:shadow-md hover:shadow-slate-200/60 transition-all duration-300',
    default:
      'bg-white border border-slate-200 shadow-sm',
    dark:
      'bg-slate-900/95 backdrop-blur-xl border border-slate-800 text-white shadow-xl shadow-slate-950/30',
    interactive:
      'bg-white/85 backdrop-blur-xl border border-slate-200/80 shadow-sm hover:shadow-lg hover:border-emerald-300 hover:-translate-y-1 transition-all duration-200 cursor-pointer',
  }

  return (
    <div
      className={cn(
        'relative rounded-2xl overflow-hidden p-6',
        hasHighlight && variant !== 'dark' && 'before:absolute before:inset-x-0 before:top-0 before:h-[1px] before:bg-gradient-to-r before:from-transparent before:via-emerald-500/30 before:to-transparent',
        hasHighlight && variant === 'dark' && 'before:absolute before:inset-x-0 before:top-0 before:h-[1px] before:bg-gradient-to-r before:from-transparent before:via-emerald-400/40 before:to-transparent',
        variantStyles[variant],
        className
      )}
      {...props}
    >
      {children}
    </div>
  )
}

export const ArchiCardHeader: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({
  className,
  children,
  ...props
}) => (
  <div className={cn('flex flex-col gap-1.5 pb-4 border-b border-slate-100/80', className)} {...props}>
    {children}
  </div>
)

export const ArchiCardTitle: React.FC<React.HTMLAttributes<HTMLHeadingElement>> = ({
  className,
  children,
  ...props
}) => (
  <h2 className={cn('text-xl font-bold tracking-tight text-slate-900', className)} {...props}>
    {children}
  </h2>
)

export const ArchiCardDescription: React.FC<React.HTMLAttributes<HTMLParagraphElement>> = ({
  className,
  children,
  ...props
}) => (
  <p className={cn('text-sm text-slate-500 leading-relaxed', className)} {...props}>
    {children}
  </p>
)

export const ArchiCardContent: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({
  className,
  children,
  ...props
}) => (
  <div className={cn('pt-4', className)} {...props}>
    {children}
  </div>
)
