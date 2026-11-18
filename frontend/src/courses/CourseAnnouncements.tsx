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
  standalone?: boolean
  announcements: ApiRequestState<LmsCourseAnnouncement[]> & { retry: () => void }
  lmsCourseId: number
}

export function CourseAnnouncements({ announcements, lmsCourseId, standalone = false }: CourseAnnouncementsProps) {
  const article = (announcement: LmsCourseAnnouncement) => (
    <article aria-labelledby={`announcement-${announcement.id}`} className={standalone ? 'px-5 py-5' : 'px-4 py-4 sm:px-5'}>
      <h3 id={`announcement-${announcement.id}`} className={`font-bold break-words ${standalone ? 'text-xl text-portal-navy' : 'text-base text-ink'}`}>
        {announcement.title}
      </h3>
      <p className={standalone ? 'mt-0.5 text-[0.95rem] text-ink-muted' : 'text-xs text-ink-muted'}>
        Published <time dateTime={announcement.publishedAt}>{formatDateTime(announcement.publishedAt)}</time>
      </p>
      <FormattedText
        text={announcement.message}
        format={announcement.messageFormat}
        className={`mt-2 leading-6 text-ink ${standalone ? 'text-[0.95rem]' : 'text-sm'}`}
        headingLevel={4}
      />
      <AnnouncementFiles
        files={announcement.files}
        title={announcement.title}
        download={(fileId) => downloadCourseAnnouncementFile(lmsCourseId, announcement.id, fileId)}
      />
    </article>
  )

  if (standalone) {
    if (announcements.status !== 'loaded' || announcements.data.length === 0) {
      return (
        <Card padding="none">
          {announcements.status !== 'loaded' ? <SectionState request={announcements} what="announcements" /> : <EmptyState title="No announcements yet." />}
        </Card>
      )
    }
    return (
      <ol className="space-y-4">
        {announcements.data.map((announcement) => (
          <li key={announcement.id} className="rounded-panel border border-line bg-surface">
            {article(announcement)}
          </li>
        ))}
      </ol>
    )
  }

  return (
    <Card title="Announcements" icon="campus" padding="none" className="mt-6">
      {announcements.status !== 'loaded' ? (
        <SectionState request={announcements} what="announcements" />
      ) : announcements.data.length === 0 ? (
        <EmptyState title="No announcements yet." />
      ) : (
        <ol className="divide-y divide-line">
          {announcements.data.map((announcement) => (
            <li key={announcement.id}>{article(announcement)}</li>
          ))}
        </ol>
      )}
    </Card>
  )
}
