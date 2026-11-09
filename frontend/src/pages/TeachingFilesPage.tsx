import { useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import { archiveMaterial, getFacultyCourseContent, getFacultyCourseFiles, publishMaterial, uploadMaterial } from '../api/lmsContentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { ActionButton } from '../components/ActionButton'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId } from '../courses/courseRoute'
import { DownloadButton, FileTitle, UploadedAt } from '../courses/FileRowParts'
import { matchesFile } from '../courses/fileSearch'
import { useMaterialDownload } from '../courses/useMaterialDownload'
import type { FormOutcome } from '../teaching/ContentForm'
import { MaterialUploadForm, type UploadPage } from '../teaching/MaterialUploadForm'
import { hiddenBecause } from '../teaching/fileVisibility'
import type { LmsFacultyCourseFile } from '../types/lms'
import { Alert, Badge, Button, Card, DataTable, PageHeader, TextField } from '../ui'
import { textLinkClasses } from '../ui/styles'

const TEACHING = { to: '/teaching', label: 'Teaching' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

function refusal(status: number | null, apiMessage: string | null): string {
  if (status === 403) {
    return 'You don’t have access to change this course’s files. If your access changed recently, sign out and sign in again.'
  }
  return apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t save. Check your connection and try again.'
}

function asFailure(error: unknown): FormOutcome {
  const status = getHttpStatus(error)
  const body = getApiError(error)
  const fieldErrors: Record<string, string> = {}
  for (const field of body?.fieldErrors ?? []) {
    fieldErrors[field.field] = field.message
  }
  return { ok: false, message: Object.keys(fieldErrors).length > 0 ? null : refusal(status, body?.message ?? null), fieldErrors }
}

function Shell({ lmsCourseId, title, children }: { lmsCourseId: number | null; title?: string; children: ReactNode }) {
  return (
    <div className="max-w-6xl">
      <title>Files · Stacc</title>
      <PageHeader
        title="Files"
        breadcrumb={
          <Breadcrumb
            parent={lmsCourseId === null ? TEACHING : [TEACHING, { to: `/teaching/${lmsCourseId}`, label: title ?? 'Course' }]}
            current="Files"
          />
        }
        description="Every file of this course that is not archived, drafts included, with the page it is on and whether students see it."
      />
      {children}
    </div>
  )
}

/**
 * /teaching/:lmsCourseId/files: the course's files as faculty manage them. Upload puts a new file on a chosen page as
 * a draft; Publish and Archive (confirmed first) use the same routes as course content, and nothing is deleted. The
 * backend checks that the faculty member teaches the course on every request.
 */
export function TeachingFilesPage() {
  const { account } = useAuth()
  const queryClient = useQueryClient()
  const lmsCourseId = parseCourseId(useParams().lmsCourseId)
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])
  const ask = isFaculty && lmsCourseId !== null
  const files = useApiQuery(ask ? queryKeys.lms.facultyCourseFiles(lmsCourseId) : null, (signal) =>
    getFacultyCourseFiles(lmsCourseId ?? 0, signal),
  )
  const content = useApiQuery(ask ? queryKeys.lms.facultyCourseContent(lmsCourseId) : null, (signal) =>
    getFacultyCourseContent(lmsCourseId ?? 0, signal),
  )
  const downloads = useMaterialDownload()
  const [search, setSearch] = useState('')
  const [uploading, setUploading] = useState(false)
  const [archiving, setArchiving] = useState<LmsFacultyCourseFile | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const [publishError, setPublishError] = useState<string | null>(null)

  if (!isFaculty) {
    return (
      <Shell lmsCourseId={lmsCourseId}>
        <StatePanel>
          <AccessDeniedState title="Course files are managed by faculty accounts." message={ASK_THE_COLLEGE} />
        </StatePanel>
      </Shell>
    )
  }
  if (files.status !== 'loaded') {
    return (
      <Shell lmsCourseId={lmsCourseId}>
        <StatePanel>
          {files.status === 'loading' && <LoadingState label="Loading files…" />}
          {files.status === 'failed' && (
            <ErrorState title="Couldn’t load the files" message="Check your connection and try again." onRetry={files.retry} />
          )}
          {files.status === 'denied' && <AccessDeniedState title="You don’t have access to this course’s files." message={ASK_THE_COLLEGE} />}
          {files.status === 'notFound' && (
            <EmptyState title="Course unavailable" description="This LMS course could not be found or is not one you teach." />
          )}
        </StatePanel>
      </Shell>
    )
  }

  const course = files.data
  const pages: UploadPage[] =
    content.status === 'loaded'
      ? content.data.modules.flatMap((module) => module.topics.map((topic) => ({ id: topic.id, title: topic.title, moduleTitle: module.title })))
      : []
  const shown = course.files.filter((file) => matchesFile(file, search))

  async function changed(message: string) {
    setAnnouncement(message)
    await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
  }

  async function publish(file: LmsFacultyCourseFile) {
    setPublishError(null)
    try {
      await publishMaterial(file.id)
      await changed(`Published ${file.title}.`)
    } catch (error) {
      const status = getHttpStatus(error)
      if (status !== 401) {
        setPublishError(refusal(status, getApiError(error)?.message ?? null))
      }
    }
  }

  return (
    <Shell lmsCourseId={lmsCourseId} title={course.courseName}>
      <p role="status" className="sr-only">
        {announcement}
      </p>
      {course.lmsCourseStatus === 'DRAFT' && (
        <Alert tone="warning" className="mb-4">
          This course is not published yet, so students see none of its files.
        </Alert>
      )}
      {publishError && (
        <Alert tone="error" className="mb-4">
          {publishError}
        </Alert>
      )}
      {uploading ? (
        <div className="mb-4">
          {pages.length === 0 ? (
            <Alert tone="info" action={<Button variant="secondary" onClick={() => setUploading(false)}>Close</Button>}>
              {content.status === 'loaded'
                ? 'Every file is on a page, and this course has no pages yet. Add a page in course content first.'
                : 'Loading the course’s pages…'}
            </Alert>
          ) : (
            <MaterialUploadForm
              pages={pages}
              onCancel={() => setUploading(false)}
              onSubmit={async (title, description, file, pageId) => {
                try {
                  await uploadMaterial(pageId ?? 0, { title, description, file })
                  setUploading(false)
                  await changed(`Uploaded ${title} as a draft.`)
                  return { ok: true }
                } catch (error) {
                  return asFailure(error)
                }
              }}
            />
          )}
        </div>
      ) : (
        <div className="mb-4">
          <Button onClick={() => setUploading(true)}>Upload file</Button>
        </div>
      )}
      {archiving && (
        <div className="mb-4">
          <StatusConfirmation
            question={`Archive ${archiving.title}?`}
            detail="Students no longer see it. The file is kept, and nothing is deleted, but an archived file cannot be published again."
            confirmLabel="Archive file"
            busyLabel="Archiving…"
            describeRefusal={refusal}
            onCancel={() => setArchiving(null)}
            onConfirm={async () => {
              await archiveMaterial(archiving.id)
              setArchiving(null)
              await changed(`Archived ${archiving.title}.`)
            }}
          />
        </div>
      )}
      <Card padding="none">
        {course.files.length > 0 && (
          <div className="border-b border-line px-4 py-3 sm:px-5">
            <TextField
              label="Search files"
              type="search"
              value={search}
              maxLength={100}
              hint={`${shown.length} of ${course.files.length} ${course.files.length === 1 ? 'file' : 'files'}`}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>
        )}
        <DataTable<LmsFacultyCourseFile>
          caption="Course files"
          rows={shown}
          rowKey={(file) => file.id}
          empty={<EmptyState title={course.files.length === 0 ? 'No files yet.' : 'No files match.'} />}
          columns={[
            { key: 'file', header: 'File', primary: true, cell: (file) => <FileTitle file={file} /> },
            {
              key: 'page',
              header: 'Page',
              cell: (file) => (
                <>
                  <Link to={`/teaching/${course.lmsCourseId}/pages/${file.pageId}`} className={textLinkClasses}>
                    {file.pageTitle}
                  </Link>
                  <span className="block text-sm text-ink-muted">{file.moduleTitle}</span>
                </>
              ),
            },
            {
              key: 'status',
              header: 'Students',
              cell: (file) => {
                const hidden = hiddenBecause(file, course.lmsCourseStatus)
                return hidden ? (
                  <span className="text-sm">
                    <Badge tone="warning">{file.status === 'DRAFT' ? 'Draft' : 'Hidden'}</Badge>
                    <span className="mt-1 block text-ink-muted">{hidden}</span>
                  </span>
                ) : (
                  <Badge tone="success">Visible</Badge>
                )
              },
            },
            { key: 'uploaded', header: 'Uploaded', cell: (file) => <UploadedAt at={file.uploadedAt} /> },
            {
              key: 'actions',
              header: 'Actions',
              cell: (file) => (
                <div className="flex flex-wrap items-start gap-2">
                  <DownloadButton file={file} downloads={downloads} />
                  {file.status === 'DRAFT' && (
                    <ActionButton onClick={() => void publish(file)}>
                      Publish<span className="sr-only"> {file.title}</span>
                    </ActionButton>
                  )}
                  <ActionButton onClick={() => setArchiving(file)}>
                    Archive<span className="sr-only"> {file.title}</span>
                  </ActionButton>
                </div>
              ),
            },
          ]}
        />
      </Card>
    </Shell>
  )
}
