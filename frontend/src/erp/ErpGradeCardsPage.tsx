import { useState } from 'react'
import { getMyAcademicProfile } from '../api/academicProfileApi'
import { getMyStanding } from '../api/erpApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { SectionState } from '../courses/SectionState'
import { PortalEmblem } from '../login/PortalEmblem'
import { useMyProfile } from '../person/useDisplayName'
import { Button, Card, PageHeader, SelectField } from '../ui'
import { ExamsTabs } from './ExamsTabs'
import { gpa, plain } from './examFormat'

export function ErpGradeCardsPage() {
  const standing = useApiQuery(queryKeys.erp.standing(), getMyStanding)
  const academic = useApiQuery(queryKeys.academic.myProfile(), getMyAcademicProfile)
  const person = useMyProfile()
  const [chosen, setChosen] = useState(0)
  const student = academic.status === 'loaded' ? academic.data.student : null
  const data = standing.status === 'loaded' ? standing.data : null
  const semester = data?.semesters[chosen] ?? null
  const name = person.status === 'loaded' ? (person.data.fullName ?? person.data.displayName) : (student?.loginId ?? '')

  return (
    <>
      <title>Grade cards · Stacc</title>
      <PageHeader title="Exams & Results" description="Examination schedule, results, grade cards and revaluation." />
      <ExamsTabs />
      {standing.status !== 'loaded' ? (
        <SectionState request={standing} what="your grade cards" />
      ) : !data || !semester || !student ? (
        <Card><p className="py-4 text-[0.95rem] text-ink-muted">A grade card appears once a semester's results are published.</p></Card>
      ) : (
        <>
          <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
            <SelectField label="Semester" className="w-64" value={String(chosen)} onChange={(event) => setChosen(Number(event.target.value))}>
              {data.semesters.map((entry, index) => (
                <option key={`${entry.academicSessionCode}-${entry.semesterNumber}`} value={index}>{`Semester ${entry.semesterNumber} · ${entry.academicSessionCode}`}</option>
              ))}
            </SelectField>
            <Button onClick={() => window.print()}>Print grade card</Button>
          </div>
          <article aria-label="Grade card" className="print-area mx-auto max-w-3xl rounded-panel border border-line bg-surface p-8 text-ink">
            <header className="flex items-center gap-4 border-b-2 border-portal-navy pb-4">
              <PortalEmblem className="size-16" />
              <div>
                <p className="text-2xl font-extrabold tracking-[0.06em] text-portal-navy">STACC</p>
                <p className="text-sm font-semibold">{`Grade Card — Semester ${semester.semesterNumber}, ${semester.academicSessionCode}`}</p>
              </div>
            </header>
            <dl className="mt-5 grid grid-cols-[9rem_1fr] gap-x-3 gap-y-1 text-[0.95rem]">
              <dt className="text-ink-muted">Name</dt><dd className="font-semibold">{name}</dd>
              <dt className="text-ink-muted">College ID</dt><dd>{student.loginId}</dd>
              <dt className="text-ink-muted">Roll number</dt><dd>{student.rollNumber ?? '—'}</dd>
              <dt className="text-ink-muted">Programme</dt><dd>{`${student.programName} (${student.programCode})`}</dd>
            </dl>
            <table className="mt-6 w-full border-collapse text-left text-sm">
              <caption className="sr-only">Courses of this semester</caption>
              <thead>
                <tr className="border-y border-line bg-surface-muted">
                  <th scope="col" className="px-3 py-2">Code</th>
                  <th scope="col" className="px-3 py-2">Course</th>
                  <th scope="col" className="px-3 py-2 text-center">Credits</th>
                  <th scope="col" className="px-3 py-2 text-center">Grade</th>
                  <th scope="col" className="px-3 py-2 text-center">Grade points</th>
                </tr>
              </thead>
              <tbody>
                {semester.courses.map((course) => (
                  <tr key={course.courseResultId} className="border-b border-line">
                    <td className="px-3 py-2">{course.courseCode}</td>
                    <td className="px-3 py-2">{course.courseName}</td>
                    <td className="px-3 py-2 text-center">{plain(course.credits)}</td>
                    <td className="px-3 py-2 text-center">{course.grade ?? '—'}</td>
                    <td className="px-3 py-2 text-center">{plain(course.gradePoints)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <dl className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
              <div><dt className="text-sm text-ink-muted">Credits</dt><dd className="text-lg font-semibold">{plain(semester.creditsGraded)}</dd></div>
              <div><dt className="text-sm text-ink-muted">Credits earned</dt><dd className="text-lg font-semibold">{plain(semester.creditsEarned)}</dd></div>
              <div><dt className="text-sm text-ink-muted">SGPA</dt><dd className="text-lg font-semibold">{gpa(semester.sgpa)}</dd></div>
              <div><dt className="text-sm text-ink-muted">CGPA</dt><dd className="text-lg font-semibold">{gpa(semester.cgpaAfter)}</dd></div>
            </dl>
            <p className="mt-10 text-right text-sm">Controller of Examinations</p>
          </article>
        </>
      )}
    </>
  )
}
