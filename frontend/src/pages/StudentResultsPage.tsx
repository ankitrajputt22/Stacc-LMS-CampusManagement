import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { creditsText } from '../courses/credits'
import { formatDateTime } from '../courses/dates'
import { formatMarks } from '../courses/marks'
import { ASK_THE_COLLEGE, RESULTS_DENIED, SIGN_IN_AGAIN } from '../results/resultText'
import { useMyResults } from '../results/useResults'
import type { StudentResult } from '../types/results'

type Group = { key: string; heading: string; programCode: string; results: StudentResult[] }

// The backend already orders the results; this only puts consecutive ones of the same semester under one heading.
function grouped(results: StudentResult[]): Group[] {
  const groups: Group[] = []
  for (const result of results) {
    const key = `${result.academicSessionCode}|${result.programCode}|${result.semesterNumber}`
    const last = groups.at(-1)
    if (last?.key === key) {
      last.results.push(result)
    } else {
      groups.push({
        key,
        heading: `${result.academicSessionCode} · Semester ${result.semesterNumber}`,
        programCode: result.programCode,
        results: [result],
      })
    }
  }
  return groups
}

function ResultCard({ result }: { result: StudentResult }) {
  return (
    <article className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
      <h3 className="text-base leading-6 font-semibold break-words text-ink">{result.courseName}</h3>
      <p className="mt-0.5 text-sm text-ink-muted">
        {result.courseCode}
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        {creditsText(result.credits)}
      </p>
      {/* Exactly what was recorded and published. There is no percentage, grade point, rank, or pass or fail. */}
      <dl className="mt-3 space-y-2">
        <div>
          <dt className="text-xs text-ink-muted">Marks</dt>
          <dd className="text-base font-semibold text-ink">
            {formatMarks(result.marksObtained)} / {formatMarks(result.maxMarks)}
          </dd>
        </div>
        {result.gradeCode && (
          <div>
            <dt className="text-xs text-ink-muted">Grade</dt>
            <dd className="text-base font-semibold break-words text-ink">{result.gradeCode}</dd>
          </div>
        )}
        {result.remarks && (
          <div>
            <dt className="text-xs text-ink-muted">Remarks</dt>
            {/* Plain text: React shows it as text, never as HTML. Line breaks are kept. */}
            <dd className="text-sm leading-6 break-words whitespace-pre-line text-ink">{result.remarks}</dd>
          </div>
        )}
      </dl>
      <p className="mt-3 text-xs text-ink-muted">
        Published <time dateTime={result.publishedAt}>{formatDateTime(result.publishedAt)}</time>
      </p>
    </article>
  )
}

function MyResults() {
  const results = useMyResults()

  switch (results.status) {
    case 'loading':
      return (
        <StatePanel>
          <LoadingState label="Loading results…" />
        </StatePanel>
      )
    case 'denied':
      return (
        <StatePanel>
          <AccessDeniedState title={RESULTS_DENIED} message={SIGN_IN_AGAIN} />
        </StatePanel>
      )
    case 'failed':
    case 'notFound':
      return (
        <StatePanel>
          <ErrorState title="Couldn’t load results." message="Check your connection and try again." onRetry={results.retry} />
        </StatePanel>
      )
    case 'loaded':
      if (results.data.results.length === 0) {
        return (
          <StatePanel>
            <EmptyState
              title="No published results available yet."
              description="Your course results appear here once your teachers publish them."
            />
          </StatePanel>
        )
      }
      return (
        <div className="space-y-8">
          {grouped(results.data.results).map((group) => (
            <section key={group.key} aria-labelledby={`results-${group.key}`}>
              <h2 id={`results-${group.key}`} className="text-base font-semibold text-ink">
                {group.heading}
              </h2>
              <p className="text-sm text-ink-muted">
                <span className="sr-only">Program </span>
                {group.programCode}
              </p>
              <ul aria-label={`Results of ${group.heading}`} className="mt-3 grid gap-3 lg:grid-cols-2">
                {group.results.map((result) => (
                  <li key={`${group.key}|${result.courseCode}`}>
                    <ResultCard result={result} />
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      )
  }
}

/**
 * The signed-in student's own published course results, at /academic/results, the latest academic session first.
 * The backend finds the student from the access token and never shows drafts. Accounts without the student role see
 * an explanation instead, and no request is made.
 */
export function StudentResultsPage() {
  const { account } = useAuth()
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])

  return (
    <>
      <title>Results · Academic · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Results</h1>
      {isStudent && (
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
          Your official course results, as your teachers published them.
        </p>
      )}
      <div className="mt-6 max-w-4xl">
        {isStudent ? (
          <MyResults />
        ) : (
          <StatePanel>
            <AccessDeniedState title="This page is for student accounts." message={ASK_THE_COLLEGE} />
          </StatePanel>
        )}
      </div>
    </>
  )
}
