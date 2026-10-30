import React from 'react'
import {
  TextField as AriaTextField,
  Label as AriaLabel,
  Input as AriaInput,
  FieldError as AriaFieldError,
  Text as AriaText,
  type TextFieldProps as AriaTextFieldProps,
} from 'react-aria-components'
import { cn } from '../../utils/cn'

export interface ArchiTextFieldProps extends AriaTextFieldProps {
  label?: string
  description?: string
  errorMessage?: string
  placeholder?: string
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
  className,
  inputClassName,
  ...props
}) => {
  return (
    <AriaTextField
      className={(values) =>
        cn('flex flex-col gap-1.5 w-full text-left', typeof className === 'function' ? className(values) : className)
      }
      {...props}
    >
      {label && (
        <AriaLabel className="text-xs font-semibold text-slate-700 uppercase tracking-wider">
          {label}
        </AriaLabel>
      )}
      <AriaInput
        placeholder={placeholder}
        className={(values) =>
          cn(
            'w-full px-3.5 py-2 text-sm text-slate-900 bg-white border border-slate-300 rounded-lg outline-none transition-all',
            'focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200',
            'disabled:bg-slate-100 disabled:text-slate-400 disabled:cursor-not-allowed',
            values.isInvalid && 'border-rose-500 focus:border-rose-500 focus:ring-rose-200',
            inputClassName
          )
        }
      />
      {description && (
        <AriaText slot="description" className="text-xs text-slate-500">
          {description}
        </AriaText>
      )}
      <AriaFieldError className="text-xs font-medium text-rose-600">
        {errorMessage}
      </AriaFieldError>
    </AriaTextField>
  )
}
