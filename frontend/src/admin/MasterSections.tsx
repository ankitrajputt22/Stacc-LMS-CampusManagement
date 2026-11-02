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
  makeAcademicSessionCurrent,
  updateAcademicSession,
  updateCourse,
  updateDepartment,
  updateProgram,
  updateSemester,
  updateSemesterRegistration,
} from '../api/adminAcademicApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { creditsText } from '../courses/credits'
import { formatDateTime, localTimeZoneName } from '../courses/dates'
import type {
  AdminAcademicSession,
  AdminCourse,
  AdminDepartment,
  AdminProgram,
  AdminSemester,
  CourseType,
  DegreeType,
  LifecycleStatus,
} from '../types/admin'
import { Badge, Button } from '../ui'
import { courseTypeOptions, courseTypeWord, degreeOptions, degreeWord, optional } from './catalogText'
import { type FieldOption, type FieldSpec, MasterForm, type Values } from './MasterForm'
import { CodeName, MasterStatus } from './MasterParts'
import { MasterSection } from './MasterSection'
import { localValue, moment, registrationInOrder } from './registrationPeriod'
import { statusAction } from './statusAction'
import { lifecycleOptions } from './masterStatus'

// The limits the backend enforces for each master, so the forms can say so before sending.
const UPPERCASE = 'Stored in uppercase. It cannot be changed later.'

function departmentOptions(departments: AdminDepartment[], activeOnly = false): FieldOption[] {
  return departments
    .filter((department) => !activeOnly || department.status === 'ACTIVE')
    .map((department) => ({
      value: String(department.id),
      label: `${department.code} · ${department.name}${department.status === 'INACTIVE' ? ' (inactive)' : ''}`,
    }))
}

/** Department filter choices, by code. */
function departmentFilter<T extends { departmentId: number }>(departments: AdminDepartment[] | null) {
  return {
    label: 'Department',
    all: 'All departments',
    options: (departments ?? []).map((department) => ({ value: String(department.id), label: department.code })),
    value: (row: T) => String(row.departmentId),
  }
}

