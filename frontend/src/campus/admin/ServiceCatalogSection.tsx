import { type FormEvent, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../../api/apiError'
import { getApprovalWorkflows } from '../../api/approvalsApi'
import { createCampusService, getAdminCampusServices, updateCampusService } from '../../api/campusApi'
import { useApiRequest } from '../../api/useApiRequest'
import { plainTagClasses, refusalText, SIGN_IN_AGAIN } from '../../approvals/approvalText'
import { ActionButton } from '../../components/ActionButton'
import { Button } from '../../components/Button'
import { AccessDeniedState } from '../../components/states/AccessDeniedState'
import { EmptyState } from '../../components/states/EmptyState'
import { ErrorState } from '../../components/states/ErrorState'
import { LoadingState } from '../../components/states/LoadingState'
import { StatePanel } from '../../components/states/StatePanel'
import { border, fieldClasses } from '../../teaching/formStyles'
import type { ApprovalWorkflowSummary } from '../../types/approvals'
import type { AdminCampusService } from '../../types/campus'
import { audienceLabel, SERVICE_CODE_MAX, SERVICE_DESCRIPTION_MAX, SERVICE_NAME_MAX } from '../campusText'
import { CheckboxField } from './CheckboxField'

type Field = 'code' | 'name' | 'audience' | 'workflow'

type ServiceFormProps = {
  /** The service being edited, or none to add one. */
  service: AdminCampusService | null
  workflows: ApprovalWorkflowSummary[] | null
  onSaved: (message: string) => void
  onClose: () => void
}

/**
 * Add or edit a service. The code is typed once and read-only afterwards. When approval is required, the workflow is
 * chosen from the approval engine's own list, never typed; the backend checks again that it exists and is active.
 */
function ServiceForm({ service, workflows, onSaved, onClose }: ServiceFormProps) {
  const prefix = service ? `campus-service-${service.serviceId}` : 'campus-service-new'
  const busyRef = useRef(false)
  const [code, setCode] = useState('')
  const [name, setName] = useState(service?.name ?? '')
  const [description, setDescription] = useState(service?.description ?? '')
  const [student, setStudent] = useState(service?.availableToStudent ?? true)
  const [faculty, setFaculty] = useState(service?.availableToFaculty ?? false)
  const [approval, setApproval] = useState(service?.approvalRequired ?? false)
  const [workflow, setWorkflow] = useState(service?.approvalWorkflowCode ?? '')
  const [active, setActive] = useState(service?.active ?? true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<{ field: Field | null; text: string } | null>(null)

  // Active workflows can be chosen; a service's current one stays listed even if it was made inactive meanwhile.
  const choices = (workflows ?? []).filter((item) => item.active || item.code === service?.approvalWorkflowCode)

  function fail(field: Field | null, text: string, focusId?: string) {
    setError({ field, text })
    if (focusId) document.getElementById(focusId)?.focus()
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busyRef.current) return
    if (!service && code.trim() === '') return fail('code', 'Enter a code.', `${prefix}-code`)
    if (!service && !/^[A-Za-z][A-Za-z0-9_]*$/.test(code.trim())) {
      return fail('code', 'Use letters, digits, and underscores, starting with a letter.', `${prefix}-code`)
    }
    if (name.trim() === '') return fail('name', 'Enter a name.', `${prefix}-name`)
    if (!student && !faculty) return fail('audience', 'Choose students, faculty, or both.', `${prefix}-student`)
    if (approval && workflow === '') return fail('workflow', 'Choose the approval workflow.', `${prefix}-workflow`)
    busyRef.current = true
    setBusy(true)
    setError(null)
    const settings = {
      name: name.trim(),
      description: description.trim() === '' ? null : description.trim(),
      active,
      availableToStudent: student,
      availableToFaculty: faculty,
      approvalRequired: approval,
      approvalWorkflowCode: approval ? workflow : null,
    }
    try {
      if (service) {
        await updateCampusService(service.serviceId, settings)
        onSaved(`${service.code} saved.`)
      } else {
        const created = await createCampusService({ code: code.trim(), ...settings })
        onSaved(`${created.code} added.`)
      }
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        fail(null, refusalText(status, getApiError(failure)?.message ?? null, 'save the service'))
      }
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate aria-label={service ? `Edit ${service.code}` : 'Add service'} className="mt-3 grid gap-4 rounded-control border border-line-strong bg-surface p-3 sm:grid-cols-2">
      <div>
        {service ? (
          <>
            <p className="block text-sm font-medium text-ink">Code</p>
            <p className="mt-1.5 font-mono text-sm text-ink">{service.code}</p>
            <p className="mt-0.5 text-xs text-ink-muted">The code cannot change.</p>
          </>
        ) : (
          <>
            <label htmlFor={`${prefix}-code`} className="block text-sm font-medium text-ink">Code</label>
            <input
              id={`${prefix}-code`}
              type="text"
              autoComplete="off"
              maxLength={SERVICE_CODE_MAX}
              value={code}
              aria-invalid={error?.field === 'code' || undefined}
              aria-describedby={`${prefix}-code-hint`}
              onChange={(event) => { setCode(event.target.value); setError(null) }}
              className={`h-11 font-mono ${fieldClasses} ${border(error?.field === 'code')}`}
            />
            <p id={`${prefix}-code-hint`} className="mt-1 text-xs text-ink-muted">Such as ID_CARD. Stored in uppercase and never changed.</p>
          </>
        )}
      </div>
      <div>
        <label htmlFor={`${prefix}-name`} className="block text-sm font-medium text-ink">Name</label>
        <input
          id={`${prefix}-name`}
          type="text"
          autoComplete="off"
          maxLength={SERVICE_NAME_MAX}
          value={name}
          aria-invalid={error?.field === 'name' || undefined}
          onChange={(event) => { setName(event.target.value); setError(null) }}
          className={`h-11 ${fieldClasses} ${border(error?.field === 'name')}`}
        />
      </div>
      <div className="sm:col-span-2">
        <label htmlFor={`${prefix}-description`} className="block text-sm font-medium text-ink">
          Description <span className="font-normal text-ink-muted">(optional)</span>
        </label>
        <textarea
          id={`${prefix}-description`}
          rows={3}
          maxLength={SERVICE_DESCRIPTION_MAX}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          className={`py-2 ${fieldClasses} ${border(false)}`}
        />
      </div>
      <fieldset className="sm:col-span-2">
        <legend className="text-sm font-medium text-ink">Available to</legend>
        <div className="mt-1 flex flex-wrap gap-x-6">
          <CheckboxField id={`${prefix}-student`} label="Available to Student" checked={student} onChange={(next) => { setStudent(next); setError(null) }} />
          <CheckboxField id={`${prefix}-faculty`} label="Available to Faculty" checked={faculty} onChange={(next) => { setFaculty(next); setError(null) }} />
        </div>
      </fieldset>
      <div className="sm:col-span-2">
        <CheckboxField
          id={`${prefix}-approval`}
          label="Approval required"
          checked={approval}
          describedBy={`${prefix}-approval-hint`}
          onChange={(next) => { setApproval(next); setError(null) }}
        />
        <p id={`${prefix}-approval-hint`} className="text-xs text-ink-muted">
          Requests then wait for the approval workflow. Changes apply to new requests only.
        </p>
      </div>
      {approval && (
        <div className="sm:col-span-2">
          <label htmlFor={`${prefix}-workflow`} className="block text-sm font-medium text-ink">Approval Workflow</label>
          {workflows === null ? (
            <p id={`${prefix}-workflow`} className="mt-1.5 text-sm text-ink-muted">
              The approval workflows couldn’t be loaded. Reload the page to choose one.
            </p>
          ) : (
            <select
              id={`${prefix}-workflow`}
              value={workflow}
              aria-invalid={error?.field === 'workflow' || undefined}
              onChange={(event) => { setWorkflow(event.target.value); setError(null) }}
              className={`h-11 ${fieldClasses} ${border(error?.field === 'workflow')}`}
            >
              <option value="">Choose a workflow</option>
              {choices.map((item) => (
                <option key={item.workflowId} value={item.code}>
                  {item.name} ({item.code}){item.active ? '' : ' — inactive'}
                </option>
              ))}
            </select>
          )}
        </div>
      )}
      <div className="sm:col-span-2">
        <CheckboxField
          id={`${prefix}-active`}
          label="Active"
          checked={active}
          describedBy={`${prefix}-active-hint`}
          onChange={setActive}
        />
        <p id={`${prefix}-active-hint`} className="text-xs text-ink-muted">An inactive service takes no new requests. Its requests stay.</p>
      </div>
      <div role="alert" className="sm:col-span-2 empty:hidden">
        {error && <p className="rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm text-danger">{error.text}</p>}
      </div>
      <div className="flex flex-col gap-2 sm:col-span-2 sm:flex-row">
        <Button type="submit" aria-disabled={busy || undefined}>
          {busy ? 'Saving…' : service ? 'Save service' : 'Add service'}
        </Button>
        <ActionButton onClick={onClose}>Cancel</ActionButton>
      </div>
    </form>
  )
}

/** Every campus service, active or not, by code, with Add service and Edit. There is no Delete. */
export function ServiceCatalogSection() {
  const services = useApiRequest(getAdminCampusServices)
  const workflows = useApiRequest(getApprovalWorkflows)
  const [editing, setEditing] = useState<number | 'new' | null>(null)
  const [saved, setSaved] = useState<string | null>(null)
  const workflowList = workflows.status === 'loaded' ? workflows.data : workflows.status === 'loading' ? [] : null

  function done(message: string) {
    setEditing(null)
    setSaved(message)
    services.refresh()
  }

  let content
  if (services.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading campus information…" /></StatePanel>
  } else if (services.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to the service catalog." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (services.status !== 'loaded') {
    content = (
      <StatePanel>
        <ErrorState title="Couldn’t load campus services." message="Check your connection and try again." onRetry={services.retry} />
      </StatePanel>
    )
  } else if (services.data.length === 0) {
    content = <StatePanel><EmptyState title="No campus services yet." description="Add the first service people can request." /></StatePanel>
  } else {
    content = (
      <ul aria-label="Service catalog" className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
        {services.data.map((service) => (
          <li key={service.serviceId} className="px-4 py-3 sm:px-5">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono text-sm text-ink">{service.code}</span>
                  <span className={plainTagClasses(!service.active)}>{service.active ? 'Active' : 'Inactive'}</span>
                </div>
                <p className="mt-0.5 text-sm font-medium break-words text-ink">{service.name}</p>
                <p className="text-xs break-words text-ink-muted">
                  Available to {audienceLabel(service.availableToStudent, service.availableToFaculty)} ·{' '}
                  {service.approvalRequired ? `Approval required (${service.approvalWorkflowCode})` : 'No approval'}
                </p>
              </div>
              {editing !== service.serviceId && (
                <ActionButton className="shrink-0 self-start" onClick={() => { setEditing(service.serviceId); setSaved(null) }}>
                  Edit<span className="sr-only"> {service.code}</span>
                </ActionButton>
              )}
            </div>
            {editing === service.serviceId && (
              <ServiceForm service={service} workflows={workflowList} onSaved={done} onClose={() => setEditing(null)} />
            )}
          </li>
        ))}
      </ul>
    )
  }

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-ink">Service Catalog</h2>
        {editing !== 'new' && services.status === 'loaded' && (
          <ActionButton onClick={() => { setEditing('new'); setSaved(null) }}>Add service</ActionButton>
        )}
      </div>
      <p role="status" className="mt-2 text-sm text-success empty:hidden">{saved ?? ''}</p>
      {editing === 'new' && <ServiceForm service={null} workflows={workflowList} onSaved={done} onClose={() => setEditing(null)} />}
      <div className="mt-3">{content}</div>
    </>
  )
}
