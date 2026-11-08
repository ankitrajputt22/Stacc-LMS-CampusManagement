import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { getLmsCourseFiles } from '../api/lmsContentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId } from '../courses/courseRoute'
import { DownloadButton, FileTitle, UploadedAt } from '../courses/FileRowParts'
import { matchesFile } from '../courses/fileSearch'
import { useMaterialDownload } from '../courses/useMaterialDownload'
import type { LmsCourseFile } from '../types/lms'
import { Card, DataTable, PageHeader, TextField } from '../ui'
import { textLinkClasses } from '../ui/styles'

const COURSES = { to: '/courses', label: 'Courses' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

function crumbs(lmsCourseId: number | null) {
  return lmsCourseId === null ? COURSES : [COURSES, { to: `/courses/${lmsCourseId}`, label: 'Course' }]
}

function Shell({ lmsCourseId, children }: { lmsCourseId: number | null; children: ReactNode }) {
  return (
    <div className="max-w-5xl">
      <title>Files · Stacc</title>
      <PageHeader
        title="Files"
        breadcrumb={<Breadcrumb parent={crumbs(lmsCourseId)} current="Files" />}
        description="Every published file of this course, with the page it is on."
      />
      {children}
    </div>
  )
}

/**
 * /courses/:lmsCourseId/files: every published file of the course in course order, searchable on this page, each with
 * its page and a Download through the API. The backend checks the student's access on every request; a course the
 * student may not use shows "Course unavailable".
 */
export function CourseFilesPage() {
  const { account } = useAuth()
  const lmsCourseId = parseCourseId(useParams().lmsCourseId)
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])
  const files = useApiQuery(isStudent && lmsCourseId !== null ? queryKeys.lms.courseFiles(lmsCourseId) : null, (signal) =>
    getLmsCourseFiles(lmsCourseId ?? 0, signal),
  )
  const downloads = useMaterialDownload()
  const [search, setSearch] = useState('')

  if (!isStudent) {
    return (
      <Shell lmsCourseId={lmsCourseId}>
        <StatePanel>
          <AccessDeniedState title="Course files are for student accounts." message={ASK_THE_COLLEGE} />
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
          {files.status === 'denied' && <AccessDeniedState title="You don’t have access to course files." message={ASK_THE_COLLEGE} />}
          {files.status === 'notFound' && (
            <EmptyState title="Course unavailable" description="This LMS course could not be found or is not available to your account." />
          )}
        </StatePanel>
      </Shell>
    )
  }

  const shown = files.data.filter((file) => matchesFile(file, search))
  return (
    <Shell lmsCourseId={lmsCourseId}>
      <Card padding="none">
        {files.data.length > 0 && (
          <div className="border-b border-line px-4 py-3 sm:px-5">
            <TextField
              label="Search files"
              type="search"
              value={search}
              maxLength={100}
              hint={`${shown.length} of ${files.data.length} ${files.data.length === 1 ? 'file' : 'files'}`}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>
        )}
        <DataTable<LmsCourseFile>
          caption="Course files"
          rows={shown}
          rowKey={(file) => file.id}
          empty={
            <EmptyState
              title={files.data.length === 0 ? 'No files yet.' : 'No files match.'}
              description={files.data.length === 0 ? 'Files appear here once your teachers publish them.' : undefined}
            />
          }
          columns={[
            { key: 'file', header: 'File', primary: true, cell: (file) => <FileTitle file={file} /> },
            {
              key: 'page',
              header: 'Page',
              cell: (file) => (
                <>
                  <Link to={`/courses/${lmsCourseId}/pages/${file.pageId}`} className={textLinkClasses}>
                    {file.pageTitle}
                  </Link>
                  <span className="block text-sm text-ink-muted">{file.moduleTitle}</span>
                </>
              ),
            },
            { key: 'uploaded', header: 'Uploaded', cell: (file) => <UploadedAt at={file.uploadedAt} /> },
            { key: 'download', header: 'Download', cell: (file) => <DownloadButton file={file} downloads={downloads} /> },
          ]}
        />
      </Card>
    </Shell>
  )
}
