import type { ReactNode } from 'react'

export type AlertTone = 'info' | 'success' | 'warning' | 'error'

const TONES: Record<AlertTone, string> = {
  info: 'border-sky-300 bg-sky-50 text-ink',
  success: 'border-success-line bg-success-soft text-ink',
  warning: 'border-amber-300 bg-amber-50 text-ink',
  error: 'border-danger/40 bg-red-50 text-danger',
}

const ICON_COLOUR: Record<AlertTone, string> = {
  info: 'text-stacc-primary',
  success: 'text-success',
  warning: 'text-warning',
  error: 'text-danger',
}

const ICONS: Record<AlertTone, ReactNode> = {
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5M12 7.5h0" />
    </>
  ),
  success: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="m8 12.5 2.8 2.8L16 9.5" />
    </>
  ),
  warning: <path d="M12 3.5 2.5 20h19zM12 10v4.5M12 17.5h0" />,
  error: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="m9 9 6 6M15 9l-6 6" />
    </>
  ),
}

type AlertProps = {
  tone?: AlertTone
  title?: ReactNode
  children?: ReactNode
  action?: ReactNode
  className?: string
}

export function Alert({ tone = 'info', title, children, action, className = '' }: AlertProps) {
  return (
    <div role={tone === 'error' ? 'alert' : undefined} className={`flex gap-3 rounded-control border px-3 py-2.5 text-sm leading-6 ${TONES[tone]} ${className}`}>
      <svg
        aria-hidden="true"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className={`mt-0.5 size-5 shrink-0 ${ICON_COLOUR[tone]}`}
      >
        {ICONS[tone]}
      </svg>
      <div className="min-w-0 flex-1 border-l border-current/20 pl-3">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className={tone === 'error' ? '' : 'text-ink'}>{children}</div>}
        {action && <div className="mt-2">{action}</div>}
      </div>
    </div>
  )
}
