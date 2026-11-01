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
import { Card, PageHeader } from '../ui'

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

function ResultItem({ result }: { result: StudentResult }) {
  return (
    <article className="grid gap-3 px-4 py-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:px-5">
      <div className="min-w-0">
        <h3 className="text-base leading-6 font-semibold break-words text-ink">{result.courseName}</h3>
        <p className="mt-0.5 text-sm text-ink-muted">
          {result.courseCode}
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, </span>
          {creditsText(result.credits)}
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, </span>
          Published <time dateTime={result.publishedAt}>{formatDateTime(result.publishedAt)}</time>
        </p>
        {result.remarks && (
          <div className="mt-2">
            <p className="text-sm text-ink-muted">Remarks</p>
            {/* Plain text: React shows it as text, never as HTML. Line breaks are kept. */}
            <p className="text-sm leading-6 break-words whitespace-pre-line text-ink">{result.remarks}</p>
          </div>
        )}
      </div>
      {/* Exactly what was recorded and published. There is no percentage, grade point, rank, or pass or fail. */}
      <dl className="flex gap-3 sm:justify-end">
        <div className="min-w-24 rounded-control border border-line bg-surface-muted px-3 py-2">
          <dt className="text-sm text-ink-muted">Marks</dt>
          <dd className="text-lg font-semibold text-ink">
            {formatMarks(result.marksObtained)} / {formatMarks(result.maxMarks)}
          </dd>
        </div>
        {result.gradeCode && (
          <div className="min-w-20 rounded-control border border-line bg-surface-muted px-3 py-2">
            <dt className="text-sm text-ink-muted">Grade</dt>
            <dd className="text-lg font-semibold break-words text-ink">{result.gradeCode}</dd>
          </div>
        )}
      </dl>
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
        <div className="space-y-4">
          {grouped(results.data.results).map((group) => (
            <Card
              key={group.key}
              title={group.heading}
              description={
                <>
                  <span className="sr-only">Program </span>
                  {group.programCode}
                </>
              }
              padding="none"
            >
              <ul aria-label={`Results of ${group.heading}`} className="divide-y divide-line">
                {group.results.map((result) => (
                  <li key={`${group.key}|${result.courseCode}`}>
                    <ResultItem result={result} />
                  </li>
                ))}
              </ul>
            </Card>
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
    <div className="max-w-4xl">
      <title>Results · Academic · Stacc</title>
      <PageHeader
        eyebrow="ERP · Academics"
        title="Results"
        description={isStudent ? 'Your official course results, as your teachers published them.' : undefined}
      />
      <div>
        {isStudent ? (
          <MyResults />
        ) : (
          <StatePanel>
            <AccessDeniedState title="This page is for student accounts." message={ASK_THE_COLLEGE} />
          </StatePanel>
        )}
      </div>
    </div>
  )
}
