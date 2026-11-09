import type { ButtonHTMLAttributes, ReactNode } from 'react'

type ActionButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode
  /** A key the editor uses to move focus back here after a change. */
  focusKey?: string
}

/**
 * A small text button for the actions on a module or topic. Several fit on one line and wrap on
 * narrow screens, and each stays a comfortable size to tap.
 */
export function ActionButton({ children, className = '', focusKey, type = 'button', ...props }: ActionButtonProps) {
  return (
    <button
      type={type}
      data-focus-key={focusKey}
      className={`inline-flex min-h-11 items-center rounded-control border border-line-strong bg-surface px-3 text-sm font-medium text-ink hover:border-slate-400 hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary disabled:cursor-not-allowed disabled:border-line disabled:text-ink-muted/60 disabled:hover:bg-surface sm:min-h-8 ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}
