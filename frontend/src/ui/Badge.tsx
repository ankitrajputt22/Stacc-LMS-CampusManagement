import type { ReactNode } from 'react'

export type BadgeTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'brand'

const TONES: Record<BadgeTone, string> = {
  neutral: 'border-line-strong bg-surface text-ink',
  info: 'border-blue-200 bg-blue-50 text-stacc-primary-hover',
  success: 'border-success-line bg-success-soft text-success',
  warning: 'border-amber-200 bg-amber-50 text-warning',
  danger: 'border-red-200 bg-red-50 text-danger',
  brand: 'border-transparent bg-portal-navy text-white',
}

/**
 * A short status word, such as Draft, Published, Late, or Closed. The word itself carries the meaning; the colour only
 * helps, so a badge never relies on colour alone.
 */
export function Badge({ tone = 'neutral', children }: { tone?: BadgeTone; children: ReactNode }) {
  return (
    <span className={`inline-flex shrink-0 items-center rounded-control border px-1.5 py-0.5 text-xs leading-4 font-medium ${TONES[tone]}`}>
      {children}
    </span>
  )
}
