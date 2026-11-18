import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  addGrievanceCommitteeMember,
  changeGrievanceCategory,
  createGrievanceCategory,
  endGrievanceCommitteeMember,
  getAdminGrievanceCategories,
} from '../api/grievanceApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { StatePanel } from '../components/states/StatePanel'
import { SectionState } from '../courses/SectionState'
import { failureText } from '../engagement/engagementText'
import type { GrievanceCategoryAdmin } from '../types/grievances'
import { Alert, Badge, Button, Card, DataTable, PageHeader, TextField } from '../ui'

type Notify = (text: string, error?: boolean) => void

function attempt(action: () => Promise<unknown>, done: string, fallback: string, notify: Notify) {
  void action()
    .then(() => notify(done))
    .catch((caught: unknown) => notify(failureText(caught, fallback) ?? '', true))
}

function SubHeading({ children }: { children: string }) {
  return (
    <>
      <h3 className="text-base font-bold text-portal-navy">{children}</h3>
      <span aria-hidden="true" className="mt-1 mb-2 block h-0.5 w-12 rounded bg-portal-cta" />
    </>
  )
}

function CategoryCard({ category, notify }: { category: GrievanceCategoryAdmin; notify: Notify }) {
  const [loginId, setLoginId] = useState('')
  return (
    <Card
      title={category.name}
      description={
        <>
          Category: <span className="font-semibold text-ink">{category.code}</span>
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, </span>
          Answer within: {category.responseDays} days
        </>
      }
      action={!category.active ? <Badge tone="neutral">Not taking new cases</Badge> : undefined}
    >
      <div className="space-y-5">
        <div>
          <SubHeading>Committee members</SubHeading>
          {category.committee.length === 0 ? (
            <p className="rounded-control border border-line px-3 py-4 text-center text-sm text-ink">
              <span className="font-semibold">Nobody yet:</span> cases in this category cannot be answered until someone is added.
            </p>
          ) : (
            <ul className="divide-y divide-line rounded-control border border-line text-sm">
              {category.committee.map((member) => (
                <li key={member.memberId} className="flex items-center justify-between gap-3 px-3 py-2">
                  <span className="text-ink">{member.loginId}</span>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() =>
                      attempt(
                        () => endGrievanceCommitteeMember(member.memberId),
                        `${member.loginId} is no longer on the committee.`,
                        'Couldn’t change the committee.',
                        notify,
                      )
                    }
                  >
                    Remove<span className="sr-only">{` ${member.loginId} from ${category.name}`}</span>
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <form
          className="space-y-2"
          onSubmit={(event) => {
            event.preventDefault()
            if (loginId.trim() !== '') {
              attempt(
                () => addGrievanceCommitteeMember(category.categoryId, loginId.trim()).then(() => setLoginId('')),
                `${loginId.trim()} added to the ${category.name} committee.`,
                'Couldn’t add the member.',
                notify,
              )
            }
          }}
        >
          <SubHeading>Add member</SubHeading>
          <TextField label={`Faculty or admin login ID for ${category.name}`} value={loginId} maxLength={100} onChange={(event) => setLoginId(event.target.value)} />
          <Button type="submit">Add to committee</Button>
        </form>
        <div className="border-t border-line pt-4">
          <SubHeading>Category</SubHeading>
          <Button
            variant="secondary"
            onClick={() =>
              attempt(
                () => changeGrievanceCategory(category.categoryId, { name: category.name, responseDays: category.responseDays, active: !category.active }),
                category.active ? 'Category closed to new cases.' : 'Category open to new cases.',
                'Couldn’t change the category.',
                notify,
              )
            }
          >
            {category.active ? 'Stop new cases' : 'Take new cases'}
            <span className="sr-only">{` in ${category.name}`}</span>
          </Button>
          <p className="mt-3 text-sm text-ink-muted">Committee members can open this category’s cases. Admin setup never shows case details.</p>
        </div>
      </div>
    </Card>
  )
}

function CategorySummary({ categories }: { categories: GrievanceCategoryAdmin[] }) {
  return (
    <Card title="Existing categories" padding="none">
      <DataTable<GrievanceCategoryAdmin>
        caption="Grievance categories"
        rows={categories}
        rowKey={(category) => category.categoryId}
        columns={[
          { key: 'code', header: 'Code', primary: true, cell: (category) => <span className="font-semibold">{category.code}</span> },
          { key: 'name', header: 'Name', cell: (category) => category.name },
          { key: 'days', header: 'Answer within', cell: (category) => `${category.responseDays} days` },
          {
            key: 'committee',
            header: 'Committee',
            cell: (category) => (category.committee.length === 0 ? 'Nobody yet' : category.committee.map((member) => member.loginId).join(', ')),
          },
          { key: 'new', header: 'New cases', cell: (category) => (category.active ? 'Taking new cases' : 'Not taking new cases') },
        ]}
      />
    </Card>
  )
}

export function AdminGrievancesPage() {
  const { account } = useAuth()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  const queryClient = useQueryClient()
  const categories = useApiQuery(isAdmin ? queryKeys.grievances.admin() : null, (signal) => getAdminGrievanceCategories(signal))
  const [form, setForm] = useState({ code: '', name: '', responseDays: '7' })
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const notify: Notify = (text, error = false) => {
    setMessage({ tone: error ? 'error' : 'success', text })
    void queryClient.invalidateQueries({ queryKey: queryKeys.grievances.all })
  }
  return (
    <>
      <title>Grievance committees · Stacc</title>
      <PageHeader title="Grievance committees" description="Categories, response times, and who answers each. Cases stay with their committees." />
      <div className="mt-6 space-y-6">
        {!isAdmin ? (
          <StatePanel><AccessDeniedState title="Grievance committees are managed by admin accounts." /></StatePanel>
        ) : (
          <>
            <p role="status" className="sr-only">{message?.tone === 'success' ? message.text : ''}</p>
            {message && <Alert tone={message.tone}>{message.text}</Alert>}
            <Card title="Add a category">
              <form
                className="grid gap-3 sm:grid-cols-[12rem_minmax(0,1fr)_10rem_auto] sm:items-end"
                onSubmit={(event) => {
                  event.preventDefault()
                  attempt(() => createGrievanceCategory({ code: form.code.trim(), name: form.name.trim(), responseDays: Number(form.responseDays) }), 'Category added.', 'Couldn’t add the category.', notify)
                }}
              >
                <TextField label="Code" hint="Capital letters, fixed later." value={form.code} maxLength={50} onChange={(event) => setForm({ ...form, code: event.target.value.toUpperCase() })} />
                <TextField label="Name" value={form.name} maxLength={150} onChange={(event) => setForm({ ...form, name: event.target.value })} />
                <TextField label="Answer within (days)" inputMode="numeric" value={form.responseDays} onChange={(event) => setForm({ ...form, responseDays: event.target.value.replace(/\D/g, '') })} />
                <div><Button type="submit">Add category</Button></div>
              </form>
            </Card>
            {categories.status !== 'loaded' ? (
              <SectionState request={categories} what="grievance categories" />
            ) : categories.data.length === 0 ? (
              <EmptyState title="No categories yet." description="Add one above, then name its committee." />
            ) : (
              <>
                <CategorySummary categories={categories.data} />
                <div className="grid items-start gap-6 xl:grid-cols-2">
                  {categories.data.map((category) => (
                    <CategoryCard key={category.categoryId} category={category} notify={notify} />
                  ))}
                </div>
              </>
            )}
          </>
        )}
      </div>
    </>
  )
}
