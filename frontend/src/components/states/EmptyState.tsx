import type { ReactNode } from 'react'

type EmptyStateProps = {
  title: string
  description?: string
  action?: ReactNode
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-start px-5 py-8 text-left sm:px-6">
      <p className="text-lg font-bold text-ink">{title}</p>
      {description && (
        <p className="mt-1 max-w-xl text-[0.95rem] leading-6 text-ink">
          {description}
        </p>
      )}
      {action && <div className="mt-4 w-full sm:w-auto">{action}</div>}
    </div>
  )
}
