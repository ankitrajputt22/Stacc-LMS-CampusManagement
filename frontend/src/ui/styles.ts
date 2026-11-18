export const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary'

export const textLinkClasses = `rounded-sm font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline ${focusRing}`

export const controlClasses =
  'mt-1.5 block w-full rounded-control border bg-surface px-3 text-base text-ink placeholder:text-ink-muted focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-stacc-primary disabled:cursor-not-allowed disabled:bg-surface-muted disabled:text-ink-muted [&[readonly]]:bg-surface-muted sm:text-[0.95rem]'

export function controlBorder(hasError: boolean): string {
  return hasError ? 'border-danger' : 'border-line-strong hover:border-slate-400'
}

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost'
export type ButtonSize = 'md' | 'sm'

const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  primary: 'border-transparent bg-portal-cta text-white hover:bg-portal-cta-hover',
  secondary: 'border-line-strong bg-surface text-ink hover:border-slate-400 hover:bg-surface-muted',
  danger: 'border-transparent bg-danger text-white hover:bg-red-800',
  ghost: 'border-transparent bg-transparent text-stacc-primary hover:bg-surface-muted',
}

const BUTTON_SIZES: Record<ButtonSize, string> = {
  md: 'h-11 px-4 text-sm',
  sm: 'min-h-11 px-3 text-sm sm:min-h-8',
}

export function buttonClasses(variant: ButtonVariant = 'primary', size: ButtonSize = 'md', fullWidth = false): string {
  return [
    'inline-flex items-center justify-center gap-2 rounded-control border font-semibold transition-colors',
    'disabled:cursor-not-allowed disabled:opacity-60 aria-disabled:cursor-not-allowed aria-disabled:opacity-60',
    focusRing,
    BUTTON_VARIANTS[variant],
    BUTTON_SIZES[size],
    fullWidth ? 'w-full' : size === 'md' ? 'w-full sm:h-9 sm:w-auto' : '',
  ].join(' ')
}
