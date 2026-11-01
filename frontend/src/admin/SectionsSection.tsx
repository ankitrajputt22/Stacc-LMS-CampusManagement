import { createSection, getSections, getSemesters, updateSection } from '../api/adminAcademicApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import type { AdminSection } from '../types/admin'
import { optional } from './catalogText'
import { semesterLabel } from './enrollmentText'
import { type FieldSpec, MasterForm } from './MasterForm'
import { MasterStatus } from './MasterParts'
import { MasterSection } from './MasterSection'
import { statusAction } from './statusAction'

/** "12 of 60" or "12 enrolled, no limit". */
function placesText(section: AdminSection): string {
  return section.capacity === null
    ? `${section.enrolledCount} enrolled, no limit`
    : `${section.enrolledCount} of ${section.capacity}`
}

/**
 * Student sections (A, B, …) of each semester. A section's semester and code never change; its name, capacity, and
 * status can. Students are placed in sections under Enrollments. Nothing is deleted.
 */
export function SectionsSection() {
  const request = useApiQuery(queryKeys.admin.sections(), (signal) => getSections(undefined, signal))
  const semesters = useApiQuery(queryKeys.admin.semesters(), getSemesters)
  const semesterList = semesters.status === 'loaded' ? semesters.data : null
  const openSemesters = semesterList?.filter((semester) => semester.status !== 'CLOSED') ?? null
  const rows = request.status === 'loaded' ? request.data : []
  const sessions = [...new Map(rows.map((row) => [row.academicSessionId, row.academicSessionCode])).entries()]
  const capacityField: FieldSpec = {
    name: 'capacity', label: 'Capacity (optional)', kind: 'integer', min: 1, max: 1000, optional: true,
    hint: 'The most enrolled students it holds, from 1 to 1000. Leave empty for no limit.',
  }
  const nameField: FieldSpec = {
    name: 'name', label: 'Name (optional)', kind: 'text', maxLength: 60, optional: true, hint: 'Such as Section A.',
  }

  return (
    <MasterSection<AdminSection>
      title="Sections"
      singular="section"
      intro="Groups of students within a semester, such as Section A and B. Students are placed in a section under Enrollments."
      request={request}
      blockedReason={
        openSemesters === null ? 'Loading semesters…' : openSemesters.length === 0 ? 'Add a semester that is not closed first.' : null
      }
      rowKey={(row) => row.id}
      rowName={(row) => `section ${row.code} of ${row.programCode} semester ${row.semesterNumber}, ${row.academicSessionCode}`}
      searchText={(row) => `${row.code} ${row.name ?? ''} ${row.programCode}`}
      rowStatus={(row) => row.status}
      filters={[
        {
          label: 'Session',
          all: 'All sessions',
          options: sessions.map(([id, code]) => ({ value: String(id), label: code })),
          value: (row) => String(row.academicSessionId),
        },
      ]}
      columns={[
        {
          header: 'Section',
          primary: true,
          cell: (row) => (
            <>
              {row.code}
              {row.name && <span className="ml-1 font-normal text-ink-muted">· {row.name}</span>}
            </>
          ),
        },
        { header: 'Semester', cell: (row) => `${row.programCode} · Semester ${row.semesterNumber}` },
        { header: 'Session', cell: (row) => row.academicSessionCode },
        { header: 'Students', cell: placesText },
        { header: 'Status', cell: (row) => <MasterStatus status={row.status} /> },
      ]}
      rowActions={(row, actions) =>
        statusAction(actions, {
          name: `section ${row.code} of ${row.programCode} semester ${row.semesterNumber}`,
          active: row.status === 'ACTIVE',
          stops: 'no more students can be placed in it',
          focusKey: `status-${row.id}`,
          save: async (status) => {
            await updateSection(row.id, { name: row.name, capacity: row.capacity, status })
          },
        })
      }
      renderCreate={(done, cancel) => (
        <MasterForm
          heading="New section"
          note="The semester and code cannot be changed later."
          fields={[
            { name: 'semesterId', label: 'Semester', kind: 'select', choose: 'Choose a semester',
              hint: 'Closed semesters are not listed.',
              options: (openSemesters ?? []).map((semester) => ({ value: String(semester.id), label: semesterLabel(semester) })) },
            { name: 'code', label: 'Code', kind: 'text', maxLength: 10, hint: 'Such as A. Stored in uppercase, unique within the semester.' },
            nameField,
            capacityField,
          ]}
          initial={{ semesterId: '', code: '', name: '', capacity: '' }}
          submitLabel="Save section"
          onSubmit={async (values) => {
            const saved = await createSection({
              semesterId: Number(values.semesterId),
              code: values.code.trim(),
              name: optional(values.name),
              capacity: values.capacity.trim() === '' ? null : Number(values.capacity),
            })
            done(`Section ${saved.code} of ${saved.programCode} semester ${saved.semesterNumber} added.`)
          }}
          onCancel={cancel}
        />
      )}
      renderEdit={(row, done, cancel) => (
        <MasterForm
          heading={`Edit section ${row.code} of ${row.programCode} semester ${row.semesterNumber}, ${row.academicSessionCode}`}
          note={`The semester and code cannot be changed. ${row.enrolledCount} students are enrolled in it now, so the capacity cannot be lower.`}
          fields={[nameField, capacityField]}
          initial={{ name: row.name ?? '', capacity: row.capacity === null ? '' : String(row.capacity) }}
          submitLabel="Save section"
          validate={(values) =>
            values.capacity.trim() !== '' && Number(values.capacity) < row.enrolledCount
              ? { capacity: `${row.enrolledCount} students are enrolled, so use at least ${row.enrolledCount}.` }
              : {}
          }
          onSubmit={async (values) => {
            await updateSection(row.id, {
              name: optional(values.name),
              capacity: values.capacity.trim() === '' ? null : Number(values.capacity),
              status: row.status,
            })
            done(`Section ${row.code} saved.`)
          }}
          onCancel={cancel}
        />
      )}
    />
  )
}
