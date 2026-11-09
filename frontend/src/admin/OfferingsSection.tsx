import { useState } from 'react'
import { getAcademicSessions, getCourses, getSections, getSemesters } from '../api/adminAcademicApi'
import { createCourseOffering, getCourseOfferings, updateCourseOffering } from '../api/adminEnrollmentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import type { AdminCourseOffering, LifecycleStatus, OfferingFaculty, RegistrationStatus } from '../types/admin'
import { Badge, ButtonLink, SelectField } from '../ui'
import { ENROLLMENT_DENIED, semesterLabel } from './enrollmentText'
import { type FieldSpec, MasterForm } from './MasterForm'
import { CodeName, MasterStatus } from './MasterParts'
import { MasterSection } from './MasterSection'
import { lifecycleOptions } from './masterStatus'
import { REGISTRATION_OPTIONS, registrationTone, registrationWord, roleWord } from './offeringText'

/** "Dr. Meera Iyer (Primary)", "faculty1 (Co-faculty)", or Unassigned when no one actively teaches it. */
function FacultyCell({ faculty }: { faculty: OfferingFaculty[] | null }) {
  const active = (faculty ?? []).filter((member) => member.status === 'ACTIVE')
  if (active.length === 0) {
    return <span className="text-ink-muted">Unassigned</span>
  }
  return (
    <ul className="space-y-0.5">
      {active.map((member) => (
        <li key={member.teachingAssignmentId}>
          {member.name ?? member.loginId}
          <span className="text-ink-muted"> ({roleWord(member.role)})</span>
        </li>
      ))}
    </ul>
  )
}

/**
 * Course offerings: one catalog course taught in one semester, for the whole semester or for one of its sections. They
 * can be listed by session and semester, searched, added from active courses, open semesters, and active sections, and
 * moved forward through their status. The course, semester, and section never change and nothing is deleted. Each
 * offering links to its roster and to who teaches it.
 */
