import { type FormEvent, useState } from 'react'
import { getDepartments } from '../api/adminAcademicApi'
import {
  searchFacultyProfiles,
  searchStudentProfiles,
  updateFacultyProfile,
  updateStudentProfile,
} from '../api/academicProfileApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import type { FacultyProfileSummary, StudentProfileSummary } from '../types/academicProfile'
import { Button, TextField } from '../ui'
import { optional } from './catalogText'
import { MasterForm } from './MasterForm'
import { MasterStatus } from './MasterParts'
import { MasterSection } from './MasterSection'
import { statusAction } from './statusAction'

const PROFILE_DENIED = 'You don’t have access to academic profiles.'

/** A search box that asks the backend only when submitted, for lists too long to load whole. */
function ServerSearch({ label, hint, onSearch }: { label: string; hint: string; onSearch: (query: string) => void }) {
  const [text, setText] = useState('')
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSearch(text.trim())
  }
  return (
    <form role="search" onSubmit={submit} className="flex flex-col gap-3 sm:col-span-2 sm:flex-row sm:items-start">
      <TextField
        className="min-w-0 flex-1"
        label={label}
        hint={hint}
        type="search"
        value={text}
        maxLength={100}
        autoComplete="off"
        onChange={(event) => setText(event.target.value)}
      />
      <Button type="submit" variant="secondary" className="sm:mt-7">
        Find
      </Button>
    </form>
  )
}

/** "2026-27 · Semester 3 · Section A", or that the student is not enrolled now. */
function placement(row: StudentProfileSummary) {
  if (row.current === null) {
    return <span className="text-ink-muted">Not enrolled now</span>
  }
  return `${row.current.academicSessionCode} · Sem ${row.current.semesterNumber}${
    row.current.sectionCode ? ` · Section ${row.current.sectionCode}` : ''
  }`
}

/**
 * Students' official academic mapping: program and department, admission session, roll number, and where they study
 * now, worked out from their enrolled semester enrollment. Admins correct the roll number and the profile status here;
 * enrollment and sections are managed under Enrollments, and accounts are not changed here.
 */
