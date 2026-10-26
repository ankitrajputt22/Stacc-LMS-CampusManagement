import type { ReactNode } from 'react'

const tones = {
  note: 'border-line bg-surface-muted text-ink',
  warning: 'border-warning/40 bg-warning/5 text-ink',
  error: 'border-danger/40 bg-danger/5 text-danger',
  success: 'border-success-line bg-success-soft text-success',
}

/** A short boxed message. Errors are announced; other tones are plain text in the page. */
export function AiMessage({ tone, children, className = '' }: { tone: keyof typeof tones; children: ReactNode; className?: string }) {
  return (
    <div
      role={tone === 'error' ? 'alert' : undefined}
      className={`rounded-control border px-3 py-2.5 text-sm leading-6 ${tones[tone]} ${className}`}
    >
      {children}
    </div>
  )
}
