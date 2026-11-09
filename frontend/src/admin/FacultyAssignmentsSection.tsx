import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useSearchParams } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import { getAcademicSessions, getSemesters } from '../api/adminAcademicApi'
import {
  assignFaculty,
  getCourseOffering,
  getCourseOfferings,
  getOfferingFaculty,
  searchFaculty,
  updateFacultyAssignment,
} from '../api/adminEnrollmentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import type { AdminCourseOffering, OfferingFaculty, TeachingRole } from '../types/admin'
import { Alert, Badge, Button, Card, DataTable, SelectField, TextField } from '../ui'
import { semesterLabel } from './enrollmentText'
import { MasterStatus } from './MasterParts'
import { SIGN_IN_AGAIN } from './MasterSection'
import { ROLE_OPTIONS, roleWord } from './offeringText'

const DENIED = 'You don’t have access to faculty assignments.'
const ID_PATTERN = /^[1-9]\d{0,17}$/

function refusal(status: number | null, apiMessage: string | null): string {
  if (status === 403) {
    return `${DENIED} If your access changed recently, sign out and sign in again.`
  }
  return apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t save. Check your connection and try again.'
}

function offeringName(offering: AdminCourseOffering): string {
  return `${offering.courseCode}${offering.sectionCode ? ` section ${offering.sectionCode}` : ''} · ${offering.programCode} Sem ${
    offering.semesterNumber
  } · ${offering.academicSessionCode}`
}

/**
 * Who teaches each course offering. An admin picks an offering, sees every assignment with its role, assigns a faculty
 * member as primary or co-faculty, changes the role, and makes an assignment inactive or active again; nothing is
 * deleted. An active assignment is what gives the faculty member attendance, results, and LMS access for the offering.
 */
