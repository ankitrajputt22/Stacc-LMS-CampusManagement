import {
  createAcademicSession,
  createCourse,
  createDepartment,
  createProgram,
  createSemester,
  getAcademicSessions,
  getCourses,
  getDepartments,
  getPrograms,
  getSemesters,
  updateAcademicSession,
  updateCourse,
  updateDepartment,
  updateProgram,
  updateSemester,
} from '../api/adminAcademicApi'
import { useApiRequest } from '../api/useApiRequest'
import { creditsText } from '../courses/credits'
import type {
  ActiveStatus,
  AdminAcademicSession,
  AdminCourse,
  AdminDepartment,
  AdminProgram,
  AdminSemester,
  LifecycleStatus,
} from '../types/admin'
import { type FieldOption, type FieldSpec, MasterForm, type Values } from './MasterForm'
import { CodeName, MasterStatus } from './MasterList'
import { MasterSection } from './MasterSection'
import { lifecycleOptions, statusOptions } from './masterStatus'

// The limits the backend enforces for each master, so the forms can say so before sending.
const ACTIVE_STATUSES = statusOptions(['ACTIVE', 'INACTIVE'])
const UPPERCASE = 'Stored in uppercase. It cannot be changed later.'

function departmentOptions(departments: AdminDepartment[]): FieldOption[] {
  return departments.map((department) => ({
    value: String(department.id),
    label: `${department.code} · ${department.name}${department.status === 'INACTIVE' ? ' (inactive)' : ''}`,
  }))
}


