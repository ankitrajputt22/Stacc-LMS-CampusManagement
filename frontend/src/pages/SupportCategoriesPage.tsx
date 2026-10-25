import { useRef, useState } from 'react'
import { createHelpdeskCategory, getAdminHelpdeskCategories, updateHelpdeskCategory } from '../api/helpdeskApi'
import { useApiRequest } from '../api/useApiRequest'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { usePendingFocus } from '../components/usePendingFocus'
import { CategoryForm, type CategoryValues } from '../support/CategoryForm'
import { SIGN_IN_AGAIN } from '../support/helpdeskText'

type Open = { type: 'create' } | { type: 'edit'; categoryId: number } | null

/**
 * Helpdesk categories at /support/admin/categories, active and inactive, from the admin category API. Admins add and
 * edit them; a category is made inactive instead of deleted, and its code never changes. There is no Delete.
 */
export function SupportCategoriesPage() {
  const { account } = useAuth()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  const request = useApiRequest(isAdmin ? getAdminHelpdeskCategories : null)
  const [open, setOpen] = useState<Open>(null)
  const [announcement, setAnnouncement] = useState('')
  const pageRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(pageRef)

  function close(focusKey: string) {
    setOpen(null)
    focusLater(focusKey)
  }

  async function create(values: CategoryValues) {
    const created = await createHelpdeskCategory({
      code: values.code.trim(),
      name: values.name.trim(),
      description: values.description.trim() || null,
      active: true,
    })
    setAnnouncement(`Category ${created.name} added.`)
    request.refresh()
    close(`edit-${created.categoryId}`)
  }

  async function update(categoryId: number, values: CategoryValues) {
    const updated = await updateHelpdeskCategory(categoryId, {
      name: values.name.trim(),
      description: values.description.trim() || null,
      active: values.active,
    })
    setAnnouncement(`Category ${updated.name} saved${updated.active ? '' : ' as inactive'}.`)
    request.refresh()
    close(`edit-${categoryId}`)
  }

  let content
  if (!isAdmin) {
    content = (
      <StatePanel>
        <AccessDeniedState title="Helpdesk categories are managed by admin accounts." />
      </StatePanel>
    )
  } else if (request.status === 'loading') {
    content = (
      <StatePanel>
        <LoadingState label="Loading categories…" />
      </StatePanel>
    )
  } else if (request.status === 'denied') {
    content = (
      <StatePanel>
        <AccessDeniedState title="You don’t have access to helpdesk categories." message={SIGN_IN_AGAIN} />
      </StatePanel>
    )
  } else if (request.status !== 'loaded') {
    content = (
      <StatePanel>
        <ErrorState title="Couldn’t load helpdesk categories." message="Check your connection and try again." onRetry={request.retry} />
      </StatePanel>
    )
  } else if (request.data.length === 0) {
    content = (
      <StatePanel>
        <EmptyState title="No categories yet." description="Add the first one so people can raise tickets." />
      </StatePanel>
    )
  } else {
    content = (
      <ul aria-label="Helpdesk categories" className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
        {request.data.map((category) => (
          <li key={category.categoryId} className="px-4 py-3 sm:px-5">
            {open?.type === 'edit' && open.categoryId === category.categoryId ? (
              <CategoryForm
                editing={category}
                onSubmit={(values) => update(category.categoryId, values)}
                onCancel={() => close(`edit-${category.categoryId}`)}
              />
            ) : (
              <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-xs text-ink-muted">{category.code}</span>
                    <span
                      className={`inline-block shrink-0 rounded-sm border px-1.5 py-0.5 text-xs font-medium ${
                        category.active ? 'border-success-line text-success' : 'border-line-strong text-ink-muted'
                      }`}
                    >
                      {category.active ? 'Active' : 'Inactive'}
                    </span>
                  </div>
                  <p className="mt-1 text-sm font-semibold break-words text-ink">{category.name}</p>
                  {category.description && <p className="mt-0.5 text-sm break-words whitespace-pre-line text-ink-muted">{category.description}</p>}
                </div>
                <ActionButton
                  focusKey={`edit-${category.categoryId}`}
                  className="self-start"
                  aria-disabled={open !== null || undefined}
                  onClick={() => open === null && setOpen({ type: 'edit', categoryId: category.categoryId })}
                >
                  Edit<span className="sr-only"> {category.name}</span>
                </ActionButton>
              </div>
            )}
          </li>
        ))}
      </ul>
    )
  }

  return (
    <div ref={pageRef}>
      <title>Helpdesk Categories · Stacc</title>
      <Breadcrumb parent={{ to: '/support', label: 'Support' }} current="Helpdesk Categories" />
      <h1 className="mt-2 text-2xl font-semibold tracking-tight text-ink">Helpdesk Categories</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        The kinds of issue people choose when they raise a ticket. Categories are made inactive instead of deleted.
      </p>
      <p role="status" className="sr-only">
        {announcement}
      </p>

      {isAdmin && request.status === 'loaded' && (
        <div className="mt-5">
          {open?.type === 'create' ? (
            <CategoryForm editing={null} onSubmit={create} onCancel={() => close('add-category')} />
          ) : (
            <Button
              data-focus-key="add-category"
              aria-disabled={open !== null || undefined}
              onClick={() => open === null && setOpen({ type: 'create' })}
            >
              Add category
            </Button>
          )}
        </div>
      )}

      <div className="mt-4">{content}</div>
    </div>
  )
}
