import { type FormEvent, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import { createHelpdeskTicket, getHelpdeskCategories } from '../api/helpdeskApi'
import { useApiRequest } from '../api/useApiRequest'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Button } from '../components/Button'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { border, fieldClasses } from '../teaching/formStyles'
import {
  DESCRIPTION_MAX,
  priorityLabel,
  REQUESTER_PRIORITIES,
  refusalText,
  SIGN_IN_AGAIN,
  SUBJECT_MAX,
} from '../support/helpdeskText'
import type { CreateHelpdeskTicketRequest, HelpdeskCategory } from '../types/helpdesk'

type Field = 'categoryId' | 'subject' | 'priority' | 'description'
type Values = Record<Field, string>
type Errors = Partial<Record<Field, string>>

const FIELD_ORDER: Field[] = ['categoryId', 'subject', 'priority', 'description']
const CATEGORY_GONE = 'This category is no longer available. Choose another one.'

function checked(values: Values): Errors {
  const errors: Errors = {}
  if (values.categoryId === '') errors.categoryId = 'Choose a category.'
  if (values.subject.trim() === '') errors.subject = 'Subject is required.'
  else if (values.subject.trim().length > SUBJECT_MAX) errors.subject = `Use at most ${SUBJECT_MAX} characters.`
  if (!REQUESTER_PRIORITIES.some((priority) => priority === values.priority)) errors.priority = 'Choose a priority.'
  if (values.description.trim() === '') errors.description = 'Description is required.'
  else if (values.description.trim().length > DESCRIPTION_MAX) errors.description = `Use at most ${DESCRIPTION_MAX} characters.`
  return errors
}

