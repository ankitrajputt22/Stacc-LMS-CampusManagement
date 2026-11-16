import { getMyAcademicProfile } from '../api/academicProfileApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useMyProfile } from '../person/useDisplayName'

/** The student's name, IDs, programme, and where they study now, above the Academics pages. */
export function StudentStrip() {
  const academic = useApiQuery(queryKeys.academic.myProfile(), getMyAcademicProfile)
  const person = useMyProfile()
  const student = academic.status === 'loaded' ? academic.data.student : null
  if (!student) {
    return null
  }
  const name = person.status === 'loaded' ? (person.data.fullName ?? person.data.displayName) : student.loginId
  return (
    <div className="mb-6 grid gap-4 rounded-panel border border-line bg-surface px-5 py-4 sm:grid-cols-[1fr_1.6fr_1fr] sm:divide-x sm:divide-line">
      <div>
        <p className="text-xl font-bold text-ink">{name}</p>
        <p className="text-ink-muted">{[student.rollNumber, student.loginId].filter(Boolean).join(' · ')}</p>
      </div>
      <div className="sm:pl-5">
        <p className="font-semibold text-ink">{`${student.programName} (${student.programCode})`}</p>
        <p className="text-ink-muted">{student.departmentName}</p>
      </div>
      <div className="sm:pl-5">
        {student.current && (
          <p className="font-semibold text-ink">
            Semester {student.current.semesterNumber}
            {student.current.sectionCode && ` · Section ${student.current.sectionCode}`}
          </p>
        )}
        <p className="text-ink-muted">
          {[student.current?.academicSessionCode, `Admission ${student.admissionSessionCode}`].filter(Boolean).join(' · ')}
        </p>
      </div>
    </div>
  )
}