export function FacultyAssignmentsSection() {
  const [searchParams, setSearchParams] = useSearchParams()
  const offeringParam = searchParams.get('offering')
  const offeringId = offeringParam && ID_PATTERN.test(offeringParam) ? Number(offeringParam) : null
  const [sessionId, setSessionId] = useState('')
  const [semesterId, setSemesterId] = useState('')
  const sessions = useApiQuery(queryKeys.admin.sessions(), getAcademicSessions)
  const semesters = useApiQuery(queryKeys.admin.semesters(), getSemesters)
  const filters = { semesterId: semesterId === '' ? undefined : Number(semesterId) }
  const offerings = useApiQuery(semesterId === '' ? null : queryKeys.admin.offerings(filters), (signal) =>
    getCourseOfferings(filters, signal),
  )
  const sessionList = sessions.status === 'loaded' ? sessions.data : []
  const semesterList = (semesters.status === 'loaded' ? semesters.data : []).filter(
    (semester) => sessionId === '' || String(semester.academicSessionId) === sessionId,
  )

  function chooseOffering(id: string) {
    setSearchParams(id === '' ? {} : { offering: id }, { replace: true })
  }

  return (
    <div className="space-y-4">
      <Card
        title="Faculty assignments"
        description="Choose a course offering to see and change who teaches it. An active assignment gives the faculty member attendance, results, and LMS access for that offering."
      >
        <div className="grid gap-4 sm:grid-cols-3">
          <SelectField
            label="Academic session"
            value={sessionId}
            onChange={(event) => {
              setSessionId(event.target.value)
              setSemesterId('')
            }}
          >
            <option value="">All sessions</option>
            {sessionList.map((session) => (
              <option key={session.id} value={String(session.id)}>
                {session.code}
              </option>
            ))}
          </SelectField>
          <SelectField label="Semester" value={semesterId} onChange={(event) => setSemesterId(event.target.value)}>
            <option value="">Choose a semester</option>
            {semesterList.map((semester) => (
              <option key={semester.id} value={String(semester.id)}>
                {semesterLabel(semester)}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Course offering"
            value={offeringId === null ? '' : String(offeringId)}
            disabled={offerings.status !== 'loaded'}
            hint={semesterId === '' ? 'Choose a semester first.' : undefined}
            onChange={(event) => chooseOffering(event.target.value)}
          >
            <option value="">Choose a course offering</option>
            {offerings.status === 'loaded' &&
              offerings.data.map((offering) => (
                <option key={offering.courseOfferingId} value={String(offering.courseOfferingId)}>
                  {offeringName(offering)}
                </option>
              ))}
          </SelectField>
        </div>
      </Card>

      {offeringId !== null && <OfferingFacultyPanel key={offeringId} courseOfferingId={offeringId} />}
    </div>
  )
}

type Confirming = { assignment: OfferingFaculty; status: 'ACTIVE' | 'INACTIVE' }

function OfferingFacultyPanel({ courseOfferingId }: { courseOfferingId: number }) {
  const queryClient = useQueryClient()
  const offering = useApiQuery(queryKeys.admin.offering(courseOfferingId), (signal) =>
    getCourseOffering(courseOfferingId, signal),
  )
  const faculty = useApiQuery(queryKeys.admin.offeringFaculty(courseOfferingId), (signal) =>
    getOfferingFaculty(courseOfferingId, signal),
  )
  const [announcement, setAnnouncement] = useState('')
  const [confirming, setConfirming] = useState<Confirming | null>(null)
  const [rowError, setRowError] = useState<string | null>(null)
  const closed = offering.status === 'loaded' && offering.data.status === 'CLOSED'

  function changed(message: string) {
    setAnnouncement(message)
    void queryClient.invalidateQueries({ queryKey: queryKeys.admin.academic })
  }

  async function changeRole(assignment: OfferingFaculty, role: TeachingRole) {
    setRowError(null)
    try {
      await updateFacultyAssignment(assignment.teachingAssignmentId, role, assignment.status)
      changed(`${assignment.name ?? assignment.loginId} is now ${roleWord(role).toLowerCase()}.`)
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setRowError(refusal(status, getApiError(failure)?.message ?? null))
      }
    }
  }

  if (offering.status === 'denied' || faculty.status === 'denied') {
    return (
      <Card>
        <AccessDeniedState title={DENIED} message={SIGN_IN_AGAIN} />
      </Card>
    )
  }
  if (offering.status === 'notFound' || faculty.status === 'notFound') {
    return (
      <Card>
        <EmptyState title="Course offering not found." description="Choose an offering from the list." />
      </Card>
    )
  }
  if (offering.status === 'failed' || faculty.status === 'failed') {
    return (
      <Card>
        <ErrorState
          title="Couldn’t load who teaches this offering."
          message="Check your connection and try again."
          onRetry={() => {
            offering.retry()
            faculty.retry()
          }}
        />
      </Card>
    )
  }
  if (offering.status !== 'loaded' || faculty.status !== 'loaded') {
    return (
      <Card>
        <LoadingState label="Loading faculty…" />
      </Card>
    )
  }

  const name = (member: OfferingFaculty) => member.name ?? member.loginId
  return (
    <>
      <p role="status" className="sr-only">
        {announcement}
      </p>
      {confirming && (
        <StatusConfirmation
          question={
            confirming.status === 'INACTIVE'
              ? `Remove ${name(confirming.assignment)} from this offering?`
              : `Assign ${name(confirming.assignment)} again?`
          }
          detail={
            confirming.status === 'INACTIVE'
              ? 'They lose attendance, results, and LMS access for it at once. The assignment is kept as history and can be made active again.'
              : 'They get attendance, results, and LMS access for this offering again.'
          }
          confirmLabel={confirming.status === 'INACTIVE' ? 'Remove from offering' : 'Assign again'}
          onConfirm={async () => {
            const { assignment, status } = confirming
            await updateFacultyAssignment(assignment.teachingAssignmentId, assignment.role, status)
            setConfirming(null)
            changed(`${name(assignment)} ${status === 'INACTIVE' ? 'removed from' : 'assigned again to'} the offering.`)
          }}
          onCancel={() => setConfirming(null)}
          describeRefusal={refusal}
        />
      )}
      <Card
        title={`Who teaches ${offeringName(offering.data)}`}
        description={
          closed
            ? 'This offering is closed, so who teaches it can no longer change.'
            : 'At most one active primary faculty member; any number of co-faculty.'
        }
        padding="none"
      >
        {rowError && (
          <Alert tone="error" className="mx-4 mt-4 sm:mx-5">
            {rowError}
          </Alert>
        )}
        <DataTable<OfferingFaculty>
          caption="Faculty assignments of this offering"
          columns={[
            {
              key: 'faculty',
              header: 'Faculty',
              primary: true,
              cell: (member) => (
                <>
                  {name(member)}
                  {member.name && <span className="block text-sm font-normal text-ink-muted">{member.loginId}</span>}
                </>
              ),
            },
            { key: 'department', header: 'Department', cell: (member) => member.departmentCode },
            {
              key: 'role',
              header: 'Role',
              cell: (member) => <Badge tone={member.role === 'PRIMARY' ? 'brand' : 'neutral'}>{roleWord(member.role)}</Badge>,
            },
            { key: 'status', header: 'Status', cell: (member) => <MasterStatus status={member.status} /> },
            {
              key: 'actions',
              header: 'Actions',
              align: 'right',
              cell: (member) =>
                !closed && (
                  <div className="flex flex-wrap gap-2 md:justify-end">
                    {member.status === 'ACTIVE' && (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => void changeRole(member, member.role === 'PRIMARY' ? 'CO_FACULTY' : 'PRIMARY')}
                      >
                        {member.role === 'PRIMARY' ? 'Make co-faculty' : 'Make primary'}
                        <span className="sr-only"> {name(member)}</span>
                      </Button>
                    )}
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => setConfirming({ assignment: member, status: member.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' })}
                    >
                      {member.status === 'ACTIVE' ? 'Remove' : 'Assign again'}
                      <span className="sr-only"> {name(member)}</span>
                    </Button>
                  </div>
                ),
            },
          ]}
          rows={faculty.data}
          rowKey={(member) => member.teachingAssignmentId}
          empty={<EmptyState title="Unassigned." description="No faculty member has been assigned to this offering yet." />}
        />
      </Card>
      {!closed && (
        <AssignFacultyForm
          courseOfferingId={courseOfferingId}
          hasPrimary={faculty.data.some((member) => member.status === 'ACTIVE' && member.role === 'PRIMARY')}
          onAssigned={changed}
        />
      )}
    </>
  )
}

type AssignFacultyFormProps = {
  courseOfferingId: number
  hasPrimary: boolean
  onAssigned: (announcement: string) => void
}

function AssignFacultyForm({ courseOfferingId, hasPrimary, onAssigned }: AssignFacultyFormProps) {
  const [text, setText] = useState('')
  const [query, setQuery] = useState<string | null>(null)
  const [chosen, setChosen] = useState('')
  const [role, setRole] = useState<TeachingRole>(hasPrimary ? 'CO_FACULTY' : 'PRIMARY')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const results = useApiQuery(query === null ? null : queryKeys.admin.facultySearch(query), (signal) =>
    searchFaculty(query ?? '', signal),
  )
  const found = results.status === 'loaded' ? results.data.filter((member) => member.status === 'ACTIVE') : []

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setChosen('')
    setQuery(text.trim())
  }

  async function assign() {
    if (chosen === '' || saving) {
      setError(chosen === '' ? 'Choose a faculty member.' : null)
      return
    }
    setSaving(true)
    setError(null)
    try {
      const saved = await assignFaculty(courseOfferingId, Number(chosen), role)
      setChosen('')
      onAssigned(`${saved.name ?? saved.loginId} assigned as ${roleWord(saved.role).toLowerCase()}.`)
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(refusal(status, getApiError(failure)?.message ?? null))
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card title="Assign faculty" description="Find a faculty member by employee ID, then choose their role.">
      <form role="search" onSubmit={search} className="flex flex-col gap-3 sm:flex-row sm:items-start">
        <TextField
          className="min-w-0 flex-1 lg:max-w-md"
          label="Employee ID"
          hint="All or part of an employee ID. Leave it empty to list the first 50."
          type="search"
          value={text}
          maxLength={100}
          autoComplete="off"
          onChange={(event) => setText(event.target.value)}
        />
        <Button type="submit" variant="secondary" className="sm:mt-7">
          Find
        </Button>
      </form>
      {query !== null && results.status === 'loading' && <LoadingState label="Searching faculty…" />}
      {query !== null && results.status === 'loaded' && found.length === 0 && (
        <p className="mt-3 text-sm text-ink-muted">No active faculty members match.</p>
      )}
      {found.length > 0 && (
        <div className="mt-4 grid gap-4 sm:grid-cols-3 sm:items-end">
          <SelectField label="Faculty member" value={chosen} onChange={(event) => setChosen(event.target.value)}>
            <option value="">Choose a faculty member</option>
            {found.map((member) => (
              <option key={member.facultyProfileId} value={String(member.facultyProfileId)}>
                {member.name ? `${member.name} (${member.loginId})` : member.loginId} · {member.departmentCode}
              </option>
            ))}
          </SelectField>
          <SelectField label="Role" value={role} onChange={(event) => setRole(event.target.value as TeachingRole)}>
            {ROLE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </SelectField>
          <Button onClick={() => void assign()} aria-disabled={saving || undefined}>
            {saving ? 'Assigning…' : 'Assign faculty'}
          </Button>
        </div>
      )}
      {found.length > 0 && hasPrimary && (
        <p className="mt-2 text-sm text-ink-muted">This offering already has a primary faculty member, so a new one joins as co-faculty.</p>
      )}
      {error && (
        <Alert tone="error" className="mt-3">
          {error}
        </Alert>
      )}
    </Card>
  )
}
