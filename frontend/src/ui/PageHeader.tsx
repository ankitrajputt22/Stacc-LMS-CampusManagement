import type { ReactNode, Ref } from 'react'

type PageHeaderProps = {
  /** The page title, the one h1 of the page. */
  title: ReactNode
  /** A breadcrumb above the title, usually `<Breadcrumb … />`. */
  breadcrumb?: ReactNode
  /** A short line above the title, such as a course code. */
  eyebrow?: ReactNode
  /** One or two sentences under the title. */
  description?: ReactNode
  /** The page's main actions, shown beside the title on wide screens and under it on phones. */
  actions?: ReactNode
  /** For pages that move focus to their heading after a change. */
  titleRef?: Ref<HTMLHeadingElement>
}

/**
 * The top of every page: breadcrumb, eyebrow, the large navy title, a description, and the page's actions. Use it
 * instead of writing a heading by hand, so every page title looks and reads the same.
 */
export function PageHeader({ title, breadcrumb, eyebrow, description, actions, titleRef }: PageHeaderProps) {
  return (
    <header className="mb-6">
      {breadcrumb && <div className="mb-3">{breadcrumb}</div>}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          {eyebrow && <p className="text-sm font-semibold tracking-[0.04em] break-words text-ink-muted">{eyebrow}</p>}
          <h1
            ref={titleRef}
            tabIndex={titleRef ? -1 : undefined}
            className="mt-0.5 text-3xl font-bold tracking-tight break-words text-portal-navy outline-none"
          >
            {title}
          </h1>
          {description && <div className="mt-2 max-w-3xl text-sm leading-6 text-ink-muted sm:text-base">{description}</div>}
        </div>
        {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
      </div>
    </header>
  )
}
