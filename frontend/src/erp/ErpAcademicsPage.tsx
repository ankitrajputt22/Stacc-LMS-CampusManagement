import { getMyAcademicProfile } from '../api/academicProfileApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { SectionState } from '../courses/SectionState'
import { Badge, ButtonLink, Card, PageHeader } from '../ui'
import { AcademicsTabs } from './AcademicsTabs'
import { currentCourses, currentSemester, formatCredits, totalCredits } from './erpData'

const STATUS_WORDS: Record<string, string> = { ENROLLED: 'Enrolled', COMPLETED: 'Completed', WITHDRAWN: 'Withdrawn', DROPPED: 'Dropped' }

export function ErpAcademicsPage() {
  const academic = useApiQuery(queryKeys.academic.myProfile(), getMyAcademicProfile)
  const student = academic.status === 'loaded' ? academic.data.student : null
  const semester = student ? currentSemester(student) : null
  const courses = student ? currentCourses(student) : []

  return (
    <>
      <title>Academics · Stacc</title>
      <PageHeader title="Academics" description="Your courses, timetable, degree progress and academic calendar." />
      <AcademicsTabs />
      {academic.status !== 'loaded' ? (
        <SectionState request={academic} what="your courses" />
      ) : (
        <Card
          title="Registered Courses"
          description={semester ? `Academic Session: ${semester.academicSessionCode} · Semester ${semester.semesterNumber}` : undefined}
        >
          {!student || !semester || courses.length === 0 ? (
            <p className="text-[0.95rem] text-ink-muted">You are not registered in any course this semester.</p>
          ) : (
            <div className="overflow-x-auto rounded-panel border border-line">
              <table className="w-full min-w-[40rem] text-left text-[0.95rem]">
                <caption className="sr-only">Registered courses</caption>
                <thead className="bg-surface-muted text-ink">
                  <tr>
                    <th scope="col" className="px-4 py-3 font-semibold">Course Code</th>
                    <th scope="col" className="px-4 py-3 font-semibold">Course Title</th>
                    <th scope="col" className="px-4 py-3 text-center font-semibold">Credits</th>
                    <th scope="col" className="px-4 py-3 font-semibold">Teacher</th>
                    <th scope="col" className="px-4 py-3 text-center font-semibold">Section</th>
                    <th scope="col" className="px-4 py-3 font-semibold">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {courses.map((course) => (
                    <tr key={course.courseCode} className="border-t border-line">
                      <th scope="row" className="px-4 py-3.5 font-medium text-ink">{course.courseCode}</th>
                      <td className="px-4 py-3.5">{course.courseName}</td>
                      <td className="px-4 py-3.5 text-center">{formatCredits(Number(course.credits))}</td>
                      <td className="px-4 py-3.5">{course.teachers.length > 0 ? course.teachers.join(', ') : '—'}</td>
                      <td className="px-4 py-3.5 text-center">{semester.sectionCode ?? '—'}</td>
                      <td className="px-4 py-3.5">
                        <Badge tone={course.status === 'ENROLLED' ? 'success' : 'neutral'}>{STATUS_WORDS[course.status] ?? course.status}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="mt-6 flex flex-wrap items-center justify-between gap-4 border-t border-line pt-5">
            <p className="text-lg font-semibold text-ink">Total Credits: {totalCredits(courses)}</p>
            <ButtonLink to="/academic/registration">Registration</ButtonLink>
          </div>
        </Card>
      )}
    </>
  )
}