export function StudentProfilesSection() {
  const [query, setQuery] = useState('')
  const request = useApiQuery(queryKeys.admin.studentProfiles(query), (signal) => searchStudentProfiles(query, signal))

  return (
    <MasterSection<StudentProfileSummary>
      title="Students"
      singular="student"
      intro="Each student’s program, department, admission session, roll number, and where they study now. Where they study comes from their official enrollment; change it under Enrollments."
      request={request}
      deniedTitle={PROFILE_DENIED}
      emptyTitle={query === '' ? 'No students yet.' : 'No matching students.'}
      toolbar={
        <ServerSearch
          label="Find students"
          hint="All or part of a student ID or roll number. Empty lists the first 50."
          onSearch={setQuery}
        />
      }
      clientSearch={false}
      rowKey={(row) => row.studentProfileId}
      rowName={(row) => `student ${row.loginId}`}
      searchText={(row) => `${row.loginId} ${row.name ?? ''} ${row.rollNumber ?? ''} ${row.programCode}`}
      rowStatus={(row) => row.status}
      columns={[
        {
          header: 'Student',
          primary: true,
          cell: (row) => (
            <>
              {row.name ?? row.loginId}
              {row.name && <span className="block text-sm font-normal text-ink-muted">{row.loginId}</span>}
            </>
          ),
        },
        { header: 'Roll number', cell: (row) => row.rollNumber ?? <span className="text-ink-muted">—</span> },
        { header: 'Program', cell: (row) => `${row.programCode} · ${row.departmentCode}` },
        { header: 'Admitted', cell: (row) => row.admissionSessionCode },
        { header: 'Now', cell: placement },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      rowActions={(row, actions) =>
        statusAction(actions, {
          name: `student ${row.loginId}`,
          active: row.status === 'ACTIVE',
          stops: 'they cannot be enrolled and lose access to their LMS courses',
          focusKey: `status-${row.studentProfileId}`,
          save: async (status) => {
            await updateStudentProfile(row.studentProfileId, { rollNumber: row.rollNumber, status })
          },
        })
      }
      renderEdit={(row, done, cancel) => (
        <MasterForm
          heading={`Edit student ${row.loginId}`}
          note="Only the roll number is edited here. The program, admission session, and account stay as they are."
          deniedMessage={PROFILE_DENIED}
          fields={[
            { name: 'rollNumber', label: 'Roll number (optional)', kind: 'text', maxLength: 30, optional: true,
              hint: 'Stored in uppercase, unique across the college. Leave empty for none.' },
          ]}
          initial={{ rollNumber: row.rollNumber ?? '' }}
          submitLabel="Save student"
          onSubmit={async (values) => {
            await updateStudentProfile(row.studentProfileId, { rollNumber: optional(values.rollNumber), status: row.status })
            done(`Student ${row.loginId} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}

/**
 * Faculty members' official academic mapping: their primary department, profile status, and how many offerings they
 * teach now. The department never limits what they teach; that comes only from faculty assignments.
 */
export function FacultyProfilesSection() {
  const [query, setQuery] = useState('')
  const request = useApiQuery(queryKeys.admin.facultyProfiles(query), (signal) => searchFacultyProfiles(query, signal))
  const departments = useApiQuery(queryKeys.admin.departments(), getDepartments)
  const departmentList = departments.status === 'loaded' ? departments.data : []

  return (
    <MasterSection<FacultyProfileSummary>
      title="Faculty"
      singular="faculty member"
      intro="Each faculty member’s primary department and how many course offerings they teach now. Who teaches what is managed under Faculty Assignments."
      request={request}
      deniedTitle={PROFILE_DENIED}
      emptyTitle={query === '' ? 'No faculty members yet.' : 'No matching faculty members.'}
      toolbar={
        <ServerSearch label="Find faculty" hint="All or part of an employee ID. Empty lists the first 50." onSearch={setQuery} />
      }
      clientSearch={false}
      rowKey={(row) => row.facultyProfileId}
      rowName={(row) => `faculty member ${row.loginId}`}
      searchText={(row) => `${row.loginId} ${row.name ?? ''} ${row.departmentCode}`}
      rowStatus={(row) => row.status}
      columns={[
        {
          header: 'Faculty',
          primary: true,
          cell: (row) => (
            <>
              {row.name ?? row.loginId}
              {row.name && <span className="block text-sm font-normal text-ink-muted">{row.loginId}</span>}
            </>
          ),
        },
        { header: 'Department', cell: (row) => `${row.departmentCode} · ${row.departmentName}` },
        {
          header: 'Teaching now',
          align: 'right',
          cell: (row) => `${row.activeTeachingCount} ${row.activeTeachingCount === 1 ? 'offering' : 'offerings'}`,
        },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      rowActions={(row, actions) =>
        statusAction(actions, {
          name: `faculty member ${row.loginId}`,
          active: row.status === 'ACTIVE',
          stops: 'they cannot be assigned and lose teaching access to their offerings',
          focusKey: `status-${row.facultyProfileId}`,
          save: async (status) => {
            await updateFacultyProfile(row.facultyProfileId, { departmentId: row.departmentId, status })
          },
        })
      }
      renderEdit={(row, done, cancel) => (
        <MasterForm
          heading={`Edit faculty member ${row.loginId}`}
          note="The primary department is where they belong. It never limits what they teach."
          deniedMessage={PROFILE_DENIED}
          fields={[
            { name: 'departmentId', label: 'Primary department', kind: 'select', hint: 'Active departments only.',
              options: departmentList
                .filter((department) => department.status === 'ACTIVE' || department.id === row.departmentId)
                .map((department) => ({ value: String(department.id), label: `${department.code} · ${department.name}` })) },
          ]}
          initial={{ departmentId: String(row.departmentId) }}
          submitLabel="Save faculty member"
          onSubmit={async (values) => {
            await updateFacultyProfile(row.facultyProfileId, { departmentId: Number(values.departmentId), status: row.status })
            done(`Faculty member ${row.loginId} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}
