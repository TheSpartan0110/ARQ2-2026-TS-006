import React from 'react'
import {
  Button as AriaButton,
  type ButtonProps as AriaButtonProps,
} from 'react-aria-components'
import { Loader2 } from 'lucide-react'
import { cn } from '../../utils/cn'

export interface ArchiButtonProps extends AriaButtonProps {
  variant?: 'primary' | 'secondary' | 'outline' | 'destructive' | 'ghost' | 'glass'
  size?: 'sm' | 'md' | 'lg'
  isLoading?: boolean
  leftIcon?: React.ReactNode
  rightIcon?: React.ReactNode
  children?: React.ReactNode
}

/**
 * Global custom wrapper around React Aria's Button component.
 * Follows the Archi* design system prefix for EatsNow platform.
 */
export const ArchiButton: React.FC<ArchiButtonProps> = ({
  variant = 'primary',
  size = 'md',
  isLoading = false,
  leftIcon,
  rightIcon,
  className,
  children,
  isDisabled,
  ...props
}) => {
  const baseStyles =
    'relative inline-flex items-center justify-center font-semibold transition-all duration-200 cursor-pointer rounded-xl outline-none select-none active:scale-[0.98] focus-visible:ring-2 focus-visible:ring-offset-2'

  const sizeStyles = {
    sm: 'text-xs px-3.5 py-1.5 gap-1.5 shadow-xs',
    md: 'text-sm px-4.5 py-2.5 gap-2 shadow-sm',
    lg: 'text-base px-6 py-3 gap-2.5 shadow-md',
  }

  const variantStyles = {
    primary:
      'bg-gradient-to-r from-emerald-600 via-emerald-500 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white shadow-emerald-600/25 hover:shadow-emerald-600/35 hover:-translate-y-0.5 focus-visible:ring-emerald-500 disabled:from-emerald-300 disabled:to-emerald-400 disabled:cursor-not-allowed disabled:hover:translate-y-0 disabled:shadow-none',
    secondary:
      'bg-slate-900 text-white hover:bg-slate-800 active:bg-slate-950 hover:-translate-y-0.5 shadow-slate-900/20 focus-visible:ring-slate-700 disabled:bg-slate-400 disabled:cursor-not-allowed disabled:hover:translate-y-0',
    outline:
      'border border-slate-200 bg-white/80 hover:bg-slate-50/80 active:bg-slate-100 text-slate-700 shadow-slate-100 hover:border-slate-300 hover:-translate-y-0.5 focus-visible:ring-slate-400 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0',
    destructive:
      'bg-gradient-to-r from-rose-600 to-red-600 hover:from-rose-500 hover:to-red-500 text-white shadow-rose-600/20 hover:shadow-rose-600/30 hover:-translate-y-0.5 focus-visible:ring-rose-500 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0',
    ghost:
      'text-slate-600 hover:bg-slate-100/80 active:bg-slate-200/80 hover:text-slate-900 shadow-none focus-visible:ring-slate-400 disabled:opacity-50 disabled:cursor-not-allowed',
    glass:
      'backdrop-blur-md bg-white/70 border border-white/80 text-slate-800 hover:bg-white/90 shadow-sm hover:shadow-md hover:-translate-y-0.5 focus-visible:ring-emerald-400 disabled:opacity-50 disabled:cursor-not-allowed',
  }

  return (
    <AriaButton
      isDisabled={isDisabled || isLoading}
      className={(values) =>
        cn(
          baseStyles,
          sizeStyles[size],
          variantStyles[variant],
          typeof className === 'function' ? className(values) : className
        )
      }
      {...props}
    >
      {isLoading ? (
        <>
          <Loader2 className="w-4 h-4 animate-spin text-current" />
          <span>Loading...</span>
        </>
      ) : (
        <>
          {leftIcon && <span className="inline-flex shrink-0">{leftIcon}</span>}
          {children}
          {rightIcon && <span className="inline-flex shrink-0">{rightIcon}</span>}
        </>
      )}
    </AriaButton>
  )
}
