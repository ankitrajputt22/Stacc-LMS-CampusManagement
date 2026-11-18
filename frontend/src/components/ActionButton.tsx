import type { ButtonHTMLAttributes, ReactNode } from 'react'

type ActionButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode
  focusKey?: string
}

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
