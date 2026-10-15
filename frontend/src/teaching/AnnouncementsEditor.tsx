import { useRef, useState } from 'react'
import {
  archiveAnnouncement,
  createAnnouncement,
  publishAnnouncement,
  updateAnnouncement,
} from '../api/lmsAnnouncementsApi'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import { formatDateTime } from '../courses/dates'
import type { LmsFacultyAnnouncement } from '../types/lms'
import { ArchiveConfirmation } from './ArchiveConfirmation'
import { ContentForm } from './ContentForm'
import { ANNOUNCEMENT_MESSAGE_MAX, asOutcome, srContext, TITLE_MAX } from './editorState'
import { StatusTag } from './StatusTag'
import { type ChangeResult, useContentChanges, useFocusAfterChanges } from './useContentChanges'
import { useFacultyAnnouncements } from './useFacultyAnnouncements'

type AnnouncementForm = { type: 'add' } | { type: 'edit'; id: number }

type Changes = ReturnType<typeof useContentChanges>

const FORM_LABELS = {
  titleLabel: 'Title',
  bodyLabel: 'Message',
  bodyField: 'message',
  bodyMissing: 'Enter a message.',
  bodyRows: 6,
  titleMaxLength: TITLE_MAX,
  bodyMaxLength: ANNOUNCEMENT_MESSAGE_MAX,
} as const

type AnnouncementItemProps = {
  announcement: LmsFacultyAnnouncement
  isEditing: boolean
  isConfirming: boolean
  setOpenForm: (form: AnnouncementForm | null) => void
  setConfirming: (id: number | null) => void
  changes: Changes
}

function AnnouncementItem({
  announcement,
  isEditing,
  isConfirming,
  setOpenForm,
  setConfirming,
  changes,
}: AnnouncementItemProps) {
  const key = `announcement-${announcement.id}`
  const name = `announcement “${announcement.title}”`

  if (isEditing) {
    return (
      <ContentForm
        {...FORM_LABELS}
        heading={`Edit ${name}`}
        note={
          announcement.status === 'PUBLISHED'
            ? 'This announcement is published. Students see your changes as soon as you save.'
            : undefined
        }
        initialTitle={announcement.title}
        initialBody={announcement.message}
        submitLabel="Save announcement"
        submittingLabel="Saving…"
        onSubmit={async (title, message) => {
          const result = await changes.run(() => updateAnnouncement(announcement.id, { title, message: message ?? '' }), {
            done: 'Announcement saved.',
            focus: () => [`${key}-heading`],
            showError: false,
          })
          if (result?.ok) {
            setOpenForm(null)
          }
          return asOutcome(result)
        }}
        onCancel={() => {
          setOpenForm(null)
          changes.focusNext([`${key}-edit`])
        }}
      />
    )
  }

  return (
    <article aria-labelledby={`${key}-title`} className="px-4 py-3 sm:px-5">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <h3
          id={`${key}-title`}
          tabIndex={-1}
          data-focus-key={`${key}-heading`}
          className="text-sm font-semibold break-words text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
        >
          {announcement.title}
        </h3>
        <StatusTag status={announcement.status} label="Announcement status" />
      </div>
      <p className="text-xs text-ink-muted">
        {announcement.publishedAt ? (
          <>
            Published <time dateTime={announcement.publishedAt}>{formatDateTime(announcement.publishedAt)}</time>
          </>
        ) : (
          'Not published yet. Students cannot see it.'
        )}
      </p>
      <p className="mt-1.5 text-sm leading-6 break-words whitespace-pre-line text-ink">{announcement.message}</p>

      {isConfirming ? (
        <ArchiveConfirmation
          what={name}
          onConfirm={() => {
            setConfirming(null)
            void changes.run(() => archiveAnnouncement(announcement.id), {
              done: `Archived ${name}.`,
              focus: () => ['add-announcement', 'announcements-heading'],
            })
          }}
          onCancel={() => {
            setConfirming(null)
            changes.focusNext([`${key}-archive`])
          }}
        />
      ) : (
        <div className="mt-2 flex flex-wrap gap-2">
          <ActionButton focusKey={`${key}-edit`} onClick={() => setOpenForm({ type: 'edit', id: announcement.id })}>
            Edit{srContext(name)}
          </ActionButton>
          {announcement.status === 'DRAFT' && (
            <ActionButton
              onClick={() =>
                void changes.run(() => publishAnnouncement(announcement.id), {
                  done: `Published ${name}. Students can see it now.`,
                  focus: () => [`${key}-heading`],
                })
              }
            >
              Publish{srContext(name)}
            </ActionButton>
          )}
          <ActionButton focusKey={`${key}-archive`} onClick={() => setConfirming(announcement.id)}>
            Archive{srContext(name)}
          </ActionButton>
        </div>
      )}
    </article>
  )
}

