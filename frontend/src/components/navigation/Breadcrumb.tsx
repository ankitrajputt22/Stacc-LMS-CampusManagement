import { Link } from 'react-router'

const linkClasses =
  'inline-flex min-h-11 items-center rounded-control font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'

type BreadcrumbProps = {
  parent: { to: string; label: string }
  /** The current page, once its name is known. */
  current?: string
}

/** "Parent / current page": the way back to a list, and where the user is. */
export function Breadcrumb({ parent, current }: BreadcrumbProps) {
  return (
    <nav aria-label="Breadcrumb" className="text-sm">
      <ol className="flex min-w-0 items-center gap-2">
        <li className="shrink-0">
          <Link to={parent.to} className={linkClasses}>
            {parent.label}
          </Link>
        </li>
        {current && (
          <li className="flex min-w-0 items-center gap-2 text-ink-muted">
            <span aria-hidden="true">/</span>
            <span aria-current="page" className="truncate">
              {current}
            </span>
          </li>
        )}
      </ol>
    </nav>
  )
}
