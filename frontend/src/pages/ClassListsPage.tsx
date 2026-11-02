import { Link, useParams } from 'react-router'
import { getClassList, getTaughtOfferings } from '../api/classListApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { statusTone, statusWord } from '../admin/masterStatus'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { creditsText } from '../courses/credits'
import { formatDateTime } from '../courses/dates'
import type { ClassStudent, TaughtOffering } from '../types/classList'
import { Badge, Card, DataTable, PageHeader } from '../ui'

const DENIED = 'You don’t have access to class lists.'
const SIGN_IN_AGAIN = 'If your access changed recently, sign out and sign in again.'
const FACULTY_ONLY = 'This page is for faculty accounts.'

function offeringFacts(offering: TaughtOffering): string {
  return [
    `${offering.programCode} · Sem ${offering.semesterNumber} · ${offering.academicSessionCode}`,
    offering.sectionCode ? `Section ${offering.sectionCode}` : 'Whole semester',
    creditsText(offering.credits),
    offering.capacity === null
      ? `${offering.enrolledCount} enrolled`
      : `${offering.enrolledCount} of ${offering.capacity} places taken`,
  ].join(' · ')
}

function useIsFaculty() {
  const { account } = useAuth()
  return hasAnyRole(account?.roles ?? [], ['FACULTY'])
}

function TaughtOfferings() {
  const offerings = useApiQuery(queryKeys.classLists.mine(), getTaughtOfferings)
  switch (offerings.status) {
    case 'loading':
      return (
        <StatePanel>
          <LoadingState label="Loading class lists…" />
        </StatePanel>
      )
    case 'denied':
      return (
        <StatePanel>
          <AccessDeniedState title={DENIED} message={SIGN_IN_AGAIN} />
        </StatePanel>
      )
    case 'failed':
    case 'notFound':
      return (
        <StatePanel>
          <ErrorState title="Couldn’t load class lists." message="Check your connection and try again." onRetry={offerings.retry} />
        </StatePanel>
      )
    case 'loaded':
      if (offerings.data.length === 0) {
        return (
          <StatePanel>
            <EmptyState title="No teaching courses available." description="Course offerings you are assigned to teach will appear here." />
          </StatePanel>
        )
      }
      return (
        <ul aria-label="Your teaching course offerings" className="grid gap-3 lg:grid-cols-2">
          {offerings.data.map((offering) => (
            <li key={offering.courseOfferingId}>
              <article className="h-full rounded-panel border border-line bg-surface px-4 py-3.5 sm:px-5 sm:py-4">
                <p className="text-xs font-semibold tracking-[0.04em] text-ink-muted">{offering.courseCode}</p>
                <h2 className="mt-0.5 text-base leading-6 font-semibold break-words text-ink">{offering.courseName}</h2>
                <p className="mt-1 text-sm text-ink-muted">{offeringFacts(offering)}</p>
                <Link
                  to={`/class-lists/${offering.courseOfferingId}`}
                  className="mt-2 inline-flex text-sm font-medium text-stacc-primary underline-offset-4 hover:underline"
                >
                  View students<span className="sr-only"> of {offering.courseCode}</span>
                </Link>
              </article>
            </li>
          ))}
        </ul>
      )
  }
}

/** Class lists at /class-lists: the offerings the faculty member teaches now, each with its registered students. */
export function ClassListsPage() {
  const isFaculty = useIsFaculty()
  return (
    <div className="max-w-6xl">
      <title>Class Lists · Stacc</title>
      <PageHeader
        eyebrow="ERP · Teaching"
        title="Class Lists"
        description={isFaculty ? 'The students registered in the course offerings you teach.' : undefined}
      />
      {isFaculty ? (
        <TaughtOfferings />
      ) : (
        <StatePanel>
          <AccessDeniedState title={FACULTY_ONLY} message="If you think you should, contact the college administration." />
        </StatePanel>
      )}
    </div>
  )
}

const columns = [
  {
    key: 'student',
    header: 'Student',
    primary: true,
    cell: (row: ClassStudent) => (
      <>
        {row.name ?? row.loginId}
        <span className="block text-sm font-normal text-ink-muted">
          {row.loginId}
          {row.rollNumber ? ` · ${row.rollNumber}` : ''}
        </span>
      </>
    ),
  },
  { key: 'program', header: 'Program', cell: (row: ClassStudent) => `${row.programCode} · Sem ${row.semesterNumber}` },
  { key: 'section', header: 'Section', cell: (row: ClassStudent) => row.sectionCode ?? '—' },
  {
    key: 'status',
    header: 'Status',
    cell: (row: ClassStudent) => <Badge tone={statusTone(row.status)}>{statusWord(row.status)}</Badge>,
  },
  { key: 'registered', header: 'Registered', cell: (row: ClassStudent) => formatDateTime(row.registeredAt) },
]

function OfferingStudents({ courseOfferingId }: { courseOfferingId: number }) {
  const list = useApiQuery(queryKeys.classLists.offering(courseOfferingId), (signal) => getClassList(courseOfferingId, signal))
  switch (list.status) {
    case 'loading':
      return (
        <StatePanel>
          <LoadingState label="Loading the class list…" />
        </StatePanel>
      )
    case 'denied':
      return (
        <StatePanel>
          <AccessDeniedState title={DENIED} message={SIGN_IN_AGAIN} />
        </StatePanel>
      )
    case 'notFound':
      return (
        <StatePanel>
          <EmptyState title="Course offering unavailable." description="It does not exist, or you do not teach it." />
        </StatePanel>
      )
    case 'failed':
      return (
        <StatePanel>
          <ErrorState title="Couldn’t load the class list." message="Check your connection and try again." onRetry={list.retry} />
        </StatePanel>
      )
    case 'loaded':
      return (
        <Card
          title={`${list.data.offering.courseCode} ${list.data.offering.courseName}`}
          description={offeringFacts(list.data.offering)}
          padding="none"
        >
          <DataTable
            caption={`Students of ${list.data.offering.courseCode}`}
            columns={columns}
            rows={list.data.students}
            rowKey={(row) => row.courseEnrollmentId}
            empty={<EmptyState title="No students registered yet." />}
          />
        </Card>
      )
  }
}

/** One class list at /class-lists/:courseOfferingId. Read-only: faculty never change enrollments. */
export function ClassListPage() {
  const isFaculty = useIsFaculty()
  const { courseOfferingId } = useParams()
  const offeringId = Number(courseOfferingId)
  return (
    <div className="max-w-6xl">
      <title>Class List · Stacc</title>
      <PageHeader eyebrow="ERP · Teaching" title="Class List" />
      <p className="mb-4 text-sm">
        <Link to="/class-lists" className="font-medium text-stacc-primary underline-offset-4 hover:underline">
          All class lists
        </Link>
      </p>
      {!isFaculty ? (
        <StatePanel>
          <AccessDeniedState title={FACULTY_ONLY} message="If you think you should, contact the college administration." />
        </StatePanel>
      ) : Number.isInteger(offeringId) && offeringId > 0 ? (
        <OfferingStudents courseOfferingId={offeringId} />
      ) : (
        <StatePanel>
          <EmptyState title="Course offering unavailable." description="It does not exist, or you do not teach it." />
        </StatePanel>
      )}
    </div>
  )
}
