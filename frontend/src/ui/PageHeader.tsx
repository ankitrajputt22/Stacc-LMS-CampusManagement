import type { ReactNode, Ref } from 'react'

type PageHeaderProps = {
  title: ReactNode
  breadcrumb?: ReactNode
  eyebrow?: ReactNode
  description?: ReactNode
  actions?: ReactNode
  titleRef?: Ref<HTMLHeadingElement>
}

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
