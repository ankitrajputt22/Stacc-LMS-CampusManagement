import { SupportHeader } from '../support/SupportHeader'
import { useRef, useState } from 'react'
import { createHelpdeskCategory, getAdminHelpdeskCategories, updateHelpdeskCategory } from '../api/helpdeskApi'
import { useApiRequest } from '../api/useApiRequest'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { usePendingFocus } from '../components/usePendingFocus'
import { CategoryForm, type CategoryValues } from '../support/CategoryForm'
import { SIGN_IN_AGAIN } from '../support/helpdeskText'

type Open = { type: 'create' } | { type: 'edit'; categoryId: number } | null

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
        {request.data.map((category) => {
          const editing = open?.type === 'edit' && open.categoryId === category.categoryId
          return (
            <li
              key={category.categoryId}
              className={`grid gap-2 px-4 py-3 sm:grid-cols-[8rem_minmax(0,1fr)_6rem_auto] sm:items-center sm:gap-4 sm:px-5 ${editing ? 'bg-sky-50' : ''}`}
            >
              <span className="font-mono text-sm text-ink">{category.code}</span>
              <div className="min-w-0">
                <p className="text-[0.95rem] font-semibold break-words text-ink">{category.name}</p>
                {category.description && <p className="mt-0.5 text-sm break-words whitespace-pre-line text-ink-muted">{category.description}</p>}
              </div>
              <span
                className={`inline-block justify-self-start rounded-sm border px-1.5 py-0.5 text-xs font-medium ${
                  category.active ? 'border-success-line bg-success-soft text-ink' : 'border-line-strong bg-surface-muted text-ink-muted'
                }`}
              >
                {category.active ? 'Active' : 'Inactive'}
              </span>
              <ActionButton
                focusKey={`edit-${category.categoryId}`}
                className="justify-self-start"
                aria-disabled={open !== null || undefined}
                onClick={() => open === null && setOpen({ type: 'edit', categoryId: category.categoryId })}
              >
                Edit<span className="sr-only"> {category.name}</span>
              </ActionButton>
            </li>
          )
        })}
      </ul>
    )
  }

  const editingCategory =
    open?.type === 'edit' && request.status === 'loaded' ? request.data.find((category) => category.categoryId === open.categoryId) : undefined

  return (
    <div ref={pageRef}>
      <title>Helpdesk Categories · Stacc</title>
      <SupportHeader
        title="Helpdesk Categories"
        description="The kinds of issue people choose when they raise a ticket. Categories are made inactive instead of deleted."
      />
      <p role="status" className="sr-only">
        {announcement}
      </p>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_24rem]">
        <section aria-labelledby="categories-heading" className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
            <h2 id="categories-heading" className="text-xl font-bold text-portal-navy">
              Categories
            </h2>
            {isAdmin && request.status === 'loaded' && (
              <Button
                data-focus-key="add-category"
                aria-disabled={open !== null || undefined}
                onClick={() => open === null && setOpen({ type: 'create' })}
              >
                Add category
              </Button>
            )}
          </div>
          {content}
        </section>
        {isAdmin && request.status === 'loaded' && (
          <aside aria-label="Category form" className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
            {open?.type === 'create' ? (
              <CategoryForm editing={null} onSubmit={create} onCancel={() => close('add-category')} />
            ) : editingCategory ? (
              <CategoryForm
                key={editingCategory.categoryId}
                editing={editingCategory}
                onSubmit={(values) => update(editingCategory.categoryId, values)}
                onCancel={() => close(`edit-${editingCategory.categoryId}`)}
              />
            ) : (
              <p className="text-sm leading-6 text-ink-muted">
                Choose Edit on a category, or Add category. Codes are fixed once created; an inactive category keeps its tickets but takes no new ones.
              </p>
            )}
          </aside>
        )}
      </div>
    </div>
  )
}
