import { useState } from 'react'
import { getAcademicSessions, getCourses, getSemesters } from '../api/adminAcademicApi'
import { createCourseOffering, getCourseOfferings, updateCourseOffering } from '../api/adminEnrollmentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import type { AdminCourseOffering, LifecycleStatus } from '../types/admin'
import { ButtonLink, SelectField } from '../ui'
import { ENROLLMENT_DENIED, semesterLabel } from './enrollmentText'
import { MasterForm } from './MasterForm'
import { CodeName, MasterStatus } from './MasterParts'
import { MasterSection } from './MasterSection'
import { lifecycleOptions } from './masterStatus'

/**
 * Course offerings: one catalog course taught in one semester. They can be listed by session and semester, searched,
 * added from real courses and semesters, and moved forward through their status. The course and semester never
 * change and nothing is deleted. Each offering links to its roster.
 */
export function OfferingsSection() {
  const [sessionFilter, setSessionFilter] = useState('')
  const [semesterFilter, setSemesterFilter] = useState('')
  const sessions = useApiQuery(queryKeys.admin.sessions(), getAcademicSessions)
  const semesters = useApiQuery(queryKeys.admin.semesters(), getSemesters)
  const courses = useApiQuery(queryKeys.admin.courses(), getCourses)
  const sessionList = sessions.status === 'loaded' ? sessions.data : null
  const semesterList = semesters.status === 'loaded' ? semesters.data : null
  const courseList = courses.status === 'loaded' ? courses.data : null
  const filteredSemesters = (semesterList ?? []).filter(
    (semester) => sessionFilter === '' || String(semester.academicSessionId) === sessionFilter,
  )

  const filters = {
    academicSessionId: sessionFilter === '' ? undefined : Number(sessionFilter),
    semesterId: semesterFilter === '' ? undefined : Number(semesterFilter),
  }
  const request = useApiQuery(queryKeys.admin.offerings(filters), (signal) => getCourseOfferings(filters, signal))
  const blocked =
    courseList === null || semesterList === null
      ? 'Loading courses and semesters…'
      : courseList.length === 0 || semesterList.length === 0
        ? 'Add a course and a semester first.'
        : null

  const toolbar = (
    <>
      <SelectField
        label="Academic session"
        value={sessionFilter}
        onChange={(event) => {
          setSessionFilter(event.target.value)
          setSemesterFilter('')
        }}
      >
        <option value="">All sessions</option>
        {(sessionList ?? []).map((session) => (
          <option key={session.id} value={String(session.id)}>
            {session.code}
          </option>
        ))}
      </SelectField>
      <SelectField label="Semester" value={semesterFilter} onChange={(event) => setSemesterFilter(event.target.value)}>
        <option value="">All semesters</option>
        {filteredSemesters.map((semester) => (
          <option key={semester.id} value={String(semester.id)}>
            {semesterLabel(semester)}
          </option>
        ))}
      </SelectField>
    </>
  )

  return (
    <MasterSection<AdminCourseOffering>
      title="Course offerings"
      singular="course offering"
      intro="Each course taught in one semester. The course and semester cannot change; only the status moves forward. Nothing is deleted."
      request={request}
      blockedReason={blocked}
      emptyTitle="No course offerings available."
      deniedTitle={ENROLLMENT_DENIED}
      toolbar={toolbar}
      searchText={(row) => `${row.courseCode} ${row.courseName} ${row.programCode} ${row.academicSessionCode}`}
      rowStatus={(row) => row.status}
      rowKey={(row) => row.courseOfferingId}
      rowName={(row) => `${row.courseCode} in semester ${row.semesterNumber}, ${row.academicSessionCode}`}
      columns={[
        { header: 'Course', cell: (row) => <CodeName code={row.courseCode} name={row.courseName} /> },
        { header: 'Program', cell: (row) => row.programCode },
        { header: 'Semester', cell: (row) => `Semester ${row.semesterNumber}` },
        { header: 'Session', cell: (row) => row.academicSessionCode },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      rowActions={(row) => (
        <ButtonLink to={`/admin/academic/offerings/${row.courseOfferingId}/enrollments`} variant="secondary" size="sm">
          View roster
          <span className="sr-only">
            {' '}
            of {row.courseCode} in semester {row.semesterNumber}, {row.academicSessionCode}
          </span>
        </ButtonLink>
      )}
      renderCreate={(done, cancel) => (
        <MasterForm
          heading="New course offering"
          note="The course and semester cannot be changed later."
          deniedMessage={ENROLLMENT_DENIED}
          fields={[
            { name: 'courseId', label: 'Course', kind: 'select', choose: 'Choose a course',
              options: (courseList ?? []).map((course) => ({
                value: String(course.id),
                label: `${course.code} — ${course.name}${course.status === 'INACTIVE' ? ' (inactive)' : ''}`,
              })) },
            { name: 'semesterId', label: 'Semester', kind: 'select', choose: 'Choose a semester',
              options: (semesterList ?? []).map((semester) => ({
                value: String(semester.id),
                label: `${semesterLabel(semester)}${semester.status === 'CLOSED' ? ' (closed)' : ''}`,
              })) },
          ]}
          initial={{ courseId: '', semesterId: '' }}
          submitLabel="Save course offering"
          onSubmit={async (values) => {
            const saved = await createCourseOffering({ courseId: Number(values.courseId), semesterId: Number(values.semesterId) })
            done(`${saved.courseCode} offered in semester ${saved.semesterNumber}, ${saved.academicSessionCode}.`)
          }}
          onCancel={cancel}
        />
      )}
      renderEdit={(row, done, cancel) => (
        <MasterForm
          heading={`Edit ${row.courseCode} in semester ${row.semesterNumber}, ${row.academicSessionCode}`}
          note="Only the status can change. The course and semester stay as they are, because enrollments and teaching depend on them."
          deniedMessage={ENROLLMENT_DENIED}
          fields={[{ name: 'status', label: 'Status', kind: 'select', options: lifecycleOptions(row.status) }]}
          initial={{ status: row.status }}
          submitLabel="Save course offering"
          onSubmit={async (values) => {
            await updateCourseOffering(row.courseOfferingId, { status: values.status as LifecycleStatus })
            done(`${row.courseCode} in semester ${row.semesterNumber} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}
