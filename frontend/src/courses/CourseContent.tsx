import type { ApiRequestState } from '../api/useApiRequest'
import { EmptyState } from '../components/states/EmptyState'
import { StatePanel } from '../components/states/StatePanel'
import { Link } from 'react-router'
import type { LmsCourseContent, LmsCourseModule } from '../types/lms'
import { Badge } from '../ui'
import { textLinkClasses } from '../ui/styles'
import { SectionState } from './SectionState'

type CourseContentProps = {
  lmsCourseId: number
  content: ApiRequestState<LmsCourseContent> & { retry: () => void }
}

const HEADING_ID = 'course-content-heading'

type PublishedModuleProps = {
  lmsCourseId: number
  module: LmsCourseModule
  number: number
}

function fileCount(count: number): string {
  return count === 1 ? '1 file' : `${count} files`
}

function PublishedModule({ lmsCourseId, module, number }: PublishedModuleProps) {
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
        {module.locked ? (
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-ink-muted">
            <Badge tone="neutral">Locked</Badge>
            Complete “{module.waitingFor}” first.
          </p>
        ) : (
          module.completed &&
          module.topics.some((topic) => topic.required) && (
            <p className="mt-1">
              <Badge tone="success">Module completed</Badge>
            </p>
          )
        )}
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
            <li key={topic.id} className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1 px-4 py-3 sm:px-5">
              <h4 className="text-sm break-words">
                {module.locked ? (
                  <span className="text-ink-muted">{topic.title}</span>
                ) : (
                  <Link to={`/courses/${lmsCourseId}/pages/${topic.id}`} className={textLinkClasses}>
                    {topic.title}
                  </Link>
                )}
              </h4>
              <span className="flex flex-wrap items-center gap-2 text-xs text-ink-muted">
                {topic.materials.length > 0 && fileCount(topic.materials.length)}
                {topic.required && !topic.completed && <Badge tone="info">Required</Badge>}
                {topic.completed && <Badge tone="success">Completed</Badge>}
              </span>
            </li>
          ))}
        </ol>
      ) : (
        <p className="border-t border-line px-4 py-3 text-sm text-ink-muted sm:px-5">
          No pages published in this module yet.
        </p>
      )}
    </section>
  )
}

/**
 * The published modules of the course, each listing its published pages as links to the page itself
 * (/courses/:lmsCourseId/pages/:pageId), where the text and study materials are. Everything the API sends is already
 * published, so no status is shown. A failure here does not hide the course details above it.
 */
export function CourseContent({ lmsCourseId, content }: CourseContentProps) {
  return (
    <section aria-labelledby={HEADING_ID} className="mt-6">
      <h2 id={HEADING_ID} className="text-base font-semibold text-ink">
        Course content
      </h2>

      <div className="mt-3">
        {content.status !== 'loaded' ? (
          <StatePanel>
            <SectionState request={content} what="course content" />
          </StatePanel>
        ) : content.data.modules.length === 0 ? (
          <StatePanel>
            <EmptyState title="No course content published yet." description="Modules appear here once your teachers publish them." />
          </StatePanel>
        ) : (
          <ol className="space-y-3">
            {content.data.modules.map((module, index) => (
              <li key={module.id}>
                <PublishedModule lmsCourseId={lmsCourseId} module={module} number={index + 1} />
              </li>
            ))}
          </ol>
        )}
      </div>
    </section>
  )
}