/**
 * The announcements of a course in the faculty page: drafts and published ones, newest created first.
 * Each change goes to the backend, which checks access again, and the list then reloads. Archived
 * announcements are kept but not listed.
 */
export function AnnouncementsEditor({ lmsCourseId }: { lmsCourseId: number }) {
  const announcements = useFacultyAnnouncements(lmsCourseId)
  const changes = useContentChanges(announcements.refresh)
  const containerRef = useRef<HTMLElement>(null)
  const [openForm, setOpenForm] = useState<AnnouncementForm | null>(null)
  const [confirming, setConfirming] = useState<number | null>(null)
  const list = announcements.status === 'loaded' ? announcements.data : null

  useFocusAfterChanges(containerRef, list, changes.pendingFocus, changes.focusHandled)

  async function add(title: string, message: string | null) {
    const result: ChangeResult<LmsFacultyAnnouncement> | null = await changes.run(
      () => createAnnouncement(lmsCourseId, { title, message: message ?? '' }),
      {
        done: 'Announcement added as a draft.',
        focus: (created) => [`announcement-${created.id}-heading`],
        showError: false,
      },
    )
    if (result?.ok) {
      setOpenForm(null)
    }
    return asOutcome(result)
  }

  return (
    <section ref={containerRef} aria-labelledby="announcements-heading" aria-busy={changes.busy} className="mt-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <h2
            id="announcements-heading"
            tabIndex={-1}
            data-focus-key="announcements-heading"
            className="text-base font-semibold text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
          >
            Announcements
          </h2>
          {changes.busy && <span className="text-sm text-ink-muted">Saving…</span>}
        </div>
        {list !== null && openForm?.type !== 'add' && (
          <div className="w-full sm:w-auto">
            <Button variant="secondary" data-focus-key="add-announcement" onClick={() => setOpenForm({ type: 'add' })}>
              Add announcement
            </Button>
          </div>
        )}
      </div>
      <p className="mt-1 text-sm leading-6 text-ink-muted">
        Course-wide updates for students. They see published announcements only.
      </p>

      <p role="status" className="sr-only">
        {changes.announcement}
      </p>
      <div role="alert">
        {changes.error && (
          <p className="mt-3 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger">
            {changes.error}
          </p>
        )}
      </div>

      <div className="mt-3 space-y-3">
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
          <p className="text-sm text-ink-muted">Announcements are not available for this course.</p>
        )}

        {openForm?.type === 'add' && (
          <ContentForm
            {...FORM_LABELS}
            heading="New announcement"
            note="It is saved as a draft. Students see it once you publish it."
            submitLabel="Create announcement"
            submittingLabel="Creating…"
            onSubmit={add}
            onCancel={() => {
              setOpenForm(null)
              changes.focusNext(['add-announcement'])
            }}
          />
        )}

        {list !== null && list.length === 0 && openForm?.type !== 'add' && (
          <p className="text-sm text-ink-muted">
            No announcements yet. Create an announcement to share a course-wide update with students.
          </p>
        )}

        {list !== null && list.length > 0 && (
          <ol aria-label="Announcements" className="divide-y divide-line rounded-panel border border-line bg-surface">
            {list.map((announcement) => (
              <li key={announcement.id}>
                <AnnouncementItem
                  announcement={announcement}
                  isEditing={openForm?.type === 'edit' && openForm.id === announcement.id}
                  isConfirming={confirming === announcement.id}
                  setOpenForm={setOpenForm}
                  setConfirming={setConfirming}
                  changes={changes}
                />
              </li>
            ))}
          </ol>
        )}
      </div>
    </section>
  )
}