export function DepartmentsSection() {
  const request = useApiRequest(getDepartments)
  const nameField: FieldSpec = { name: 'name', label: 'Name', kind: 'text', maxLength: 150, hint: 'Unique across the college.' }

  return (
    <MasterSection<AdminDepartment>
      title="Departments"
      singular="department"
      intro="The college’s academic departments. Programs and courses belong to a department."
      request={request}
      rowKey={(row) => row.id}
      rowName={(row) => `department ${row.code}`}
      columns={[
        { header: 'Code', cell: (row) => <span className="font-medium">{row.code}</span> },
        { header: 'Name', cell: (row) => row.name },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      renderCreate={(done, cancel) => (
        <MasterForm
          heading="New department"
          fields={[{ name: 'code', label: 'Code', kind: 'text', maxLength: 20, hint: UPPERCASE }, nameField]}
          initial={{ code: '', name: '' }}
          submitLabel="Save department"
          onSubmit={async (values) => {
            const saved = await createDepartment({ code: values.code.trim(), name: values.name.trim() })
            done(`Department ${saved.code} added.`)
          }}
          onCancel={cancel}
        />
      )}
      renderEdit={(row, done, cancel) => (
        <MasterForm
          heading={`Edit department ${row.code}`}
          note="The code cannot be changed."
          fields={[nameField, { name: 'status', label: 'Status', kind: 'select', options: ACTIVE_STATUSES }]}
          initial={{ name: row.name, status: row.status }}
          submitLabel="Save department"
          onSubmit={async (values) => {
            await updateDepartment(row.id, { name: values.name.trim(), status: values.status as ActiveStatus })
            done(`Department ${row.code} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}

export function ProgramsSection() {
  const request = useApiRequest(getPrograms)
  const departments = useApiRequest(getDepartments)
  const departmentList = departments.status === 'loaded' ? departments.data : null
  const editFields: FieldSpec[] = [
    { name: 'name', label: 'Name', kind: 'text', maxLength: 150, hint: 'Unique within the department.' },
    { name: 'durationSemesters', label: 'Duration (semesters)', kind: 'integer', min: 1, max: 20,
      hint: 'From 1 to 20, and not below a semester the program already has.' },
    { name: 'status', label: 'Status', kind: 'select', options: ACTIVE_STATUSES },
  ]

  return (
    <MasterSection<AdminProgram>
      title="Programs"
      singular="program"
      intro="The programs each department offers, with their normal length in semesters."
      request={request}
      blockedReason={departmentList === null ? 'Loading departments…' : departmentList.length === 0 ? 'Add a department first.' : null}
      rowKey={(row) => row.id}
      rowName={(row) => `program ${row.code}`}
      columns={[
        { header: 'Program', cell: (row) => <CodeName code={row.code} name={row.name} /> },
        { header: 'Department', cell: (row) => <CodeName code={row.departmentCode} name={row.departmentName} /> },
        { header: 'Duration', cell: (row) => `${row.durationSemesters} ${row.durationSemesters === 1 ? 'semester' : 'semesters'}` },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      renderCreate={(done, cancel) => (
        <MasterForm
          heading="New program"
          fields={[
            { name: 'departmentId', label: 'Department', kind: 'select', choose: 'Choose a department',
              options: departmentOptions(departmentList ?? []), hint: 'It cannot be changed later.' },
            { name: 'code', label: 'Code', kind: 'text', maxLength: 30, hint: `${UPPERCASE} Unique within the department.` },
            ...editFields.slice(0, 2),
          ]}
          initial={{ departmentId: '', code: '', name: '', durationSemesters: '' }}
          submitLabel="Save program"
          onSubmit={async (values) => {
            const saved = await createProgram({
              departmentId: Number(values.departmentId),
              code: values.code.trim(),
              name: values.name.trim(),
              durationSemesters: Number(values.durationSemesters),
            })
            done(`Program ${saved.code} added.`)
          }}
          onCancel={cancel}
        />
      )}
      renderEdit={(row, done, cancel) => (
        <MasterForm
          heading={`Edit program ${row.code}`}
          note={`Department ${row.departmentCode}. The department and code cannot be changed.`}
          fields={editFields}
          initial={{ name: row.name, durationSemesters: String(row.durationSemesters), status: row.status }}
          submitLabel="Save program"
          onSubmit={async (values) => {
            await updateProgram(row.id, {
              name: values.name.trim(),
              durationSemesters: Number(values.durationSemesters),
              status: values.status as ActiveStatus,
            })
            done(`Program ${row.code} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}

function datesInOrder(values: Values) {
  return values.startDate && values.endDate && values.endDate <= values.startDate
    ? { endDate: 'The end date must be after the start date.' }
    : {}
}

export function AcademicSessionsSection() {
  const request = useApiRequest(getAcademicSessions)
  const dateFields: FieldSpec[] = [
    { name: 'startDate', label: 'Start date', kind: 'date' },
    { name: 'endDate', label: 'End date', kind: 'date', hint: 'After the start date.' },
  ]

  return (
    <MasterSection<AdminAcademicSession>
      title="Academic sessions"
      singular="academic session"
      intro="College-wide academic periods, the latest first. A session moves from planned to active to closed, never back."
      request={request}
      rowKey={(row) => row.id}
      rowName={(row) => `academic session ${row.code}`}
      columns={[
        { header: 'Code', cell: (row) => <span className="font-medium">{row.code}</span> },
        { header: 'Dates', cell: (row) => `${row.startDate} to ${row.endDate}` },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      renderCreate={(done, cancel) => (
        <MasterForm
          heading="New academic session"
          fields={[
            { name: 'code', label: 'Code', kind: 'text', maxLength: 20,
              hint: 'The college’s own name, such as 2026-27. It cannot be changed later.' },
            ...dateFields,
          ]}
          initial={{ code: '', startDate: '', endDate: '' }}
          submitLabel="Save academic session"
          validate={datesInOrder}
          onSubmit={async (values) => {
            const saved = await createAcademicSession({
              code: values.code.trim(),
              startDate: values.startDate,
              endDate: values.endDate,
            })
            done(`Academic session ${saved.code} added.`)
          }}
          onCancel={cancel}
        />
      )}
      renderEdit={(row, done, cancel) => (
        <MasterForm
          heading={`Edit academic session ${row.code}`}
          note="The code cannot be changed."
          fields={[...dateFields, { name: 'status', label: 'Status', kind: 'select', options: lifecycleOptions(row.status) }]}
          initial={{ startDate: row.startDate, endDate: row.endDate, status: row.status }}
          submitLabel="Save academic session"
          validate={datesInOrder}
          onSubmit={async (values) => {
            await updateAcademicSession(row.id, {
              startDate: values.startDate,
              endDate: values.endDate,
              status: values.status as LifecycleStatus,
            })
            done(`Academic session ${row.code} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}

export function SemestersSection() {
  const request = useApiRequest(getSemesters)
  const programs = useApiRequest(getPrograms)
  const sessions = useApiRequest(getAcademicSessions)
  const programList = programs.status === 'loaded' ? programs.data : null
  const sessionList = sessions.status === 'loaded' ? sessions.data : null
  const blocked =
    programList === null || sessionList === null
      ? 'Loading programs and academic sessions…'
      : programList.length === 0 || sessionList.length === 0
        ? 'Add a program and an academic session first.'
        : null

  return (
    <MasterSection<AdminSemester>
      title="Semesters"
      singular="semester"
      intro="Each numbered semester of a program in an academic session. Program, session, and number cannot change; only the status moves forward."
      request={request}
      blockedReason={blocked}
      rowKey={(row) => row.id}
      rowName={(row) => `semester ${row.semesterNumber} of ${row.programCode} in ${row.academicSessionCode}`}
      columns={[
        { header: 'Session', cell: (row) => <span className="font-medium">{row.academicSessionCode}</span> },
        { header: 'Program', cell: (row) => <CodeName code={row.programCode} name={row.programName} /> },
        { header: 'Semester', cell: (row) => `Semester ${row.semesterNumber}` },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      renderCreate={(done, cancel) => (
        <MasterForm
          heading="New semester"
          note="Program, session, and number cannot be changed later."
          fields={[
            { name: 'programId', label: 'Program', kind: 'select', choose: 'Choose a program',
              options: (programList ?? []).map((program) => ({
                value: String(program.id),
                label: `${program.code} · ${program.name} (${program.departmentCode}, ${program.durationSemesters} semesters${
                  program.status === 'INACTIVE' ? ', inactive' : ''
                })`,
              })) },
            { name: 'academicSessionId', label: 'Academic session', kind: 'select', choose: 'Choose an academic session',
              options: (sessionList ?? []).map((session) => ({ value: String(session.id), label: session.code })) },
            { name: 'semesterNumber', label: 'Semester number', kind: 'integer', min: 1, max: 20,
              hint: 'From 1 up to the program’s duration.' },
          ]}
          initial={{ programId: '', academicSessionId: '', semesterNumber: '' }}
          submitLabel="Save semester"
          validate={(values) => {
            const program = programList?.find((candidate) => String(candidate.id) === values.programId)
            return program && Number(values.semesterNumber) > program.durationSemesters
              ? { semesterNumber: `This program has ${program.durationSemesters} semesters.` }
              : {}
          }}
          onSubmit={async (values) => {
            const saved = await createSemester({
              programId: Number(values.programId),
              academicSessionId: Number(values.academicSessionId),
              semesterNumber: Number(values.semesterNumber),
            })
            done(`Semester ${saved.semesterNumber} of ${saved.programCode} in ${saved.academicSessionCode} added.`)
          }}
          onCancel={cancel}
        />
      )}
      renderEdit={(row, done, cancel) => (
        <MasterForm
          heading={`Edit semester ${row.semesterNumber} of ${row.programCode} in ${row.academicSessionCode}`}
          note="Only the status can change."
          fields={[{ name: 'status', label: 'Status', kind: 'select', options: lifecycleOptions(row.status) }]}
          initial={{ status: row.status }}
          submitLabel="Save semester"
          onSubmit={async (values) => {
            await updateSemester(row.id, { status: values.status as LifecycleStatus })
            done(`Semester ${row.semesterNumber} of ${row.programCode} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}

export function CoursesSection() {
  const request = useApiRequest(getCourses)
  const departments = useApiRequest(getDepartments)
  const departmentList = departments.status === 'loaded' ? departments.data : null
  const editFields: FieldSpec[] = [
    { name: 'name', label: 'Name', kind: 'text', maxLength: 200 },
    { name: 'credits', label: 'Credits', kind: 'decimal', above: 0, max: 20, hint: 'Above 0 and at most 20, such as 4 or 1.5.' },
  ]

  return (
    <MasterSection<AdminCourse>
      title="Courses"
      singular="course"
      intro="The permanent course catalog. A course has an owning department; when it is taught in a semester is managed separately."
      request={request}
      blockedReason={departmentList === null ? 'Loading departments…' : departmentList.length === 0 ? 'Add a department first.' : null}
      rowKey={(row) => row.id}
      rowName={(row) => `course ${row.code}`}
      columns={[
        { header: 'Course', cell: (row) => <CodeName code={row.code} name={row.name} /> },
        { header: 'Department', cell: (row) => row.departmentCode },
        { header: 'Credits', cell: (row) => creditsText(row.credits) },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      renderCreate={(done, cancel) => (
        <MasterForm
          heading="New course"
          fields={[
            { name: 'departmentId', label: 'Department', kind: 'select', choose: 'Choose a department',
              options: departmentOptions(departmentList ?? []), hint: 'The owning department. It cannot be changed later.' },
            { name: 'code', label: 'Code', kind: 'text', maxLength: 30, hint: `${UPPERCASE} Unique across the college.` },
            ...editFields,
          ]}
          initial={{ departmentId: '', code: '', name: '', credits: '' }}
          submitLabel="Save course"
          onSubmit={async (values) => {
            const saved = await createCourse({
              departmentId: Number(values.departmentId),
              code: values.code.trim(),
              name: values.name.trim(),
              credits: Number(values.credits.trim()),
            })
            done(`Course ${saved.code} added.`)
          }}
          onCancel={cancel}
        />
      )}
      renderEdit={(row, done, cancel) => (
        <MasterForm
          heading={`Edit course ${row.code}`}
          note={`Department ${row.departmentCode}. The department and code cannot be changed.`}
          fields={[...editFields, { name: 'status', label: 'Status', kind: 'select', options: ACTIVE_STATUSES }]}
          initial={{ name: row.name, credits: String(row.credits), status: row.status }}
          submitLabel="Save course"
          onSubmit={async (values) => {
            await updateCourse(row.id, {
              name: values.name.trim(),
              credits: Number(values.credits.trim()),
              status: values.status as ActiveStatus,
            })
            done(`Course ${row.code} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}