export function DepartmentsSection() {
  const request = useApiQuery(queryKeys.admin.departments(), getDepartments)
  const nameField: FieldSpec = { name: 'name', label: 'Name', kind: 'text', maxLength: 150, hint: 'Unique across the college.' }
  const descriptionField: FieldSpec = {
    name: 'description', label: 'Description (optional)', kind: 'textarea', maxLength: 1000, optional: true,
    hint: 'Plain text, up to 1000 characters.',
  }

  return (
    <MasterSection<AdminDepartment>
      title="Departments"
      singular="department"
      intro="The college’s academic departments. Programs and courses belong to a department."
      request={request}
      rowKey={(row) => row.id}
      rowName={(row) => `department ${row.code}`}
      searchText={(row) => `${row.code} ${row.name}`}
      rowStatus={(row) => row.status}
      columns={[
        { header: 'Code', primary: true, cell: (row) => row.code },
        {
          header: 'Name',
          cell: (row) => (
            <>
              {row.name}
              {row.description && <span className="mt-0.5 block text-sm text-ink-muted">{row.description}</span>}
            </>
          ),
        },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      rowActions={(row, actions) =>
        statusAction(actions, {
          name: `department ${row.code}`,
          active: row.status === 'ACTIVE',
          stops: 'it takes no new programs or courses',
          focusKey: `status-${row.id}`,
          save: async (status) => {
            await updateDepartment(row.id, { name: row.name, description: row.description, status })
          },
        })
      }
      renderCreate={(done, cancel) => (
        <MasterForm
          heading="New department"
          fields={[{ name: 'code', label: 'Code', kind: 'text', maxLength: 20, hint: UPPERCASE }, nameField, descriptionField]}
          initial={{ code: '', name: '', description: '' }}
          submitLabel="Save department"
          onSubmit={async (values) => {
            const saved = await createDepartment({
              code: values.code.trim(),
              name: values.name.trim(),
              description: optional(values.description),
            })
            done(`Department ${saved.code} added.`)
          }}
          onCancel={cancel}
        />
      )}
      renderEdit={(row, done, cancel) => (
        <MasterForm
          heading={`Edit department ${row.code}`}
          note="The code cannot be changed. Use Activate or Deactivate in the list to change the status."
          fields={[nameField, descriptionField]}
          initial={{ name: row.name, description: row.description ?? '' }}
          submitLabel="Save department"
          onSubmit={async (values) => {
            await updateDepartment(row.id, {
              name: values.name.trim(),
              description: optional(values.description),
              status: row.status,
            })
            done(`Department ${row.code} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}

export function ProgramsSection() {
  const request = useApiQuery(queryKeys.admin.programs(), getPrograms)
  const departments = useApiQuery(queryKeys.admin.departments(), getDepartments)
  const departmentList = departments.status === 'loaded' ? departments.data : null
  const activeDepartments = departmentList?.filter((department) => department.status === 'ACTIVE') ?? null
  const degreeField: FieldSpec = { name: 'degreeType', label: 'Degree', kind: 'select', options: degreeOptions }
  const editFields: FieldSpec[] = [
    { name: 'name', label: 'Name', kind: 'text', maxLength: 150, hint: 'Unique within the department.' },
    degreeField,
    { name: 'durationSemesters', label: 'Duration (semesters)', kind: 'integer', min: 1, max: 20,
      hint: 'From 1 to 20, and not below a semester the program already has.' },
    { name: 'maxCreditsPerSemester', label: 'Credit limit per semester (optional)', kind: 'decimal', above: 0, max: 60,
      optional: true, hint: 'The most credits a student may register for in one semester, up to 60. Leave empty for no limit.' },
  ]
  const limit = (values: Values) =>
    values.maxCreditsPerSemester.trim() === '' ? null : Number(values.maxCreditsPerSemester)

  return (
    <MasterSection<AdminProgram>
      title="Programs"
      singular="program"
      intro="The programs each department offers, with their degree and normal length in semesters."
      request={request}
      blockedReason={
        activeDepartments === null ? 'Loading departments…' : activeDepartments.length === 0 ? 'Add or activate a department first.' : null
      }
      rowKey={(row) => row.id}
      rowName={(row) => `program ${row.code}`}
      searchText={(row) => `${row.code} ${row.name} ${row.departmentCode} ${row.departmentName}`}
      rowStatus={(row) => row.status}
      filters={[
        departmentFilter<AdminProgram>(departmentList),
        { label: 'Degree', all: 'All degrees', options: degreeOptions, value: (row) => row.degreeType },
      ]}
      columns={[
        { header: 'Program', primary: true, cell: (row) => <CodeName code={row.code} name={row.name} /> },
        { header: 'Department', cell: (row) => row.departmentCode },
        { header: 'Degree', cell: (row) => degreeWord(row.degreeType) },
        { header: 'Duration', cell: (row) => `${row.durationSemesters} ${row.durationSemesters === 1 ? 'semester' : 'semesters'}` },
        {
          header: 'Credit limit',
          cell: (row) =>
            row.maxCreditsPerSemester === null ? <span className="text-ink-muted">No limit</span> : creditsText(row.maxCreditsPerSemester),
        },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      rowActions={(row, actions) =>
        statusAction(actions, {
          name: `program ${row.code}`,
          active: row.status === 'ACTIVE',
          stops: 'it takes no new semesters',
          focusKey: `status-${row.id}`,
          save: async (status) => {
            await updateProgram(row.id, {
              name: row.name, degreeType: row.degreeType, durationSemesters: row.durationSemesters, status,
              maxCreditsPerSemester: row.maxCreditsPerSemester,
            })
          },
        })
      }
      renderCreate={(done, cancel) => (
        <MasterForm
          heading="New program"
          fields={[
            { name: 'departmentId', label: 'Department', kind: 'select', choose: 'Choose a department',
              options: departmentOptions(departmentList ?? [], true), hint: 'Active departments only. It cannot be changed later.' },
            { name: 'code', label: 'Code', kind: 'text', maxLength: 30, hint: `${UPPERCASE} Unique within the department.` },
            ...editFields,
          ]}
          initial={{ departmentId: '', code: '', name: '', degreeType: 'UNDERGRADUATE', durationSemesters: '', maxCreditsPerSemester: '' }}
          submitLabel="Save program"
          onSubmit={async (values) => {
            const saved = await createProgram({
              departmentId: Number(values.departmentId),
              code: values.code.trim(),
              name: values.name.trim(),
              degreeType: values.degreeType as DegreeType,
              durationSemesters: Number(values.durationSemesters),
              maxCreditsPerSemester: limit(values),
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
          initial={{
            name: row.name,
            degreeType: row.degreeType,
            durationSemesters: String(row.durationSemesters),
            maxCreditsPerSemester: row.maxCreditsPerSemester === null ? '' : String(row.maxCreditsPerSemester),
          }}
          submitLabel="Save program"
          onSubmit={async (values) => {
            await updateProgram(row.id, {
              name: values.name.trim(),
              degreeType: values.degreeType as DegreeType,
              durationSemesters: Number(values.durationSemesters),
              maxCreditsPerSemester: limit(values),
              status: row.status,
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
  const request = useApiQuery(queryKeys.admin.sessions(), getAcademicSessions)
  const currentSession = request.status === 'loaded' ? request.data.find((session) => session.current) ?? null : null
  const dateFields: FieldSpec[] = [
    { name: 'startDate', label: 'Start date', kind: 'date' },
    { name: 'endDate', label: 'End date', kind: 'date', hint: 'After the start date.' },
  ]

  return (
    <MasterSection<AdminAcademicSession>
      title="Academic sessions"
      singular="academic session"
      intro="College-wide academic periods, the latest first. A session moves from planned to active to closed, never back. One session is the current one."
      request={request}
      rowKey={(row) => row.id}
      rowName={(row) => `academic session ${row.code}`}
      searchText={(row) => row.code}
      rowStatus={(row) => row.status}
      columns={[
        {
          header: 'Session',
          primary: true,
          cell: (row) => (
            <span className="inline-flex flex-wrap items-center gap-2">
              {row.code}
              {row.current && <Badge tone="brand">Current</Badge>}
            </span>
          ),
        },
        { header: 'Start date', cell: (row) => row.startDate },
        { header: 'End date', cell: (row) => row.endDate },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      rowActions={(row, actions) =>
        !row.current &&
        row.status !== 'CLOSED' && (
          <Button
            variant="secondary"
            size="sm"
            data-focus-key={`current-${row.id}`}
            onClick={() =>
              actions.confirm(
                {
                  question: `Make ${row.code} the current session?`,
                  detail: currentSession
                    ? `${currentSession.code} stops being the current session. Only one session is current at a time.`
                    : 'Only one session is current at a time.',
                  confirmLabel: 'Set as current',
                  busyLabel: 'Saving…',
                  run: async () => {
                    await makeAcademicSessionCurrent(row.id)
                    return `${row.code} is now the current session.`
                  },
                },
                `current-${row.id}`,
              )
            }
          >
            Set as current<span className="sr-only"> {row.code}</span>
          </Button>
        )
      }
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
          note={`The code cannot be changed.${row.current ? ' Closing the current session also stops it being current.' : ''}`}
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

const registrationFields: FieldSpec[] = [
  { name: 'registrationOpensAt', label: 'Registration opens (optional)', kind: 'datetime', optional: true,
    hint: 'When students may start registering for this semester’s offerings.' },
  { name: 'registrationClosesAt', label: 'Registration closes (optional)', kind: 'datetime', optional: true,
    hint: 'After it opens. Both times or neither.' },
  { name: 'dropDeadline', label: 'Drop deadline (optional)', kind: 'datetime', optional: true,
    hint: 'The last moment a student may drop a course. Without it, students cannot drop.' },
]

/** "1 Jul 2026, 9:00 am – 15 Jul 2026, 5:00 pm" with the drop deadline under it, or Not set. */
function RegistrationPeriod({ semester }: { semester: AdminSemester }) {
  if (semester.registrationOpensAt === null || semester.registrationClosesAt === null) {
    return <span className="text-ink-muted">Not set</span>
  }
  return (
    <>
      {formatDateTime(semester.registrationOpensAt)} – {formatDateTime(semester.registrationClosesAt)}
      <span className="block text-sm text-ink-muted">
        {semester.dropDeadline === null ? 'No dropping' : `Drop until ${formatDateTime(semester.dropDeadline)}`}
      </span>
    </>
  )
}

export function SemestersSection() {
  const request = useApiQuery(queryKeys.admin.semesters(), getSemesters)
  const programs = useApiQuery(queryKeys.admin.programs(), getPrograms)
  const sessions = useApiQuery(queryKeys.admin.sessions(), getAcademicSessions)
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
      searchText={(row) => `${row.academicSessionCode} ${row.programCode} ${row.programName} semester ${row.semesterNumber}`}
      rowStatus={(row) => row.status}
      columns={[
        { header: 'Session', cell: (row) => <span className="font-medium">{row.academicSessionCode}</span> },
        { header: 'Program', cell: (row) => <CodeName code={row.programCode} name={row.programName} /> },
        { header: 'Semester', cell: (row) => `Semester ${row.semesterNumber}` },
        { header: 'Registration', cell: (row) => <RegistrationPeriod semester={row} /> },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      renderCreate={(done, cancel) => (
        <MasterForm
          heading="New semester"
          note="Program, session, and number cannot be changed later."
          fields={[
            { name: 'programId', label: 'Program', kind: 'select', choose: 'Choose a program',
              hint: 'Active programs only.',
              options: (programList ?? [])
                .filter((program) => program.status === 'ACTIVE')
                .map((program) => ({
                  value: String(program.id),
                  label: `${program.code} · ${program.name} (${program.departmentCode}, ${program.durationSemesters} semesters)`,
                })) },
            { name: 'academicSessionId', label: 'Academic session', kind: 'select', choose: 'Choose an academic session',
              hint: 'Closed sessions are not listed.',
              options: (sessionList ?? [])
                .filter((session) => session.status !== 'CLOSED')
                .map((session) => ({ value: String(session.id), label: `${session.code}${session.current ? ' (current)' : ''}` })) },
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
          note={
            row.status === 'CLOSED'
              ? 'A closed semester keeps its registration period.'
              : `Program, session, and number cannot change. Times are in your time zone (${localTimeZoneName()}). Leave the registration times empty to close registration.`
          }
          fields={[
            { name: 'status', label: 'Status', kind: 'select', options: lifecycleOptions(row.status) },
            ...(row.status === 'CLOSED' ? [] : registrationFields),
          ]}
          initial={{
            status: row.status,
            registrationOpensAt: localValue(row.registrationOpensAt),
            registrationClosesAt: localValue(row.registrationClosesAt),
            dropDeadline: localValue(row.dropDeadline),
          }}
          validate={row.status === 'CLOSED' ? undefined : registrationInOrder}
          submitLabel="Save semester"
          onSubmit={async (values) => {
            if (row.status !== 'CLOSED') {
              const period = {
                registrationOpensAt: moment(values.registrationOpensAt),
                registrationClosesAt: moment(values.registrationClosesAt),
                dropDeadline: moment(values.dropDeadline),
              }
              if (
                period.registrationOpensAt !== moment(localValue(row.registrationOpensAt)) ||
                period.registrationClosesAt !== moment(localValue(row.registrationClosesAt)) ||
                period.dropDeadline !== moment(localValue(row.dropDeadline))
              ) {
                await updateSemesterRegistration(row.id, period)
              }
            }
            if (values.status !== row.status) {
              await updateSemester(row.id, { status: values.status as LifecycleStatus })
            }
            done(`Semester ${row.semesterNumber} of ${row.programCode} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}

export function CoursesSection() {
  const request = useApiQuery(queryKeys.admin.courses(), getCourses)
  const departments = useApiQuery(queryKeys.admin.departments(), getDepartments)
  const departmentList = departments.status === 'loaded' ? departments.data : null
  const activeDepartments = departmentList?.filter((department) => department.status === 'ACTIVE') ?? null
  const editFields: FieldSpec[] = [
    { name: 'name', label: 'Name', kind: 'text', maxLength: 200 },
    { name: 'shortName', label: 'Short name (optional)', kind: 'text', maxLength: 20, optional: true, hint: 'Such as DBMS.' },
    { name: 'courseType', label: 'Type', kind: 'select', options: courseTypeOptions },
    { name: 'credits', label: 'Credits', kind: 'decimal', above: 0, max: 20, hint: 'Above 0 and at most 20, such as 4 or 1.5.' },
    { name: 'description', label: 'Description (optional)', kind: 'textarea', maxLength: 2000, optional: true,
      hint: 'Plain text, up to 2000 characters.' },
  ]

  return (
    <MasterSection<AdminCourse>
      title="Courses"
      singular="course"
      intro="The permanent course catalog. A course has an owning department; when it is taught in a semester is managed under Course Offerings."
      request={request}
      blockedReason={
        activeDepartments === null ? 'Loading departments…' : activeDepartments.length === 0 ? 'Add or activate a department first.' : null
      }
      rowKey={(row) => row.id}
      rowName={(row) => `course ${row.code}`}
      searchText={(row) => `${row.code} ${row.name} ${row.shortName ?? ''} ${row.departmentCode}`}
      rowStatus={(row) => row.status}
      filters={[
        departmentFilter<AdminCourse>(departmentList),
        { label: 'Type', all: 'All types', options: courseTypeOptions, value: (row) => row.courseType },
      ]}
      columns={[
        {
          header: 'Course',
          primary: true,
          cell: (row) => (
            <>
              <CodeName code={row.code} name={row.name} />
              {row.shortName && <span className="ml-1 text-ink-muted">({row.shortName})</span>}
            </>
          ),
        },
        { header: 'Department', cell: (row) => row.departmentCode },
        { header: 'Type', cell: (row) => courseTypeWord(row.courseType) },
        { header: 'Credits', cell: (row) => creditsText(row.credits) },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      rowActions={(row, actions) =>
        statusAction(actions, {
          name: `course ${row.code}`,
          active: row.status === 'ACTIVE',
          stops: 'it cannot be offered in a new semester',
          focusKey: `status-${row.id}`,
          save: async (status) => {
            await updateCourse(row.id, {
              name: row.name, credits: row.credits, status,
              shortName: row.shortName, description: row.description, courseType: row.courseType,
            })
          },
        })
      }
      renderCreate={(done, cancel) => (
        <MasterForm
          heading="New course"
          fields={[
            { name: 'departmentId', label: 'Department', kind: 'select', choose: 'Choose a department',
              options: departmentOptions(departmentList ?? [], true),
              hint: 'The owning department, active ones only. It cannot be changed later.' },
            { name: 'code', label: 'Code', kind: 'text', maxLength: 30, hint: `${UPPERCASE} Unique across the college.` },
            ...editFields,
          ]}
          initial={{ departmentId: '', code: '', name: '', shortName: '', courseType: 'CORE', credits: '', description: '' }}
          submitLabel="Save course"
          onSubmit={async (values) => {
            const saved = await createCourse({
              departmentId: Number(values.departmentId),
              code: values.code.trim(),
              name: values.name.trim(),
              shortName: optional(values.shortName),
              description: optional(values.description),
              courseType: values.courseType as CourseType,
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
          note={`Department ${row.departmentCode}. The department and code cannot be changed. Use Activate or Deactivate in the list to change the status.`}
          fields={editFields}
          initial={{
            name: row.name,
            shortName: row.shortName ?? '',
            courseType: row.courseType,
            credits: String(row.credits),
            description: row.description ?? '',
          }}
          submitLabel="Save course"
          onSubmit={async (values) => {
            await updateCourse(row.id, {
              name: values.name.trim(),
              shortName: optional(values.shortName),
              description: optional(values.description),
              courseType: values.courseType as CourseType,
              credits: Number(values.credits.trim()),
              status: row.status,
            })
            done(`Course ${row.code} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}