function NewTicketForm({ categories, onCategoryGone }: { categories: HelpdeskCategory[]; onCategoryGone: () => void }) {
  const navigate = useNavigate()
  const submittingRef = useRef(false)
  const [values, setValues] = useState<Values>({ categoryId: '', subject: '', priority: 'NORMAL', description: '' })
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function change(field: Field, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function focusFirst(found: Errors) {
    const first = FIELD_ORDER.find((field) => found[field])
    if (first) document.getElementById(`new-ticket-${first}`)?.focus()
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }
    const found = checked(values)
    setErrors(found)
    setFormError(null)
    if (Object.values(found).some(Boolean)) {
      focusFirst(found)
      return
    }
    submittingRef.current = true
    setSubmitting(true)
    const request: CreateHelpdeskTicketRequest = {
      categoryId: Number(values.categoryId),
      subject: values.subject.trim(),
      description: values.description,
      priority: values.priority as CreateHelpdeskTicketRequest['priority'],
    }
    try {
      const ticket = await createHelpdeskTicket(request)
      // No reload: the ticket page opens and says which number the ticket got.
      navigate(`/support/tickets/${ticket.ticketId}`, { state: { createdNumber: ticket.ticketNumber } })
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status === 401) {
        return
      }
      const apiError = getApiError(failure)
      const fieldErrors: Errors = {}
      for (const error of apiError?.fieldErrors ?? []) {
        if (FIELD_ORDER.includes(error.field as Field)) fieldErrors[error.field as Field] = error.message
      }
      if (status === 404 || (status === 400 && apiError?.message.includes('category'))) {
        // The category was removed or made inactive meanwhile: the list is loaded again without it.
        fieldErrors.categoryId = CATEGORY_GONE
        change('categoryId', '')
        onCategoryGone()
      }
      setErrors(fieldErrors)
      setFormError(Object.keys(fieldErrors).length > 0 ? 'Check the highlighted field.' : refusalText(status, apiError?.message ?? null, 'create the ticket'))
      focusFirst(fieldErrors)
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  const describedBy = (field: Field, hint?: boolean) =>
    [hint ? `new-ticket-${field}-hint` : '', errors[field] ? `new-ticket-${field}-error` : ''].filter(Boolean).join(' ') || undefined
  const errorText = (field: Field) =>
    errors[field] && (
      <p id={`new-ticket-${field}-error`} className="mt-1.5 text-sm text-danger">
        {errors[field]}
      </p>
    )

  return (
    <form onSubmit={submit} noValidate aria-label="New support ticket" className="rounded-panel border border-line bg-surface p-4 sm:p-5">
      <p className="text-sm text-ink-muted">All fields are required.</p>
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="new-ticket-categoryId" className="block text-sm font-medium text-ink">
            Category
          </label>
          <select
            id="new-ticket-categoryId"
            required
            value={values.categoryId}
            aria-invalid={errors.categoryId ? true : undefined}
            aria-describedby={describedBy('categoryId')}
            onChange={(event) => change('categoryId', event.target.value)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.categoryId))}`}
          >
            <option value="">Choose a category</option>
            {categories.map((category) => (
              <option key={category.categoryId} value={category.categoryId}>
                {category.name}
              </option>
            ))}
          </select>
          {errorText('categoryId')}
        </div>
        <div>
          <label htmlFor="new-ticket-priority" className="block text-sm font-medium text-ink">
            Priority
          </label>
          <select
            id="new-ticket-priority"
            required
            value={values.priority}
            aria-invalid={errors.priority ? true : undefined}
            aria-describedby={describedBy('priority', true)}
            onChange={(event) => change('priority', event.target.value)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.priority))}`}
          >
            {REQUESTER_PRIORITIES.map((priority) => (
              <option key={priority} value={priority}>
                {priorityLabel(priority)}
              </option>
            ))}
          </select>
          <p id="new-ticket-priority-hint" className="mt-1 text-xs text-ink-muted">
            The support team may change it.
          </p>
          {errorText('priority')}
        </div>
        <div className="sm:col-span-2">
          <label htmlFor="new-ticket-subject" className="block text-sm font-medium text-ink">
            Subject
          </label>
          <input
            id="new-ticket-subject"
            type="text"
            required
            autoComplete="off"
            maxLength={SUBJECT_MAX}
            value={values.subject}
            aria-invalid={errors.subject ? true : undefined}
            aria-describedby={describedBy('subject', true)}
            onChange={(event) => change('subject', event.target.value)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.subject))}`}
          />
          <p id="new-ticket-subject-hint" className="mt-1 text-xs text-ink-muted">
            A short summary, up to {SUBJECT_MAX} characters.
          </p>
          {errorText('subject')}
        </div>
        <div className="sm:col-span-2">
          <label htmlFor="new-ticket-description" className="block text-sm font-medium text-ink">
            Description
          </label>
          <textarea
            id="new-ticket-description"
            required
            rows={8}
            maxLength={DESCRIPTION_MAX}
            value={values.description}
            aria-invalid={errors.description ? true : undefined}
            aria-describedby={describedBy('description', true)}
            onChange={(event) => change('description', event.target.value)}
            className={`py-2 ${fieldClasses} ${border(Boolean(errors.description))}`}
          />
          <p id="new-ticket-description-hint" className="mt-1 text-xs text-ink-muted">
            What happened, and what you expected. Plain text, up to {DESCRIPTION_MAX.toLocaleString()} characters.
          </p>
          {errorText('description')}
        </div>
      </div>

      <div role="alert">
        {formError && (
          <p className="mt-4 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger">{formError}</p>
        )}
      </div>

      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        <Button type="submit" aria-disabled={submitting || undefined}>
          {submitting ? 'Creating ticket…' : 'Create ticket'}
        </Button>
        <Button variant="secondary" onClick={() => navigate('/support')}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

/** A new ticket at /support/new, raised as the signed-in account in one of the active categories. */
export function SupportNewTicketPage() {
  const { account } = useAuth()
  const hasSupport = hasAnyRole(account?.roles ?? [], ['STUDENT', 'FACULTY', 'ADMIN'])
  const categories = useApiRequest(hasSupport ? getHelpdeskCategories : null)

  let content
  if (!hasSupport) {
    content = <AccessDeniedState title="Support is for student, faculty, and admin accounts." />
  } else if (categories.status === 'loading') {
    content = <LoadingState label="Loading categories…" />
  } else if (categories.status === 'denied') {
    content = <AccessDeniedState title="You don’t have access to create support tickets." message={SIGN_IN_AGAIN} />
  } else if (categories.status !== 'loaded') {
    content = <ErrorState title="Couldn’t load helpdesk categories." message="Check your connection and try again." onRetry={categories.retry} />
  } else if (categories.data.length === 0) {
    content = <EmptyState title="No categories are available yet." description="Contact the college administration for help." />
  }

  return (
    <>
      <title>New ticket · Stacc</title>
      <Breadcrumb parent={{ to: '/support', label: 'Support' }} current="New ticket" />
      <h1 className="mt-2 text-2xl font-semibold tracking-tight text-ink">Create ticket</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        Describe the issue. You can follow the ticket and reply to the support team on its page.
      </p>
      <div className="mt-6">
        {content ? (
          <StatePanel>{content}</StatePanel>
        ) : (
          categories.status === 'loaded' && <NewTicketForm categories={categories.data} onCategoryGone={categories.refresh} />
        )}
      </div>
    </>
  )
}
