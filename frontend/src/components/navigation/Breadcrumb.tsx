import { Link } from 'react-router'

const linkClasses =
  'inline-flex min-h-11 max-w-full min-w-0 items-center rounded-control font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'

type Crumb = { to: string; label: string }

type BreadcrumbProps = {
  /** The page above this one, or the pages above it, outermost first. */
  parent: Crumb | readonly Crumb[]
  /** The current page, once its name is known. */
  current?: string
}

/** "Parent / current page": the way back to a list, and where the user is. Long names are shortened. */
export function Breadcrumb({ parent, current }: BreadcrumbProps) {
  const parents = 'to' in parent ? [parent] : parent

  return (
    <nav aria-label="Breadcrumb" className="text-sm">
      <ol className="flex min-w-0 items-center gap-2">
        {parents.map((crumb, index) => (
          <li key={crumb.to} className={index === 0 ? 'shrink-0' : 'flex min-w-0 items-center gap-2'}>
            {index > 0 && (
              <span aria-hidden="true" className="text-ink-muted">
                /
              </span>
            )}
            <Link to={crumb.to} className={linkClasses}>
              <span className="truncate">{crumb.label}</span>
            </Link>
          </li>
        ))}
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
