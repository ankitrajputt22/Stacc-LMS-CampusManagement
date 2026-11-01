import { type FormEvent, useEffect, useRef, useState } from 'react'
import { searchEnrollmentStudents } from '../api/adminEnrollmentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import type { EnrollmentStudent } from '../types/admin'
import { Button, Card, DataTable, TextField } from '../ui'
import { ENROLLMENT_DENIED } from './enrollmentText'
import { MasterStatus } from './MasterParts'
import { SIGN_IN_AGAIN } from './MasterSection'
import { StudentEnrollments } from './StudentEnrollments'

/**
 * Official enrollment, one student at a time: find an existing student by login ID, then manage their semester and
 * course enrollments. The search only selects a student; it never creates or edits accounts or profiles.
 */
export function EnrollmentsSection() {
  const [selected, setSelected] = useState<number | null>(null)
  const [text, setText] = useState('')
  const [query, setQuery] = useState<string | null>(null)
  const [returned, setReturned] = useState(false)

  return selected === null ? (
    <StudentSearch text={text} onText={setText} query={query} onSearch={setQuery} onSelect={setSelected} focusOnShow={returned} />
  ) : (
    <StudentEnrollments
      key={selected}
      studentProfileId={selected}
      onBack={() => {
        setReturned(true)
        setSelected(null)
      }}
    />
  )
}

type StudentSearchProps = {
  text: string
  onText: (text: string) => void
  /** The text last searched for, or null before the first search. */
  query: string | null
  onSearch: (query: string) => void
  onSelect: (studentProfileId: number) => void
  /** After returning from a student, focus goes back to the search box. */
  focusOnShow: boolean
}

const SEARCH_LIMIT = 50

function StudentSearch({ text, onText, query, onSearch, onSelect, focusOnShow }: StudentSearchProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [tooLong, setTooLong] = useState(false)
  const request = useApiQuery(query === null ? null : queryKeys.admin.studentSearch(query), (signal) =>
    searchEnrollmentStudents(query ?? '', signal),
  )
  const results = request.status === 'loaded' ? request.data : null

  useEffect(() => {
    if (focusOnShow) {
      inputRef.current?.focus()
    }
  }, [focusOnShow])

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = text.trim()
    setTooLong(value.length > 100)
    if (value.length <= 100) {
      onSearch(value)
      if (value === query) {
        request.retry()
      }
    }
  }

  return (
    <Card
      title="Enrollments"
      description="Find a student, then enroll them in a semester of their program and in that semester’s course offerings. Enrollments end as completed or withdrawn and are never deleted."
      padding="none"
    >
      <form role="search" onSubmit={submit} className="flex flex-col gap-3 border-b border-line px-4 py-4 sm:flex-row sm:items-start sm:px-5">
        <TextField
          ref={inputRef}
          className="min-w-0 flex-1 lg:max-w-md"
          label="Student login ID"
          hint={`All or part of a college ID. Leave it empty to list the first ${SEARCH_LIMIT} students.`}
          error={tooLong ? 'Use at most 100 characters.' : null}
          type="search"
          value={text}
          maxLength={100}
          autoComplete="off"
          onChange={(event) => onText(event.target.value)}
        />
        <Button type="submit" className="sm:mt-7">
          Search
        </Button>
      </form>

      {query === null && <EmptyState title="Search for a student to see their enrollments." />}
      {query !== null && request.status === 'loading' && <LoadingState label="Searching students…" />}
      {query !== null && request.status === 'denied' && <AccessDeniedState title={ENROLLMENT_DENIED} message={SIGN_IN_AGAIN} />}
      {query !== null && (request.status === 'failed' || request.status === 'notFound') && (
        <ErrorState title="Couldn’t load students." message="Check your connection and try again." onRetry={request.retry} />
      )}
      {results !== null && (
        <>
          {results.length > 0 && (
            <p role="status" className="px-4 pt-3 text-sm text-ink-muted sm:px-5">
              {results.length === 1 ? '1 student found' : `${results.length} students found`}
              {results.length === SEARCH_LIMIT ? `. Showing the first ${SEARCH_LIMIT}; search more precisely to narrow it down.` : '.'}
            </p>
          )}
          <DataTable<EnrollmentStudent>
            caption="Matching students"
            columns={[
              { key: 'student', header: 'Student ID', primary: true, cell: (student) => <span className="break-all">{student.loginId}</span> },
              { key: 'program', header: 'Program', cell: (student) => student.programCode },
              { key: 'admitted', header: 'Admitted', cell: (student) => student.admissionSessionCode },
              { key: 'status', header: 'Profile', cell: (student) => <MasterStatus status={student.studentProfileStatus} /> },
              {
                key: 'actions',
                header: 'Actions',
                align: 'right',
                cell: (student) => (
                  <Button variant="secondary" size="sm" onClick={() => onSelect(student.studentProfileId)}>
                    Manage enrollment<span className="sr-only"> for {student.loginId}</span>
                  </Button>
                ),
              },
            ]}
            rows={results}
            rowKey={(student) => student.studentProfileId}
            empty={<EmptyState title="No matching students." description="Check the college ID and search again." />}
          />
        </>
      )}
    </Card>
  )
}
