import type { ButtonHTMLAttributes, ReactNode } from 'react'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode
  variant?: 'primary' | 'secondary'
}

const variantClasses = {
  primary:
    'border-transparent bg-stacc-primary text-white hover:bg-stacc-primary-hover',
  secondary:
    'border-line-strong bg-surface text-ink hover:border-slate-400 hover:bg-surface-muted',
}

export function Button({
  children,
  className = '',
  type = 'button',
  variant = 'primary',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`inline-flex h-11 w-full items-center justify-center rounded-control border px-4 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:h-9 sm:w-auto ${variantClasses[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}
