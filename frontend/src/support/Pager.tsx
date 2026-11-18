import { ActionButton } from '../components/ActionButton'

const pagerClasses = 'aria-disabled:cursor-not-allowed aria-disabled:text-ink-muted/60 aria-disabled:hover:bg-surface'

type PagerProps = {
  page: number
  totalPages: number
  label: string
  onPage: (page: number, focusKey: string) => void
}

export function Pager({ page, totalPages, label, onPage }: PagerProps) {
  if (totalPages <= 1) {
    return null
  }
  return (
    <nav aria-label={label} className="mt-4 flex flex-wrap items-center gap-3">
      <ActionButton
        aria-disabled={page === 0 || undefined}
        className={pagerClasses}
        focusKey="page-previous"
        onClick={() => page > 0 && onPage(page - 1, 'page-previous')}
      >
        Previous<span className="sr-only"> page</span>
      </ActionButton>
      <p className="text-sm text-ink-muted" aria-live="polite">
        Page {page + 1} of {totalPages}
      </p>
      <ActionButton
        aria-disabled={page + 1 >= totalPages || undefined}
        className={pagerClasses}
        focusKey="page-next"
        onClick={() => page + 1 < totalPages && onPage(page + 1, 'page-next')}
      >
        Next<span className="sr-only"> page</span>
      </ActionButton>
    </nav>
  )
}