export function OfferingsSection() {
  const [sessionFilter, setSessionFilter] = useState('')
  const [semesterFilter, setSemesterFilter] = useState('')
  const sessions = useApiQuery(queryKeys.admin.sessions(), getAcademicSessions)
  const semesters = useApiQuery(queryKeys.admin.semesters(), getSemesters)
  const courses = useApiQuery(queryKeys.admin.courses(), getCourses)
  const sections = useApiQuery(queryKeys.admin.sections(), (signal) => getSections(undefined, signal))
  const sessionList = sessions.status === 'loaded' ? sessions.data : null
  const semesterList = semesters.status === 'loaded' ? semesters.data : null
  const courseList = courses.status === 'loaded' ? courses.data : null
  const sectionList = sections.status === 'loaded' ? sections.data : []
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

  const capacityField: FieldSpec = {
    name: 'capacity', label: 'Capacity (optional)', kind: 'integer', min: 1, max: 1000, optional: true,
    hint: 'From 1 to 1000. Course registration uses it from v0.4; leave empty for no limit.',
  }
  const registrationField: FieldSpec = {
    name: 'registrationStatus', label: 'Registration', kind: 'select', options: REGISTRATION_OPTIONS,
    hint: 'Whether students may register for it. Registration itself comes in v0.4.',
  }

  return (
    <MasterSection<AdminCourseOffering>
      title="Course offerings"
      singular="course offering"
      intro="Each course taught in one semester, to the whole semester or to one section. The course, semester, and section cannot change; only the status moves forward. Nothing is deleted."
      request={request}
      blockedReason={blocked}
      emptyTitle="No course offerings available."
      deniedTitle={ENROLLMENT_DENIED}
      toolbar={toolbar}
      searchText={(row) =>
        `${row.courseCode} ${row.courseName} ${row.programCode} ${row.academicSessionCode} ${row.sectionCode ?? ''} ${(row.faculty ?? [])
          .map((member) => `${member.name ?? ''} ${member.loginId}`)
          .join(' ')}`
      }
      rowStatus={(row) => row.status}
      filters={[
        { label: 'Registration', all: 'Any registration', options: REGISTRATION_OPTIONS, value: (row) => row.registrationStatus },
      ]}
      rowKey={(row) => row.courseOfferingId}
      rowName={(row) =>
        `${row.courseCode}${row.sectionCode ? ` section ${row.sectionCode}` : ''} in semester ${row.semesterNumber}, ${row.academicSessionCode}`
      }
      columns={[
        { header: 'Course', primary: true, cell: (row) => <CodeName code={row.courseCode} name={row.courseName} /> },
        { header: 'Section', cell: (row) => row.sectionCode ?? <span className="text-ink-muted">Whole semester</span> },
        { header: 'Semester', cell: (row) => `${row.programCode} · Sem ${row.semesterNumber} · ${row.academicSessionCode}` },
        { header: 'Capacity', align: 'right', cell: (row) => row.capacity ?? '—' },
        {
          header: 'Registration',
          cell: (row) => <Badge tone={registrationTone(row.registrationStatus)}>{registrationWord(row.registrationStatus)}</Badge>,
        },
        { header: 'Faculty', cell: (row) => <FacultyCell faculty={row.faculty} /> },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      rowActions={(row) => (
        <>
          <ButtonLink to={`/admin/academic/faculty-assignments?offering=${row.courseOfferingId}`} variant="secondary" size="sm">
            Faculty<span className="sr-only"> of {row.courseCode}</span>
          </ButtonLink>
          <ButtonLink to={`/admin/academic/offerings/${row.courseOfferingId}/enrollments`} variant="secondary" size="sm">
            Roster<span className="sr-only"> of {row.courseCode}</span>
          </ButtonLink>
        </>
      )}
      renderCreate={(done, cancel) => (
        <MasterForm
          heading="New course offering"
          note="The course, semester, and section cannot be changed later. A course is offered either to the whole semester or per section, not both."
          deniedMessage={ENROLLMENT_DENIED}
          fields={[
            { name: 'courseId', label: 'Course', kind: 'select', choose: 'Choose a course', hint: 'Active courses only.',
              options: (courseList ?? [])
                .filter((course) => course.status === 'ACTIVE')
                .map((course) => ({ value: String(course.id), label: `${course.code} — ${course.name}` })) },
            { name: 'semesterId', label: 'Semester', kind: 'select', choose: 'Choose a semester',
              hint: 'Closed semesters are not listed.',
              options: (semesterList ?? [])
                .filter((semester) => semester.status !== 'CLOSED')
                .map((semester) => ({ value: String(semester.id), label: semesterLabel(semester) })) },
            { name: 'sectionId', label: 'Section (optional)', kind: 'select', optional: true, choose: 'Whole semester',
              hint: 'An active section of the chosen semester.',
              options: sectionList
                .filter((section) => section.status === 'ACTIVE')
                .map((section) => ({
                  value: String(section.id),
                  label: `${section.code} — ${section.programCode} Sem ${section.semesterNumber} · ${section.academicSessionCode}`,
                })) },
            capacityField,
            registrationField,
          ]}
          initial={{ courseId: '', semesterId: '', sectionId: '', capacity: '', registrationStatus: 'DRAFT' }}
          validate={(values) => {
            const section = sectionList.find((candidate) => String(candidate.id) === values.sectionId)
            return section && values.semesterId !== '' && String(section.semesterId) !== values.semesterId
              ? { sectionId: 'Choose a section of the chosen semester.' }
              : {}
          }}
          submitLabel="Save course offering"
          onSubmit={async (values) => {
            const saved = await createCourseOffering({
              courseId: Number(values.courseId),
              semesterId: Number(values.semesterId),
              sectionId: values.sectionId === '' ? null : Number(values.sectionId),
              capacity: values.capacity.trim() === '' ? null : Number(values.capacity),
              registrationStatus: values.registrationStatus as RegistrationStatus,
            })
            done(
              `${saved.courseCode}${saved.sectionCode ? ` section ${saved.sectionCode}` : ''} offered in semester ${saved.semesterNumber}, ${saved.academicSessionCode}.`,
            )
          }}
          onCancel={cancel}
        />
      )}
      renderEdit={(row, done, cancel) => (
        <MasterForm
          heading={`Edit ${row.courseCode}${row.sectionCode ? ` section ${row.sectionCode}` : ''} in semester ${row.semesterNumber}, ${row.academicSessionCode}`}
          note="The course, semester, and section stay as they are, because enrollments and teaching depend on them."
          deniedMessage={ENROLLMENT_DENIED}
          fields={[
            { name: 'status', label: 'Status', kind: 'select', options: lifecycleOptions(row.status) },
            capacityField,
            registrationField,
          ]}
          initial={{
            status: row.status,
            capacity: row.capacity === null ? '' : String(row.capacity),
            registrationStatus: row.registrationStatus,
          }}
          submitLabel="Save course offering"
          onSubmit={async (values) => {
            await updateCourseOffering(row.courseOfferingId, {
              status: values.status as LifecycleStatus,
              capacity: values.capacity.trim() === '' ? null : Number(values.capacity),
              registrationStatus: values.registrationStatus as RegistrationStatus,
            })
            done(`${row.courseCode} in semester ${row.semesterNumber} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}
