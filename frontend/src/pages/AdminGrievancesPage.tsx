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
import { Alert, Badge, Button, Card, PageHeader, TextField } from '../ui'

type Notify = (text: string, error?: boolean) => void

function attempt(action: () => Promise<unknown>, done: string, fallback: string, notify: Notify) {
  void action()
    .then(() => notify(done))
    .catch((caught: unknown) => notify(failureText(caught, fallback) ?? '', true))
}

function CategoryCard({ category, notify }: { category: GrievanceCategoryAdmin; notify: Notify }) {
  const [loginId, setLoginId] = useState('')
  return (
    <Card
      title={category.name}
      description={`${category.code} · answer within ${category.responseDays} days`}
      action={!category.active ? <Badge tone="neutral">Not taking new cases</Badge> : undefined}
    >
      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-ink">Committee</h3>
        {category.committee.length === 0 ? (
          <p className="text-sm text-ink-muted">Nobody yet: cases in this category cannot be answered until someone is added.</p>
        ) : (
          <ul className="flex flex-wrap gap-2 text-sm">
            {category.committee.map((member) => (
              <li key={member.memberId} className="inline-flex items-center gap-2 rounded-control border border-line px-2 py-1">
                <span className="font-mono">{member.loginId}</span>
                <Button size="sm" variant="secondary" onClick={() => attempt(() => endGrievanceCommitteeMember(member.memberId), `${member.loginId} is no longer on the committee.`, 'Couldn’t change the committee.', notify)}>
                  Remove<span className="sr-only">{` ${member.loginId} from ${category.name}`}</span>
                </Button>
              </li>
            ))}
          </ul>
        )}
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            if (loginId.trim() !== '') {
              attempt(() => addGrievanceCommitteeMember(category.categoryId, loginId.trim()).then(() => setLoginId('')), `${loginId.trim()} added to the ${category.name} committee.`, 'Couldn’t add the member.', notify)
            }
          }}
        >
          <TextField label={`Faculty or admin login ID for ${category.name}`} value={loginId} maxLength={100} onChange={(event) => setLoginId(event.target.value)} />
          <Button type="submit" size="sm">Add to committee</Button>
        </form>
        <Button
          size="sm"
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
      </div>
    </Card>
  )
}

/** /admin/grievances: grievance categories and their committees. Admins never read cases here; committees do. */
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
                className="grid gap-3 sm:grid-cols-3"
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
              categories.data.map((category) => <CategoryCard key={category.categoryId} category={category} notify={notify} />)
            )}
          </>
        )}
      </div>
    </>
  )
}
