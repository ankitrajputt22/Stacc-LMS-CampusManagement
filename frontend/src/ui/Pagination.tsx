import { Button } from './Button'

type PaginationProps = {
  page: number
  pageSize: number
  total: number
  onPage: (page: number) => void
  noun: string
}

export function Pagination({ page, pageSize, total, onPage, noun }: PaginationProps) {
  const pages = Math.max(1, Math.ceil(total / pageSize))
  if (pages <= 1) {
    return null
  }
  const first = (page - 1) * pageSize + 1
  const last = Math.min(page * pageSize, total)
  return (
    <nav aria-label={`Pages of ${noun}`} className="flex flex-col gap-2 border-t border-line px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
      <p role="status" className="text-sm text-ink-muted">
        Showing {first}–{last} of {total} {noun}
      </p>
      <div className="flex gap-2">
        <Button variant="secondary" size="sm" disabled={page <= 1} onClick={() => onPage(page - 1)}>
          Previous<span className="sr-only"> page</span>
        </Button>
        <Button variant="secondary" size="sm" disabled={page >= pages} onClick={() => onPage(page + 1)}>
          Next<span className="sr-only"> page</span>
        </Button>
      </div>
    </nav>
  )
}
