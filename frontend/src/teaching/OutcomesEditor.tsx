import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { addOutcome, archiveOutcome, describeOutcome, getFacultyOutcomes } from '../api/lmsOutcomesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import type { LmsOutcome } from '../types/lms'
import { Alert, Badge, Button, Card, TextAreaField } from '../ui'

const MAX_LENGTH = 500

function refusal(status: number | null, apiMessage: string | null): string {
  if (status === 403) {
    return 'You don’t have access to change this course’s outcomes. If your access changed recently, sign out and sign in again.'
  }
  return apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t save. Check your connection and try again.'
}

function failureText(failure: unknown): string | null {
  const status = getHttpStatus(failure)
  return status === 401 ? null : refusal(status, getApiError(failure)?.message ?? null)
}

/** A description form: add a new outcome, or correct one. Checks only the obvious; the backend decides. */
function DescriptionForm({
  label,
  initial,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  label: string
  initial: string
  submitLabel: string
  onSubmit: (description: string) => Promise<void>
  onCancel?: () => void
}) {
  const [text, setText] = useState(initial)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit() {
    if (busy) {
      return
    }
    if (text.trim() === '') {
      setError('Describe the outcome.')
      return
    }
    if (text.trim().length > MAX_LENGTH) {
      setError(`Use at most ${MAX_LENGTH} characters.`)
      return
    }
    setBusy(true)
    setError(null)
    try {
      await onSubmit(text)
    } catch (failure) {
      setError(failureText(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <TextAreaField
        label={label}
        hint="Plain text, such as “Analyse the cost of common data structures.”"
        value={text}
        rows={2}
        maxLength={MAX_LENGTH}
        error={error ?? undefined}
        onChange={(event) => setText(event.target.value)}
      />
      <div className="mt-3 flex flex-col gap-2 sm:flex-row">
        <Button aria-disabled={busy || undefined} onClick={() => void submit()}>
          {busy ? 'Saving…' : submitLabel}
        </Button>
        {onCancel && (
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
    </div>
  )
}

/**
 * The course's learning outcomes as faculty manage them: Add outcome (the server gives the next code, CO1, CO2, …),
 * Edit the description, and Archive, confirmed first. Archived outcomes stay listed and on what they were aligned to,
 * but take no new alignments; nothing is deleted.
 */
export function OutcomesEditor({ lmsCourseId }: { lmsCourseId: number }) {
  const queryClient = useQueryClient()
  const outcomes = useApiQuery(queryKeys.lms.facultyOutcomes(lmsCourseId), (signal) => getFacultyOutcomes(lmsCourseId, signal))
  const [editing, setEditing] = useState<number | null>(null)
  const [archiving, setArchiving] = useState<LmsOutcome | null>(null)
  const [adding, setAdding] = useState(0)
  const [announcement, setAnnouncement] = useState('')

  async function changed(message: string) {
    setAnnouncement(message)
    await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
  }

  return (
    <>
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <Card title="Add an outcome" description="Each outcome gets the next code. Codes are never reused, even after archiving." className="mb-6">
        <DescriptionForm
          key={adding}
          label="New outcome"
          initial=""
          submitLabel="Add outcome"
          onSubmit={async (description) => {
            const added = await addOutcome(lmsCourseId, description)
            setAdding((count) => count + 1)
            await changed(`Added ${added.code}.`)
          }}
        />
      </Card>

      {archiving && (
        <div className="mb-6">
          <StatusConfirmation
            question={`Archive ${archiving.code}?`}
            detail="Assignments aligned to it keep it, but it cannot be chosen again and students no longer see it. Nothing restores it."
            confirmLabel="Archive outcome"
            busyLabel="Archiving…"
            describeRefusal={refusal}
            onCancel={() => setArchiving(null)}
            onConfirm={async () => {
              await archiveOutcome(archiving.id)
              setArchiving(null)
              await changed(`Archived ${archiving.code}.`)
            }}
          />
        </div>
      )}

      <Card title="Course outcomes" padding="none">
        {outcomes.status !== 'loaded' ? (
          <SectionState request={outcomes} what="outcomes" />
        ) : outcomes.data.length === 0 ? (
          <EmptyState title="No outcomes yet." description="Add the course’s learning outcomes, then align assignments with them." />
        ) : (
          <ol className="divide-y divide-line">
            {outcomes.data.map((outcome) => (
              <li key={outcome.id} className="px-4 py-3 sm:px-5">
                {editing === outcome.id ? (
                  <DescriptionForm
                    label={`Description of ${outcome.code}`}
                    initial={outcome.description}
                    submitLabel="Save outcome"
                    onCancel={() => setEditing(null)}
                    onSubmit={async (description) => {
                      await describeOutcome(outcome.id, description)
                      setEditing(null)
                      await changed(`Saved ${outcome.code}.`)
                    }}
                  />
                ) : (
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <p className="text-sm break-words text-ink">
                      <span className="font-semibold">{outcome.code}</span> {outcome.description}{' '}
                      {outcome.status === 'ARCHIVED' && <Badge tone="neutral">Archived</Badge>}
                    </p>
                    {outcome.status === 'ACTIVE' && (
                      <div className="flex shrink-0 gap-2">
                        <Button variant="secondary" size="sm" onClick={() => setEditing(outcome.id)}>
                          Edit<span className="sr-only"> {outcome.code}</span>
                        </Button>
                        <Button variant="secondary" size="sm" onClick={() => setArchiving(outcome)}>
                          Archive<span className="sr-only"> {outcome.code}</span>
                        </Button>
                      </div>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ol>
        )}
      </Card>
      {outcomes.status === 'loaded' && outcomes.data.some((outcome) => outcome.status === 'ACTIVE') && (
        <Alert tone="info" className="mt-6">
          Align each assignment with these outcomes and a Bloom level under Assignments.
        </Alert>
      )}
    </>
  )
}
