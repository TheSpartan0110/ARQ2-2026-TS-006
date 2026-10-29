import React from 'react'
import { cn } from '../../utils/cn'

export interface ArchiCardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode
}

export const ArchiCard: React.FC<ArchiCardProps> = ({ className, children, ...props }) => {
  return (
    <div
      className={cn(
        'bg-white border border-slate-200/80 rounded-2xl shadow-sm overflow-hidden p-6',
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
  <div className={cn('flex flex-col gap-1.5 pb-4 border-b border-slate-100', className)} {...props}>
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
  <p className={cn('text-sm text-slate-500', className)} {...props}>
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
