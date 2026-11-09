import { useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { useParams } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import { getFacultySyllabus, publishSyllabus, saveSyllabusDraft } from '../api/lmsSyllabusApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId } from '../courses/courseRoute'
import { formatDateTime } from '../courses/dates'
import { SYLLABUS_TEMPLATE } from '../courses/syllabusTemplate'
import { FormattedTextField } from '../formatting/FormattedTextField'
import type { LmsFacultySyllabus } from '../types/lms'
import { Alert, Button, Card, PageHeader } from '../ui'

const TEACHING = { to: '/teaching', label: 'Teaching' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'
const MAX_LENGTH = 30_000

function refusal(status: number | null, apiMessage: string | null): string {
  if (status === 403) {
    return 'You don’t have access to change this syllabus. If your access changed recently, sign out and sign in again.'
  }
  return apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t save. Check your connection and try again.'
}

function Shell({ lmsCourseId, children }: { lmsCourseId: number | null; children: ReactNode }) {
  return (
    <div className="max-w-4xl">
      <title>Syllabus · Teaching · Stacc</title>
      <PageHeader
        title="Syllabus"
        breadcrumb={
          <Breadcrumb
            parent={lmsCourseId === null ? TEACHING : [TEACHING, { to: `/teaching/${lmsCourseId}`, label: 'Course' }]}
            current="Syllabus"
          />
        }
        description="Students read the published version. Save drafts as often as you like; they see changes only after you publish."
      />
      {children}
    </div>
  )
}

/** Where the syllabus stands, in words, from what the API sent. */
function stateText(syllabus: LmsFacultySyllabus): string {
  if (syllabus.lastPublishedAt === null) {
    return syllabus.draftContent === null ? 'No syllabus yet. Start from the sections below.' : 'Not published yet. Students cannot see it.'
  }
  const published = `Published ${formatDateTime(syllabus.lastPublishedAt)}.`
  return syllabus.unpublishedChanges ? `${published} The saved draft has changes students do not see yet.` : `${published} Students see this version.`
}

function Editor({ lmsCourseId, syllabus }: { lmsCourseId: number; syllabus: LmsFacultySyllabus }) {
  const queryClient = useQueryClient()
  const [text, setText] = useState(syllabus.draftContent ?? SYLLABUS_TEMPLATE)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [announcement, setAnnouncement] = useState('')

  async function refreshed(message: string) {
    setAnnouncement(message)
    await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
  }

  function check(): boolean {
    if (text.trim() === '') {
      setError('Enter the syllabus.')
      return false
    }
    if (text.trim().length > MAX_LENGTH) {
      setError(`Use at most ${MAX_LENGTH.toLocaleString('en')} characters.`)
      return false
    }
    setError(null)
    return true
  }

  async function saveDraft() {
    if (busy || !check()) {
      return
    }
    setBusy(true)
    try {
      await saveSyllabusDraft(lmsCourseId, text)
      await refreshed('Syllabus draft saved.')
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(refusal(status, getApiError(failure)?.message ?? null))
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <Alert tone={syllabus.lastPublishedAt !== null && !syllabus.unpublishedChanges ? 'success' : 'info'} className="mb-4">
        {stateText(syllabus)}
      </Alert>
      <Card>
        <FormattedTextField
          id="syllabus-text"
          label="Syllabus"
          value={text}
          onChange={(value) => {
            setText(value)
            setConfirming(false)
          }}
          format="FORMATTED"
          onFormatChange={() => undefined}
          formatChoice={false}
          rows={18}
          maxLength={MAX_LENGTH}
          error={error ?? undefined}
        />
        {confirming ? (
          <div className="mt-4">
            <StatusConfirmation
              question="Save and publish the syllabus?"
              detail="Students of this course will read this text from now on. You can keep editing and publish again; there is no unpublish."
              confirmLabel="Publish syllabus"
              busyLabel="Publishing…"
              describeRefusal={refusal}
              onCancel={() => setConfirming(false)}
              onConfirm={async () => {
                await saveSyllabusDraft(lmsCourseId, text)
                await publishSyllabus(lmsCourseId)
                setConfirming(false)
                await refreshed('Syllabus published.')
              }}
            />
          </div>
        ) : (
          <div className="mt-4 flex flex-col gap-2 sm:flex-row">
            <Button variant="secondary" aria-disabled={busy || undefined} onClick={() => void saveDraft()}>
              {busy ? 'Saving…' : 'Save draft'}
            </Button>
            <Button onClick={() => check() && setConfirming(true)}>Publish</Button>
          </div>
        )}
      </Card>
    </>
  )
}

/**
 * /teaching/:lmsCourseId/syllabus: the course's syllabus as formatted text, starting from the usual sections, with Save
 * draft and Publish (confirmed first, saving the text too). The backend checks that the faculty member teaches the
 * course on every request.
 */
export function TeachingSyllabusPage() {
  const { account } = useAuth()
  const lmsCourseId = parseCourseId(useParams().lmsCourseId)
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])
  const syllabus = useApiQuery(isFaculty && lmsCourseId !== null ? queryKeys.lms.facultySyllabus(lmsCourseId) : null, (signal) =>
    getFacultySyllabus(lmsCourseId ?? 0, signal),
  )

  if (!isFaculty) {
    return (
      <Shell lmsCourseId={lmsCourseId}>
        <StatePanel>
          <AccessDeniedState title="Syllabi are edited by faculty accounts." message={ASK_THE_COLLEGE} />
        </StatePanel>
      </Shell>
    )
  }
  if (syllabus.status !== 'loaded' || lmsCourseId === null) {
    return (
      <Shell lmsCourseId={lmsCourseId}>
        <StatePanel>
          {syllabus.status === 'loading' && <LoadingState label="Loading syllabus…" />}
          {syllabus.status === 'failed' && (
            <ErrorState title="Couldn’t load the syllabus" message="Check your connection and try again." onRetry={syllabus.retry} />
          )}
          {syllabus.status === 'denied' && <AccessDeniedState title="You don’t have access to this course." message={ASK_THE_COLLEGE} />}
          {syllabus.status === 'notFound' && (
            <EmptyState title="Course unavailable" description="This LMS course could not be found or is not one you teach." />
          )}
        </StatePanel>
      </Shell>
    )
  }
  return (
    <Shell lmsCourseId={lmsCourseId}>
      {/* Keyed by the saved draft, so the editor starts again from it after a save elsewhere. */}
      <Editor key={syllabus.data.draftUpdatedAt ?? 'new'} lmsCourseId={lmsCourseId} syllabus={syllabus.data} />
    </Shell>
  )
}
