import type { ReactNode } from 'react'

type EmptyStateProps = {
  title: string
  description?: string
  action?: ReactNode
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center px-4 py-10 text-center">
      <p className="text-base font-semibold text-ink">{title}</p>
      {description && (
        <p className="mt-1 max-w-md text-sm leading-6 text-ink-muted">
          {description}
        </p>
      )}
      {action && <div className="mt-5 w-full sm:w-auto">{action}</div>}
    </div>
  )
}
