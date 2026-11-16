import { NavIcon } from '../components/navigation/navIcons'
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
  const completedCount = module.topics.filter((topic) => topic.completed).length

  return (
    <section aria-labelledby={headingId} className="rounded-panel border border-line bg-surface shadow-[0_1px_2px_rgba(15,23,42,0.05)]">
      <div className="relative mx-4 flex flex-wrap items-center justify-between gap-3 border-b border-line pt-4 pb-3 sm:mx-5">
        <span aria-hidden="true" className="absolute -bottom-px left-0 h-0.5 w-12 bg-portal-cta" />
        <div className="min-w-0">
          <p className="text-xs font-semibold tracking-[0.04em] text-ink-muted uppercase">Module {number}</p>
          <h3 id={headingId} className="text-lg font-bold break-words text-ink">
            {module.title}
          </h3>
        </div>
        {module.locked ? (
          <span className="inline-flex items-center gap-2 rounded-md bg-surface-muted px-2.5 py-1 text-sm text-ink">
            <Badge tone="neutral">Locked</Badge>
            Complete “{module.waitingFor}” first.
          </span>
        ) : module.completed && module.topics.some((topic) => topic.required) ? (
          <Badge tone="success">Module completed</Badge>
        ) : completedCount > 0 ? (
          <Badge tone="success">{`${completedCount} completed`}</Badge>
        ) : null}
      </div>
      {/* Plain text: React shows it as text, never as HTML. Line breaks are kept. */}
      {module.description && (
        <p className="px-4 pt-3 text-sm leading-6 break-words whitespace-pre-line text-ink-muted sm:px-5">{module.description}</p>
      )}

      {module.topics.length > 0 ? (
        <ol className="divide-y divide-line px-4 pb-2 sm:px-5">
          {module.topics.map((topic) => (
            <li key={topic.id} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 py-3">
              <span className="flex min-w-0 items-center gap-3">
                <NavIcon name="documents" className={`size-5 ${module.locked ? 'text-ink-muted' : 'text-ink'}`} />
                <h4 className="text-[0.95rem] font-medium break-words">
                  {module.locked ? (
                    <span className="text-ink-muted">{topic.title}</span>
                  ) : (
                    <Link to={`/courses/${lmsCourseId}/pages/${topic.id}`} className={textLinkClasses}>
                      {topic.title}
                    </Link>
                  )}
                </h4>
                {topic.required && <Badge tone="neutral">Required</Badge>}
              </span>
              <span className="flex flex-wrap items-center gap-2 text-xs text-ink-muted">
                {topic.materials.length > 0 && fileCount(topic.materials.length)}
                {module.locked ? <Badge tone="neutral">Locked</Badge> : topic.completed && <Badge tone="success">Completed</Badge>}
              </span>
            </li>
          ))}
        </ol>
      ) : (
        <p className="px-4 py-3 text-sm text-ink-muted sm:px-5">No pages published in this module yet.</p>
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
      <h2 id={HEADING_ID} className="flex items-center gap-2.5 text-lg font-semibold text-ink">
        <NavIcon name="academics" className="size-6 text-portal-navy" />
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
          <ol className="space-y-4">
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
