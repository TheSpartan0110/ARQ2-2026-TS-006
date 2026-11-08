import React from 'react'
import { cn } from '../../utils/cn'
import { TONE_CLASSES, type Tone } from '../../utils/format'

export const StatusPill: React.FC<{ label: string; tone: Tone; className?: string }> = ({ label, tone, className }) => (
  <span
    className={cn(
      'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border whitespace-nowrap',
      TONE_CLASSES[tone],
      className
    )}
  >
    <span className="w-1.5 h-1.5 rounded-full bg-current opacity-70" />
    {label}
  </span>
)
