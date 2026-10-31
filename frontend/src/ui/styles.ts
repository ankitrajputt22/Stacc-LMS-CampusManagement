// The shared class lists of Stacc's interface. Components in this folder use them, and pages that are not yet moved
// onto the components can use them directly, so every page draws from one source.

/** The visible focus ring used everywhere. */
export const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary'

/** An inline text link, such as "View all" or a link inside a sentence. */
export const textLinkClasses = `rounded-sm font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline ${focusRing}`

/** A form control: text input, textarea, or select. Add `controlBorder(hasError)` and a height or padding. */
export const controlClasses =
  'mt-1.5 block w-full rounded-control border bg-surface px-3 text-base text-ink placeholder:text-ink-muted focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-stacc-primary disabled:cursor-not-allowed disabled:bg-surface-muted disabled:text-ink-muted sm:text-sm'

export function controlBorder(hasError: boolean): string {
  return hasError ? 'border-danger' : 'border-line-strong hover:border-slate-400'
}

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost'
export type ButtonSize = 'md' | 'sm'

const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  primary: 'border-transparent bg-stacc-primary text-white hover:bg-stacc-primary-hover',
  secondary: 'border-line-strong bg-surface text-ink hover:border-slate-400 hover:bg-surface-muted',
  danger: 'border-transparent bg-danger text-white hover:bg-red-800',
  ghost: 'border-transparent bg-transparent text-stacc-primary hover:bg-surface-muted',
}

const BUTTON_SIZES: Record<ButtonSize, string> = {
  // 44 px tall on phones, so it is easy to tap.
  md: 'h-11 px-4 text-sm',
  sm: 'min-h-11 px-3 text-sm sm:min-h-8',
}

/** The classes of a button, or of a link that looks like one. */
export function buttonClasses(variant: ButtonVariant = 'primary', size: ButtonSize = 'md', fullWidth = false): string {
  return [
    'inline-flex items-center justify-center gap-2 rounded-control border font-medium transition-colors',
    'disabled:cursor-not-allowed disabled:opacity-60 aria-disabled:cursor-not-allowed aria-disabled:opacity-60',
    focusRing,
    BUTTON_VARIANTS[variant],
    BUTTON_SIZES[size],
    // A normal button is full width on phones and fits its text from the sm breakpoint; a full-width one always fills.
    fullWidth ? 'w-full' : size === 'md' ? 'w-full sm:h-9 sm:w-auto' : '',
  ].join(' ')
}
