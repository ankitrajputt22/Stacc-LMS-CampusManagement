import { type FormEvent, useState } from 'react'
import { getAcademicSessions, getCourses, getPrograms, getSemesters } from '../api/adminAcademicApi'
import { searchCourseEnrollments } from '../api/adminEnrollmentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { formatDateTime } from '../courses/dates'
import type { EnrollmentMonitorFilters, EnrollmentMonitorRow, EnrollmentStatus } from '../types/admin'
import { Badge, Button, Card, DataTable, Pagination, SelectField, TextField } from '../ui'
import { ENROLLMENT_DENIED, semesterLabel } from './enrollmentText'
import { SIGN_IN_AGAIN } from './MasterSection'
import { statusTone, statusWord } from './masterStatus'

const PAGE_SIZE = 20
const STATUSES: EnrollmentStatus[] = ['ENROLLED', 'COMPLETED', 'WITHDRAWN', 'DROPPED']

const id = (value: string) => (value === '' ? undefined : Number(value))

/**
 * Every official course enrollment, searched, filtered, and paged on the server: by session, program, semester,
 * course, status, and a student ID, roll number, name, or course code. It only reads; enrollments are changed per
 * student under Enrollments.
 */
export function EnrollmentMonitorSection() {
  const [sessionId, setSessionId] = useState('')
  const [programId, setProgramId] = useState('')
  const [semesterId, setSemesterId] = useState('')
  const [courseId, setCourseId] = useState('')
  const [status, setStatus] = useState('')
  const [text, setText] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)

  const sessions = useApiQuery(queryKeys.admin.sessions(), getAcademicSessions)
  const programs = useApiQuery(queryKeys.admin.programs(), getPrograms)
  const semesters = useApiQuery(queryKeys.admin.semesters(), getSemesters)
  const courses = useApiQuery(queryKeys.admin.courses(), getCourses)
  const semesterChoices = (semesters.status === 'loaded' ? semesters.data : []).filter(
    (semester) =>
      (sessionId === '' || String(semester.academicSessionId) === sessionId) &&
      (programId === '' || String(semester.programId) === programId),
  )

  const filters: EnrollmentMonitorFilters = {
    academicSessionId: id(sessionId),
    programId: id(programId),
    semesterId: id(semesterId),
    courseId: id(courseId),
    status: status === '' ? undefined : (status as EnrollmentStatus),
    q: query === '' ? undefined : query,
    page: page - 1,
    size: PAGE_SIZE,
  }
  const request = useApiQuery(queryKeys.admin.enrollmentMonitor(filters), (signal) =>
    searchCourseEnrollments(filters, signal),
  )

  function choose(set: (value: string) => void, also?: () => void) {
    return (value: string) => {
      set(value)
      also?.()
      setPage(1)
    }
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setQuery(text.trim())
    setPage(1)
  }

  const columns = [
    {
      key: 'student',
      header: 'Student',
      primary: true,
      cell: (row: EnrollmentMonitorRow) => (
        <>
          {row.name ?? row.loginId}
          <span className="block text-sm font-normal text-ink-muted">
            {row.loginId}
            {row.rollNumber ? ` · ${row.rollNumber}` : ''}
          </span>
        </>
      ),
    },
    {
      key: 'course',
      header: 'Course',
      cell: (row: EnrollmentMonitorRow) => (
        <>
          <span className="font-medium">{row.courseCode}</span> {row.courseName}
        </>
      ),
    },
    {
      key: 'semester',
      header: 'Semester',
      cell: (row: EnrollmentMonitorRow) => `${row.programCode} · Sem ${row.semesterNumber} · ${row.academicSessionCode}`,
    },
    { key: 'section', header: 'Section', cell: (row: EnrollmentMonitorRow) => row.sectionCode ?? '—' },
    {
      key: 'status',
      header: 'Status',
      cell: (row: EnrollmentMonitorRow) => <Badge tone={statusTone(row.status)}>{statusWord(row.status)}</Badge>,
    },
    {
      key: 'registered',
      header: 'Registered',
      cell: (row: EnrollmentMonitorRow) => (
        <>
          {formatDateTime(row.registeredAt)}
          {row.droppedAt && (
            <span className="block text-sm text-ink-muted">Dropped {formatDateTime(row.droppedAt)}</span>
          )}
        </>
      ),
    },
  ]

  let body
  switch (request.status) {
    case 'loading':
      body = <LoadingState label="Loading course enrollments…" />
      break
    case 'denied':
      body = <AccessDeniedState title={ENROLLMENT_DENIED} message={SIGN_IN_AGAIN} />
      break
    case 'failed':
    case 'notFound':
      body = (
        <ErrorState
          title="Couldn’t load course enrollments."
          message="Check the filters and your connection, then try again."
          onRetry={request.retry}
        />
      )
      break
    case 'loaded':
      body = (
        <>
          <DataTable
            caption="Course enrollments"
            columns={columns}
            rows={request.data.items}
            rowKey={(row) => row.courseEnrollmentId}
            empty={<EmptyState title="No course enrollments match." description="Change the filters or the search." />}
          />
          <Pagination
            page={page}
            pageSize={PAGE_SIZE}
            total={request.data.totalElements}
            onPage={setPage}
            noun="course enrollments"
          />
        </>
      )
      break
  }

  return (
    <Card
      title="Course enrollments"
      description="Every student’s official course enrollments, whatever their status. Change an enrollment from the student’s page under Enrollments."
      padding="none"
    >
      <div className="grid gap-3 border-b border-line px-4 py-4 sm:grid-cols-2 sm:px-5 lg:grid-cols-3">
        <SelectField
          label="Academic session"
          value={sessionId}
          onChange={(event) => choose(setSessionId, () => setSemesterId(''))(event.target.value)}
        >
          <option value="">All sessions</option>
          {(sessions.status === 'loaded' ? sessions.data : []).map((session) => (
            <option key={session.id} value={String(session.id)}>
              {session.code}
            </option>
          ))}
        </SelectField>
        <SelectField
          label="Program"
          value={programId}
          onChange={(event) => choose(setProgramId, () => setSemesterId(''))(event.target.value)}
        >
          <option value="">All programs</option>
          {(programs.status === 'loaded' ? programs.data : []).map((program) => (
            <option key={program.id} value={String(program.id)}>
              {program.code}
            </option>
          ))}
        </SelectField>
        <SelectField label="Semester" value={semesterId} onChange={(event) => choose(setSemesterId)(event.target.value)}>
          <option value="">All semesters</option>
          {semesterChoices.map((semester) => (
            <option key={semester.id} value={String(semester.id)}>
              {semesterLabel(semester)}
            </option>
          ))}
        </SelectField>
        <SelectField label="Course" value={courseId} onChange={(event) => choose(setCourseId)(event.target.value)}>
          <option value="">All courses</option>
          {(courses.status === 'loaded' ? courses.data : []).map((course) => (
            <option key={course.id} value={String(course.id)}>
              {course.code} — {course.name}
            </option>
          ))}
        </SelectField>
        <SelectField label="Status" value={status} onChange={(event) => choose(setStatus)(event.target.value)}>
          <option value="">Any status</option>
          {STATUSES.map((value) => (
            <option key={value} value={value}>
              {statusWord(value)}
            </option>
          ))}
        </SelectField>
        <form role="search" onSubmit={search} className="flex items-end gap-2">
          <TextField
            className="min-w-0 flex-1"
            label="Search course enrollments"
            type="search"
            value={text}
            maxLength={100}
            autoComplete="off"
            onChange={(event) => setText(event.target.value)}
          />
          <Button type="submit" variant="secondary">
            Find
          </Button>
        </form>
      </div>
      <div aria-live="polite">{body}</div>
    </Card>
  )
}
