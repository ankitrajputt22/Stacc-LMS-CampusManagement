import { useState } from 'react'
import { Link } from 'react-router'
import { getMyStanding } from '../api/erpApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { NavIcon } from '../components/navigation/navIcons'
import { SectionState } from '../courses/SectionState'
import type { SemesterStanding } from '../types/erp'
import { Card, PageHeader, SelectField } from '../ui'
import { ExamsTabs } from './ExamsTabs'
import { StudentStrip } from './StudentStrip'
import { gpa, plain } from './examFormat'

function semesterLabel(semester: SemesterStanding): string {
  return `Semester ${semester.semesterNumber} · ${semester.academicSessionCode}`
}

function ResultsTable({ semester }: { semester: SemesterStanding }) {
  const split = semester.courses.some((course) => course.internalMax !== null)
  const first = semester.courses.find((course) => course.internalMax !== null)
  return (
    <div className="overflow-x-auto rounded-panel border border-line">
      <table className="w-full min-w-[42rem] text-left text-[0.95rem]">
        <caption className="sr-only">{`Results of ${semesterLabel(semester)}`}</caption>
        <thead className="bg-surface-muted">
          <tr>
            <th scope="col" className="px-3 py-3 font-semibold">Code</th>
            <th scope="col" className="px-3 py-3 font-semibold">Course</th>
            <th scope="col" className="px-3 py-3 text-center font-semibold">Credits</th>
            {split && <th scope="col" className="px-3 py-3 text-center font-semibold">Internal{first && ` / ${plain(first.internalMax)}`}</th>}
            {split && <th scope="col" className="px-3 py-3 text-center font-semibold">External{first && ` / ${plain(first.externalMax)}`}</th>}
            <th scope="col" className="px-3 py-3 text-center font-semibold">Total</th>
            <th scope="col" className="px-3 py-3 text-center font-semibold">Grade</th>
            <th scope="col" className="px-3 py-3 text-center font-semibold">Points</th>
          </tr>
        </thead>
        <tbody>
          {semester.courses.map((course) => (
            <tr key={course.courseResultId} className="border-t border-line">
              <th scope="row" className="px-3 py-3 font-semibold">{course.courseCode}</th>
              <td className="px-3 py-3">{course.courseName}</td>
              <td className="px-3 py-3 text-center">{plain(course.credits)}</td>
              {split && <td className="px-3 py-3 text-center">{plain(course.internalMarks)}</td>}
              {split && <td className="px-3 py-3 text-center">{plain(course.externalMarks)}</td>}
              <td className="px-3 py-3 text-center">{`${plain(course.marksObtained)} / ${plain(course.maxMarks)}`}</td>
              <td className="px-3 py-3 text-center font-medium">{course.grade ?? '—'}</td>
              <td className="px-3 py-3 text-center">{plain(course.gradePoints)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function ErpResultsPage() {
  const standing = useApiQuery(queryKeys.erp.standing(), getMyStanding)
  const [chosen, setChosen] = useState(0)
  const data = standing.status === 'loaded' ? standing.data : null
  const semester = data?.semesters[chosen] ?? null
  const chronological = data ? [...data.semesters].reverse() : []

  return (
    <>
      <title>Results · Stacc</title>
      <PageHeader title="Results" description="Semester-wise exam results, SGPA and CGPA." />
      <ExamsTabs />
      <StudentStrip />
      {standing.status !== 'loaded' ? (
        <SectionState request={standing} what="your results" />
      ) : !data || data.semesters.length === 0 ? (
        <Card><p className="py-4 text-[0.95rem] text-ink-muted">No results have been published for you yet.</p></Card>
      ) : (
        <div className="space-y-6">
          <div className="grid gap-6 xl:grid-cols-[minmax(0,2.2fr)_minmax(0,1fr)]">
            <Card
              title="Results"
              icon="exams"
              action={
                <SelectField label="Semester" className="w-56" value={String(chosen)} onChange={(event) => setChosen(Number(event.target.value))}>
                  {data.semesters.map((entry, index) => <option key={semesterLabel(entry)} value={index}>{semesterLabel(entry)}</option>)}
                </SelectField>
              }
            >
              {semester && (
                <>
                  <ResultsTable semester={semester} />
                  <div className="mt-5 grid gap-3 rounded-panel bg-surface-muted px-5 py-4 sm:grid-cols-3 sm:divide-x sm:divide-line">
                    <p className="text-lg"><strong className="text-2xl">{plain(semester.creditsGraded)}</strong> Credits</p>
                    <p className="text-lg sm:pl-5"><strong className="text-2xl">{plain(semester.weightedPoints)}</strong> Weighted Points</p>
                    <p className="text-lg font-semibold sm:pl-5">SGPA <span className="text-3xl font-bold text-portal-cta">{gpa(semester.sgpa)}</span></p>
                  </div>
                </>
              )}
            </Card>
            <Card title="CGPA history" icon="academics">
              <p className="text-5xl font-bold text-portal-cta">{gpa(data.cgpa)}</p>
              <p className="mt-1 text-ink-muted">{`${plain(data.creditsGraded)} Credits, ${plain(data.weightedPoints)} Points`}</p>
              <ul className="mt-5 space-y-4 border-t border-line pt-4">
                {chronological.map((entry) => (
                  <li key={semesterLabel(entry)}>
                    <div className="flex justify-between text-lg font-semibold"><span>{`Sem ${entry.semesterNumber}`}</span><span>{gpa(entry.cgpaAfter)}</span></div>
                    <div role="img" aria-label={`CGPA ${gpa(entry.cgpaAfter)} after semester ${entry.semesterNumber}`} className="mt-1.5 h-2.5 rounded-full bg-line">
                      <div className="h-full rounded-full bg-portal-cta" style={{ width: `${Math.min(100, Number(entry.cgpaAfter ?? 0) * 10)}%` }} />
                    </div>
                  </li>
                ))}
              </ul>
            </Card>
          </div>
          <Card>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="flex items-center gap-3 text-lg"><NavIcon name="documents" className="size-6" /><strong>Revaluation:</strong> Apply for revaluation of a published result.</p>
              <Link to="/erp/exams/revaluation" className="text-lg font-semibold text-portal-cta underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-stacc-primary">
                Apply for revaluation
              </Link>
            </div>
          </Card>
        </div>
      )}
    </>
  )
}
