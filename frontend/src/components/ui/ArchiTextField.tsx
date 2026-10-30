import React, { useState } from 'react'
import {
  TextField as AriaTextField,
  Label as AriaLabel,
  Input as AriaInput,
  FieldError as AriaFieldError,
  Text as AriaText,
  type TextFieldProps as AriaTextFieldProps,
} from 'react-aria-components'
import { Eye, EyeOff } from 'lucide-react'
import { cn } from '../../utils/cn'

export interface ArchiTextFieldProps extends AriaTextFieldProps {
  label?: string
  description?: string
  errorMessage?: string
  placeholder?: string
  leftIcon?: React.ReactNode
  rightAdornment?: React.ReactNode
  inputClassName?: string
}

/**
 * Global custom wrapper around React Aria's TextField, Label, Input and FieldError components.
 */
export const ArchiTextField: React.FC<ArchiTextFieldProps> = ({
  label,
  description,
  errorMessage,
  placeholder,
  leftIcon,
  rightAdornment,
  className,
  inputClassName,
  type,
  ...props
}) => {
  const [showPassword, setShowPassword] = useState(false)
  const isPasswordField = type === 'password'
  const resolvedType = isPasswordField ? (showPassword ? 'text' : 'password') : type

  return (
    <AriaTextField
      className={(values) =>
        cn('flex flex-col gap-1.5 w-full text-left', typeof className === 'function' ? className(values) : className)
      }
      type={resolvedType}
      {...props}
    >
      {label && (
        <AriaLabel className="text-xs font-bold text-slate-700 uppercase tracking-wider flex items-center justify-between">
          <span>{label}</span>
          {props.isRequired && <span className="text-emerald-600 font-semibold text-[10px] lowercase">required</span>}
        </AriaLabel>
      )}

      <div className="relative flex items-center">
        {leftIcon && (
          <div className="absolute left-3.5 flex items-center pointer-events-none text-slate-400">
            {leftIcon}
          </div>
        )}

        <AriaInput
          placeholder={placeholder}
          className={(values) =>
            cn(
              'w-full py-2.5 text-sm text-slate-900 bg-white/90 border border-slate-200 rounded-xl outline-none transition-all duration-200 shadow-xs',
              'placeholder:text-slate-400',
              leftIcon ? 'pl-10.5 pr-4' : 'px-4',
              (isPasswordField || rightAdornment) && 'pr-11',
              'focus:border-emerald-500 focus:ring-4 focus:ring-emerald-500/10 focus:bg-white',
              'disabled:bg-slate-50 disabled:text-slate-400 disabled:cursor-not-allowed',
              values.isInvalid && 'border-rose-400 focus:border-rose-500 focus:ring-rose-500/10',
              inputClassName
            )
          }
        />

        {isPasswordField ? (
          <button
            type="button"
            onClick={() => setShowPassword(!showPassword)}
            tabIndex={-1}
            aria-label={showPassword ? 'Hide password' : 'Show password'}
            className="absolute right-3 p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
          </button>
        ) : rightAdornment ? (
          <div className="absolute right-3 flex items-center">{rightAdornment}</div>
        ) : null}
      </div>

      {description && (
        <AriaText slot="description" className="text-xs text-slate-500 leading-normal">
          {description}
        </AriaText>
      )}
      <AriaFieldError className="text-xs font-semibold text-rose-600 animate-in fade-in">
        {errorMessage}
      </AriaFieldError>
    </AriaTextField>
  )
}
