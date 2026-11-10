import { downloadCourseAnnouncementFile } from '../api/lmsAnnouncementsApi'
import type { ApiRequestState } from '../api/useApiRequest'
import { EmptyState } from '../components/states/EmptyState'
import { FormattedText } from '../formatting/FormattedText'
import type { LmsCourseAnnouncement } from '../types/lms'
import { Card } from '../ui'
import { AnnouncementFiles } from './AnnouncementFiles'
import { formatDateTime } from './dates'
import { SectionState } from './SectionState'

type CourseAnnouncementsProps = {
  /** On the section's own page, whose title already names it: a plain card without its own heading. */
  standalone?: boolean
  announcements: ApiRequestState<LmsCourseAnnouncement[]> & { retry: () => void }
  /** The course, for downloading announcement files. */
  lmsCourseId: number
}

/**
 * The course's published announcements, newest first, each shown as its format says, never as HTML. This part loads
 * on its own: if it fails, the rest of the course page stays as it is.
 */
export function CourseAnnouncements({ announcements, lmsCourseId, standalone = false }: CourseAnnouncementsProps) {
  return (
    <Card title={standalone ? undefined : 'Announcements'} padding="none" className={standalone ? '' : 'mt-6'}>
      {announcements.status !== 'loaded' ? (
        <SectionState request={announcements} what="announcements" />
      ) : announcements.data.length === 0 ? (
        <EmptyState title="No announcements yet." />
      ) : (
        <ol className="divide-y divide-line">
          {announcements.data.map((announcement) => (
            <li key={announcement.id}>
              <article aria-labelledby={`announcement-${announcement.id}`} className="px-4 py-4 sm:px-5">
                <h3 id={`announcement-${announcement.id}`} className="text-base font-semibold break-words text-ink">
                  {announcement.title}
                </h3>
                <p className="text-xs text-ink-muted">
                  Published <time dateTime={announcement.publishedAt}>{formatDateTime(announcement.publishedAt)}</time>
                </p>
                <FormattedText
                  text={announcement.message}
                  format={announcement.messageFormat}
                  className="mt-2 text-sm leading-6 text-ink"
                  headingLevel={4}
                />
                <AnnouncementFiles
                  files={announcement.files}
                  title={announcement.title}
                  download={(fileId) => downloadCourseAnnouncementFile(lmsCourseId, announcement.id, fileId)}
                />
              </article>
            </li>
          ))}
        </ol>
      )}
    </Card>
  )
}
