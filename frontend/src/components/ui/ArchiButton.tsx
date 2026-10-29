import React from 'react'
import {
  Button as AriaButton,
  type ButtonProps as AriaButtonProps,
} from 'react-aria-components'
import { Loader2 } from 'lucide-react'
import { cn } from '../../utils/cn'

export interface ArchiButtonProps extends AriaButtonProps {
  variant?: 'primary' | 'secondary' | 'outline' | 'destructive' | 'ghost'
  size?: 'sm' | 'md' | 'lg'
  isLoading?: boolean
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
  className,
  children,
  isDisabled,
  ...props
}) => {
  const baseStyles =
    'inline-flex items-center justify-center font-medium transition-colors cursor-pointer rounded-lg outline-none select-none focus-visible:ring-2 focus-visible:ring-offset-2'

  const sizeStyles = {
    sm: 'text-xs px-3 py-1.5 gap-1.5',
    md: 'text-sm px-4 py-2 gap-2',
    lg: 'text-base px-6 py-2.5 gap-2.5',
  }

  const variantStyles = {
    primary:
      'bg-emerald-600 text-white hover:bg-emerald-700 active:bg-emerald-800 focus-visible:ring-emerald-500 disabled:bg-emerald-300 disabled:cursor-not-allowed',
    secondary:
      'bg-slate-800 text-white hover:bg-slate-900 active:bg-slate-950 focus-visible:ring-slate-500 disabled:bg-slate-400 disabled:cursor-not-allowed',
    outline:
      'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 active:bg-slate-100 focus-visible:ring-slate-400 disabled:opacity-50 disabled:cursor-not-allowed',
    destructive:
      'bg-rose-600 text-white hover:bg-rose-700 active:bg-rose-800 focus-visible:ring-rose-500 disabled:bg-rose-300 disabled:cursor-not-allowed',
    ghost:
      'text-slate-600 hover:bg-slate-100 active:bg-slate-200 focus-visible:ring-slate-400 disabled:opacity-50 disabled:cursor-not-allowed',
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
          <Loader2 className="w-4 h-4 animate-spin" />
          <span>Loading...</span>
        </>
      ) : (
        children
      )}
    </AriaButton>
  )
}
