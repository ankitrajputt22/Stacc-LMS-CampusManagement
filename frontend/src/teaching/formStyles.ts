// The look of the editor's form fields, shared by its forms.
export const fieldClasses =
  'mt-1.5 block w-full rounded-control border bg-surface px-3 text-base text-ink focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-stacc-primary sm:text-sm'

export function border(hasError: boolean): string {
  return hasError ? 'border-danger' : 'border-line-strong hover:border-slate-400'
}
