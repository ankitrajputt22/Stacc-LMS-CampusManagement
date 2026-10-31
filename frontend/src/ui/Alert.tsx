import type { ReactNode } from 'react'

export type AlertTone = 'info' | 'success' | 'warning' | 'error'

const TONES: Record<AlertTone, string> = {
  info: 'border-line bg-surface-muted text-ink',
  success: 'border-success-line bg-success-soft text-ink',
  warning: 'border-amber-300 bg-amber-50 text-ink',
  error: 'border-danger/40 bg-danger/5 text-danger',
}

type AlertProps = {
  tone?: AlertTone
  /** A short bold first line, such as "Couldn’t save." */
  title?: ReactNode
  children?: ReactNode
  /** Something to do about it, such as a Retry button. */
  action?: ReactNode
  className?: string
}

/**
 * A boxed message inside a page: a failed save, a success, a warning, or a neutral note. Errors are announced to screen
 * readers as they appear; the others are read in place.
 */
export function Alert({ tone = 'info', title, children, action, className = '' }: AlertProps) {
  return (
    <div role={tone === 'error' ? 'alert' : undefined} className={`rounded-control border px-3 py-2.5 text-sm leading-6 ${TONES[tone]} ${className}`}>
      {title && <p className="font-semibold">{title}</p>}
      {children && <div className={tone === 'error' ? '' : 'text-ink'}>{children}</div>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}
