import type { ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { LinkTabs } from '../ui'

export function SupportHeader({ title, description, action }: { title: string; description?: ReactNode; action?: ReactNode }) {
  const { account } = useAuth()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  return (
    <>
      <header className="mb-5 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-3xl font-bold tracking-tight break-words text-portal-navy">{title}</h1>
          {description && <p className="mt-2 text-[0.95rem] leading-6 text-ink-muted sm:text-base">{description}</p>}
        </div>
        {action && <div className="shrink-0">{action}</div>}
      </header>
      {isAdmin && (
        <LinkTabs
          label="Support"
          tabs={[
            { to: '/support', label: 'My tickets', end: true },
            { to: '/support/admin', label: 'Admin ticket queue', end: true },
            { to: '/support/admin/categories', label: 'Categories' },
            { to: '/support/admin/teams', label: 'Teams' },
            { to: '/support/admin/articles', label: 'Help articles' },
          ]}
        />
      )}
    </>
  )
}
