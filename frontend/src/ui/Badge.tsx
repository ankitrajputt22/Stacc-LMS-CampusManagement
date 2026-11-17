import type { ReactNode } from 'react'

export type BadgeTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'brand'

const TONES: Record<BadgeTone, string> = {
  neutral: 'bg-slate-100 text-ink',
  info: 'bg-sky-100 text-portal-navy',
  success: 'bg-success-soft text-success',
  warning: 'bg-amber-100 text-ink',
  danger: 'bg-red-50 text-danger',
  brand: 'bg-portal-navy text-white',
}

export function Badge({ tone = 'neutral', children }: { tone?: BadgeTone; children: ReactNode }) {
  return (
    <span className={`inline-flex shrink-0 items-center rounded-control px-2 py-0.5 text-sm leading-5 font-semibold ${TONES[tone]}`}>
      {children}
    </span>
  )
}
