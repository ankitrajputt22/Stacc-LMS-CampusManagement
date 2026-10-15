import type { ApiRequestState } from '../api/useApiRequest'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import type { LmsCourseContent, LmsCourseModule } from '../types/lms'

type CourseContentProps = {
  content: ApiRequestState<LmsCourseContent> & { retry: () => void }
}

const HEADING_ID = 'course-content-heading'

function PublishedModule({ module, number }: { module: LmsCourseModule; number: number }) {
  const headingId = `course-module-${module.id}`

  return (
    <section
      aria-labelledby={headingId}
      className="rounded-panel border border-line bg-surface"
    >
      <div className="px-4 py-3 sm:px-5">
        <p className="text-xs font-semibold tracking-[0.04em] text-ink-muted">
          Module {number}
        </p>
        <h3 id={headingId} className="mt-0.5 text-base font-semibold break-words text-ink">
          {module.title}
        </h3>
        {/* Plain text: React shows it as text, never as HTML. Line breaks are kept. */}
        {module.description && (
          <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink-muted">
            {module.description}
          </p>
        )}
      </div>

      {module.topics.length > 0 ? (
        <ol className="divide-y divide-line border-t border-line">
          {module.topics.map((topic) => (
            <li key={topic.id} className="px-4 py-3 sm:px-5">
              <h4 className="text-sm font-semibold break-words text-ink">{topic.title}</h4>
              {topic.content && (
                <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink">
                  {topic.content}
                </p>
              )}
            </li>
          ))}
        </ol>
      ) : (
        <p className="border-t border-line px-4 py-3 text-sm text-ink-muted sm:px-5">
          No topics published in this module yet.
        </p>
      )}
    </section>
  )
}

/**
 * The published modules and topics of the course. Everything the API sends is already published,
 * so no status is shown. A failure here does not hide the course details above it.
 */
export function CourseContent({ content }: CourseContentProps) {
  return (
    <section aria-labelledby={HEADING_ID} className="mt-8 max-w-2xl">
      <h2 id={HEADING_ID} className="text-base font-semibold text-ink">
        Course content
      </h2>

      <div className="mt-3">
        {content.status === 'loading' && (
          <StatePanel>
            <LoadingState label="Loading course content…" />
          </StatePanel>
        )}

        {content.status === 'failed' && (
          <StatePanel>
            <ErrorState
              title="Couldn’t load the course content"
              message="Check your connection and try again."
              onRetry={content.retry}
            />
          </StatePanel>
        )}

        {/* The course itself loaded, so a refusal here means access ended in the meantime. */}
        {(content.status === 'notFound' || content.status === 'denied') && (
          <p className="text-sm leading-6 text-ink-muted">
            The course content is not available to your account.
          </p>
        )}

        {content.status === 'loaded' && content.data.modules.length === 0 && (
          <p className="rounded-panel border border-line bg-surface px-4 py-3 text-sm text-ink-muted sm:px-5">
            No course content published yet.
          </p>
        )}

        {content.status === 'loaded' && content.data.modules.length > 0 && (
          <ol className="space-y-3">
            {content.data.modules.map((module, index) => (
              <li key={module.id}>
                <PublishedModule module={module} number={index + 1} />
              </li>
            ))}
          </ol>
        )}
      </div>
    </section>
  )
}
