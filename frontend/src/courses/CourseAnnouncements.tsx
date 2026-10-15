import type { ApiRequestState } from '../api/useApiRequest'
import { ActionButton } from '../components/ActionButton'
import type { LmsCourseAnnouncement } from '../types/lms'
import { formatDateTime } from './dates'

type CourseAnnouncementsProps = {
  announcements: ApiRequestState<LmsCourseAnnouncement[]> & { retry: () => void }
}

const HEADING_ID = 'course-announcements-heading'

/**
 * The course's published announcements, newest first, as plain text with their line breaks. This
 * section loads on its own: if it fails, the rest of the course page stays as it is.
 */
export function CourseAnnouncements({ announcements }: CourseAnnouncementsProps) {
  return (
    <section aria-labelledby={HEADING_ID} className="mt-8 max-w-2xl">
      <h2 id={HEADING_ID} className="text-base font-semibold text-ink">
        Announcements
      </h2>

      <div className="mt-3">
        {announcements.status === 'loading' && (
          <p role="status" className="text-sm text-ink-muted">
            Loading announcements…
          </p>
        )}

        {announcements.status === 'failed' && (
          <div role="alert" className="flex flex-wrap items-center gap-3">
            <p className="text-sm text-ink">Couldn’t load announcements.</p>
            <ActionButton onClick={announcements.retry}>Retry</ActionButton>
          </div>
        )}

        {(announcements.status === 'notFound' || announcements.status === 'denied') && (
          <p className="text-sm text-ink-muted">Announcements are not available to your account.</p>
        )}

        {announcements.status === 'loaded' && announcements.data.length === 0 && (
          <p className="text-sm text-ink-muted">No announcements yet.</p>
        )}

        {announcements.status === 'loaded' && announcements.data.length > 0 && (
          <ol className="divide-y divide-line rounded-panel border border-line bg-surface">
            {announcements.data.map((announcement) => (
              <li key={announcement.id}>
                <article aria-labelledby={`announcement-${announcement.id}`} className="px-4 py-3 sm:px-5">
                  <h3 id={`announcement-${announcement.id}`} className="text-sm font-semibold break-words text-ink">
                    {announcement.title}
                  </h3>
                  <p className="text-xs text-ink-muted">
                    Published <time dateTime={announcement.publishedAt}>{formatDateTime(announcement.publishedAt)}</time>
                  </p>
                  {/* Plain text: React shows it as text, never as HTML. Line breaks are kept. */}
                  <p className="mt-1.5 text-sm leading-6 break-words whitespace-pre-line text-ink">
                    {announcement.message}
                  </p>
                </article>
              </li>
            ))}
          </ol>
        )}
      </div>
    </section>
  )
}
