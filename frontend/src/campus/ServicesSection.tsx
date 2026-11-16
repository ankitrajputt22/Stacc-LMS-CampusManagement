import { NavIcon } from '../components/navigation/navIcons'
import { type FormEvent, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import { createCampusRequest, getCampusServices } from '../api/campusApi'
import { useApiRequest } from '../api/useApiRequest'
import { refusalText, SIGN_IN_AGAIN, plainTagClasses } from '../approvals/approvalText'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { border, fieldClasses } from '../teaching/formStyles'
import type { CampusService } from '../types/campus'
import { DETAILS_MAX, SUBJECT_MAX } from './campusText'

/** The request form for one service: only a subject and details. The requester is the account in the access token. */
function RequestForm({ service, onClose }: { service: CampusService; onClose: () => void }) {
  const navigate = useNavigate()
  const busyRef = useRef(false)
  const [subject, setSubject] = useState('')
  const [details, setDetails] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<{ field: 'subject' | 'details' | null; text: string } | null>(null)
  const prefix = `campus-request-${service.serviceId}`

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busyRef.current) {
      return
    }
    if (subject.trim() === '') {
      setError({ field: 'subject', text: 'Enter a subject.' })
      document.getElementById(`${prefix}-subject`)?.focus()
      return
    }
    if (details.trim() === '') {
      setError({ field: 'details', text: 'Enter the details of your request.' })
      document.getElementById(`${prefix}-details`)?.focus()
      return
    }
    busyRef.current = true
    setBusy(true)
    setError(null)
    try {
      const created = await createCampusRequest({ serviceId: service.serviceId, subject: subject.trim(), details: details.trim() })
      navigate(`/campus/requests/${created.requestId}`, { state: { createdNumber: created.requestNumber } })
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError({ field: null, text: refusalText(status, getApiError(failure)?.message ?? null, 'submit the request') })
      }
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate aria-label={`Request ${service.name}`} className="mt-3 grid gap-3 rounded-control border border-line-strong p-3">
      <p className="text-sm text-ink">
        <span className="font-medium">Service:</span> {service.name}
      </p>
      <div>
        <label htmlFor={`${prefix}-subject`} className="block text-sm font-medium text-ink">
          Subject
        </label>
        <input
          id={`${prefix}-subject`}
          type="text"
          autoComplete="off"
          maxLength={SUBJECT_MAX}
          value={subject}
          aria-invalid={error?.field === 'subject' || undefined}
          onChange={(event) => {
            setSubject(event.target.value)
            setError(null)
          }}
          className={`h-11 ${fieldClasses} ${border(error?.field === 'subject')}`}
        />
      </div>
      <div>
        <label htmlFor={`${prefix}-details`} className="block text-sm font-medium text-ink">
          Details
        </label>
        <textarea
          id={`${prefix}-details`}
          rows={5}
          maxLength={DETAILS_MAX}
          value={details}
          aria-invalid={error?.field === 'details' || undefined}
          aria-describedby={`${prefix}-details-hint`}
          onChange={(event) => {
            setDetails(event.target.value)
            setError(null)
          }}
          className={`py-2 ${fieldClasses} ${border(error?.field === 'details')}`}
        />
        <p id={`${prefix}-details-hint`} className="mt-1 text-xs text-ink-muted">
          Plain text. For a question or a problem, use Support instead.
        </p>
      </div>
      <div role="alert" className="empty:hidden">
        {error && <p className="rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm text-danger">{error.text}</p>}
      </div>
      <div className="flex flex-col gap-2 sm:flex-row">
        <Button type="submit" aria-disabled={busy || undefined}>
          {busy ? 'Submitting…' : 'Submit request'}
        </Button>
        <ActionButton onClick={onClose}>Cancel</ActionButton>
      </div>
    </form>
  )
}

/** The active services the account may request through any role it holds, each once, as the backend lists them. */
export function ServicesSection() {
  const services = useApiRequest(getCampusServices)
  const [open, setOpen] = useState<number | null>(null)

  let content
  if (services.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading campus information…" /></StatePanel>
  } else if (services.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to campus services." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (services.status !== 'loaded') {
    content = (
      <StatePanel>
        <ErrorState title="Couldn’t load campus services." message="Check your connection and try again." onRetry={services.retry} />
      </StatePanel>
    )
  } else if (services.data.length === 0) {
    content = <StatePanel><EmptyState title="No campus services are currently available." /></StatePanel>
  } else {
    content = (
      <ul aria-label="Campus services" className="space-y-3">
        {services.data.map((service) => (
          <li key={service.serviceId} className="rounded-panel border border-line bg-surface px-4 py-3 sm:px-5">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
              <span aria-hidden="true" className="hidden size-12 shrink-0 items-center justify-center rounded-control bg-surface-muted text-ink-muted sm:flex">
                <NavIcon name="documents" className="size-6" />
              </span>
              <div className="min-w-0 flex-1 sm:border-l sm:border-line sm:pl-4">
                <h3 className="text-base font-bold break-words text-ink">{service.name}</h3>
                {service.description && (
                  <p className="mt-0.5 text-sm leading-6 break-words whitespace-pre-wrap text-ink-muted">{service.description}</p>
                )}
              </div>
              {service.approvalRequired && <span className={`${plainTagClasses()} self-start sm:self-center`}>Approval required</span>}
              {open !== service.serviceId && (
                <Button className="shrink-0 self-start sm:self-center" onClick={() => setOpen(service.serviceId)}>
                  Request service<span className="sr-only">: {service.name}</span>
                </Button>
              )}
            </div>
            {open === service.serviceId && <RequestForm service={service} onClose={() => setOpen(null)} />}
          </li>
        ))}
      </ul>
    )
  }

  return (
    <section aria-labelledby="campus-services-heading" className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
      <h2 id="campus-services-heading" className="text-xl font-bold text-portal-navy">
        Campus Services
      </h2>
      <span aria-hidden="true" className="mt-1.5 block h-0.5 w-16 rounded bg-portal-cta" />
      <div className="mt-4">{content}</div>
    </section>
  )
}
