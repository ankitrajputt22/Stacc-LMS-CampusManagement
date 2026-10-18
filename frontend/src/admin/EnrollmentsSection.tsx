import { type FormEvent, useEffect, useId, useMemo, useRef, useState } from 'react'
import { searchEnrollmentStudents } from '../api/adminEnrollmentApi'
import { useApiRequest } from '../api/useApiRequest'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { border, fieldClasses } from '../teaching/formStyles'
import { ENROLLMENT_DENIED } from './enrollmentText'
import { MasterStatus } from './MasterList'
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

  return (
    <section aria-labelledby="enrollments-heading" className="mt-6">
      <h2 id="enrollments-heading" className="text-lg font-semibold text-ink">
        Enrollments
      </h2>
      <p className="mt-1 text-sm leading-6 text-ink-muted">
        Find a student, then enroll them in a semester of their program and in that semester’s course offerings. Enrollments
        end as completed or withdrawn and are never deleted.
      </p>
      {selected === null ? (
        <StudentSearch
          text={text}
          onText={setText}
          query={query}
          onSearch={setQuery}
          onSelect={setSelected}
          focusOnShow={returned}
        />
      ) : (
        <StudentEnrollments
          key={selected}
          studentProfileId={selected}
          onBack={() => {
            setReturned(true)
            setSelected(null)
          }}
        />
      )}
    </section>
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

function StudentSearch({ text, onText, query, onSearch, onSelect, focusOnShow }: StudentSearchProps) {
  const id = useId()
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (focusOnShow) {
      inputRef.current?.focus()
    }
  }, [focusOnShow])

  const [tooLong, setTooLong] = useState(false)
  const search = useMemo(
    () => (query === null ? null : (signal: AbortSignal) => searchEnrollmentStudents(query, signal)),
    [query],
  )
  const request = useApiRequest(search)
  const results = request.status === 'loaded' ? request.data : null

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
    <>
      <form role="search" onSubmit={submit} className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-end lg:max-w-2xl">
        <div className="min-w-0 flex-1">
          <label htmlFor={`${id}-q`} className="block text-sm font-medium text-ink">
            Student login ID
          </label>
          <p id={`${id}-hint`} className="mt-0.5 text-xs text-ink-muted">
            All or part of a college ID. Leave it empty to list the first 50 students.
          </p>
          <input
            ref={inputRef}
            id={`${id}-q`}
            type="search"
            value={text}
            maxLength={100}
            autoComplete="off"
            aria-describedby={`${id}-hint${tooLong ? ` ${id}-error` : ''}`}
            aria-invalid={tooLong || undefined}
            onChange={(event) => onText(event.target.value)}
            className={`h-11 ${fieldClasses} ${border(tooLong)}`}
          />
          {tooLong && (
            <p id={`${id}-error`} className="mt-1.5 text-sm text-danger">
              Use at most 100 characters.
            </p>
          )}
        </div>
        <Button type="submit" className="sm:mb-px">
          Search
        </Button>
      </form>

      {query !== null && (
        <div className="mt-4">
          {request.status === 'loading' && (
            <StatePanel>
              <LoadingState label="Searching students…" />
            </StatePanel>
          )}
          {request.status === 'denied' && (
            <StatePanel>
              <AccessDeniedState title={ENROLLMENT_DENIED} message={SIGN_IN_AGAIN} />
            </StatePanel>
          )}
          {(request.status === 'failed' || request.status === 'notFound') && (
            <StatePanel>
              <ErrorState title="Couldn’t load enrollments." message="Check your connection and try again." onRetry={request.retry} />
            </StatePanel>
          )}
          {results !== null && results.length === 0 && (
            <StatePanel>
              <EmptyState title="No matching students." description="Check the college ID and search again." />
            </StatePanel>
          )}
          {results !== null && results.length > 0 && (
            <>
              <p className="mb-2 text-sm text-ink-muted" role="status">
                {results.length === 1 ? '1 student found' : `${results.length} students found`}
                {results.length === 50 ? '. Showing the first 50; search more precisely to narrow it down.' : '.'}
              </p>
              <ul aria-label="Matching students" className="divide-y divide-line rounded-panel border border-line bg-surface">
                {results.map((student) => (
                  <li key={student.studentProfileId} className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0">
                      <p className="text-sm font-medium break-all text-ink">{student.loginId}</p>
                      <p className="mt-0.5 text-sm break-words text-ink-muted">
                        {student.programCode} · admitted {student.admissionSessionCode}
                      </p>
                      <p className="mt-1">
                        <MasterStatus status={student.studentProfileStatus} />
                      </p>
                    </div>
                    <ActionButton onClick={() => onSelect(student.studentProfileId)}>
                      Manage enrollment<span className="sr-only"> for {student.loginId}</span>
                    </ActionButton>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </>
  )
}
