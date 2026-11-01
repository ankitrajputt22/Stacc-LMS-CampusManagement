import { getMyAcademicProfile } from '../api/academicProfileApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { courseTypeWord, degreeWord } from '../admin/catalogText'
import { roleWord } from '../admin/offeringText'
import { statusTone, statusWord } from '../admin/masterStatus'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { creditsText } from '../courses/credits'
import { formatMarks } from '../courses/marks'
import type { MyAcademicProfile, MyCourseRecord, MySemesterRecord } from '../types/academicProfile'
import { Badge, Card, DataTable, PageHeader } from '../ui'

type StudentPart = NonNullable<MyAcademicProfile['student']>
type FacultyPart = NonNullable<MyAcademicProfile['faculty']>
type Teaching = FacultyPart['teaching'][number]

function Facts({ items }: { items: [string, string | null][] }) {
  return (
    <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">
      {items.map(([term, value]) => (
        <div key={term}>
          <dt className="text-sm text-ink-muted">{term}</dt>
          <dd className="text-base font-medium break-words text-ink">{value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  )
}

/** The published result exactly as recorded, or that none is published. Never a percentage, GPA, or pass or fail. */
function resultText(course: MyCourseRecord): string {
  if (course.result === null) {
    return 'Not published'
  }
  const marks = `${formatMarks(course.result.marksObtained)} / ${formatMarks(course.result.maxMarks)}`
  return course.result.gradeCode ? `${marks} · ${course.result.gradeCode}` : marks
}

function SemesterCard({ semester }: { semester: MySemesterRecord }) {
  return (
    <Card
      title={`${semester.academicSessionCode} · Semester ${semester.semesterNumber}`}
      description={`${semester.programCode}${semester.sectionCode ? ` · Section ${semester.sectionCode}` : ''}`}
      action={<Badge tone={statusTone(semester.status)}>{statusWord(semester.status)}</Badge>}
      padding="none"
    >
      <DataTable<MyCourseRecord>
        caption={`Courses of ${semester.academicSessionCode} semester ${semester.semesterNumber}`}
        columns={[
          { key: 'course', header: 'Course', primary: true, cell: (course) => `${course.courseCode} · ${course.courseName}` },
          { key: 'type', header: 'Type', cell: (course) => courseTypeWord(course.courseType) },
          { key: 'credits', header: 'Credits', cell: (course) => creditsText(course.credits) },
          { key: 'status', header: 'Status', cell: (course) => <Badge tone={statusTone(course.status)}>{statusWord(course.status)}</Badge> },
          { key: 'result', header: 'Result', cell: resultText },
        ]}
        rows={semester.courses}
        rowKey={(course) => course.courseCode}
        empty={<EmptyState title="No courses in this semester yet." />}
      />
    </Card>
  )
}

function StudentProfile({ student }: { student: StudentPart }) {
  const now = student.current
  return (
    <section aria-labelledby="student-profile" className="space-y-4">
      <h2 id="student-profile" className="text-xl font-semibold text-ink">
        As a student
      </h2>
      <Card>
        <Facts
          items={[
            ['Student ID', student.loginId],
            ['Roll number', student.rollNumber],
            ['Program', `${student.programCode} · ${student.programName}`],
            ['Degree', degreeWord(student.degreeType)],
            ['Department', `${student.departmentCode} · ${student.departmentName}`],
            ['Admitted', student.admissionSessionCode],
            [
              'Studying now',
              now === null
                ? 'Not enrolled in a semester now'
                : `${now.academicSessionCode} · Semester ${now.semesterNumber}${now.sectionCode ? ` · Section ${now.sectionCode}` : ''}`,
            ],
          ]}
        />
      </Card>
      <h3 className="text-lg font-semibold text-ink">Academic record</h3>
      {student.semesters.length === 0 ? (
        <StatePanel>
          <EmptyState title="No semesters yet." description="Your semesters appear here once the college enrolls you." />
        </StatePanel>
      ) : (
        student.semesters.map((semester) => (
          <SemesterCard key={`${semester.academicSessionCode}-${semester.semesterNumber}`} semester={semester} />
        ))
      )}
    </section>
  )
}

function FacultyProfile({ faculty }: { faculty: FacultyPart }) {
  return (
    <section aria-labelledby="faculty-profile" className="space-y-4">
      <h2 id="faculty-profile" className="text-xl font-semibold text-ink">
        As a faculty member
      </h2>
      <Card>
        <Facts
          items={[
            ['Employee ID', faculty.loginId],
            ['Primary department', `${faculty.departmentCode} · ${faculty.departmentName}`],
          ]}
        />
      </Card>
      <Card title="Teaching now" description="The course offerings the college has assigned you to teach." padding="none">
        <DataTable<Teaching>
          caption="Course offerings you teach"
          columns={[
            { key: 'course', header: 'Course', primary: true, cell: (item) => `${item.courseCode} · ${item.courseName}` },
            { key: 'semester', header: 'Semester', cell: (item) => `${item.programCode} · Sem ${item.semesterNumber} · ${item.academicSessionCode}` },
            { key: 'section', header: 'Section', cell: (item) => item.sectionCode ?? 'Whole semester' },
            { key: 'role', header: 'Role', cell: (item) => roleWord(item.role) },
          ]}
          rows={faculty.teaching}
          rowKey={(item) => item.courseOfferingId}
          empty={<EmptyState title="No teaching assigned now." />}
        />
      </Card>
    </section>
  )
}

/**
 * The signed-in person's own official academic profile, at /academic/profile: for a student, their program,
 * department, where they study now, and their semester-by-semester record with published results; for a faculty member,
 * their department and the offerings they teach. Read-only: the college keeps this data. The backend takes the account
 * from the session and decides every part.
 */
export function AcademicProfilePage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const allowed = hasAnyRole(roles, ['STUDENT', 'FACULTY'])
  const profile = useApiQuery(allowed ? queryKeys.academic.myProfile() : null, getMyAcademicProfile)

  return (
    <div className="max-w-5xl">
      <title>Academic profile · Stacc</title>
      <PageHeader
        eyebrow="ERP"
        title="Academic profile"
        description="Your official academic details, as the college keeps them. Contact the college administration if something is wrong."
      />
      {!allowed ? (
        <StatePanel>
          <AccessDeniedState title="This page is for students and faculty." message="If you think you should see it, contact the college administration." />
        </StatePanel>
      ) : profile.status === 'loading' ? (
        <StatePanel>
          <LoadingState label="Loading your academic profile…" />
        </StatePanel>
      ) : profile.status === 'denied' ? (
        <StatePanel>
          <AccessDeniedState
            title="You don’t have access to your academic profile."
            message="If your access changed recently, sign out and sign in again."
          />
        </StatePanel>
      ) : profile.status !== 'loaded' ? (
        <StatePanel>
          <ErrorState title="Couldn’t load your academic profile." message="Check your connection and try again." onRetry={profile.retry} />
        </StatePanel>
      ) : profile.data.student === null && profile.data.faculty === null ? (
        <StatePanel>
          <EmptyState title="No academic profile yet." description="The college has not set up your academic details yet." />
        </StatePanel>
      ) : (
        <div className="space-y-8">
          {profile.data.student && <StudentProfile student={profile.data.student} />}
          {profile.data.faculty && <FacultyProfile faculty={profile.data.faculty} />}
        </div>
      )}
    </div>
  )
}
