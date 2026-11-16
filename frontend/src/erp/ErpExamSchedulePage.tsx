import { getMyExams } from '../api/erpApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { NavIcon } from '../components/navigation/navIcons'
import { SectionState } from '../courses/SectionState'
import { ButtonLink, Card, PageHeader } from '../ui'
import { ExamsTabs } from './ExamsTabs'
import { hhmm } from './examFormat'
import { formatDay } from './requestWords'

/** Exams & Results → Exam schedule: the student's published exams with hall and seat. */
export function ErpExamSchedulePage() {
  const exams = useApiQuery(queryKeys.erp.myExams(), getMyExams)
  const rows = exams.status === 'loaded' ? exams.data : []
  const sessionName = rows[0]?.sessionName

  return (
    <>
      <title>Exams & Results · Stacc</title>
      <PageHeader title="Exams & Results" description="Examination schedule, results, grade cards and revaluation." />
      <ExamsTabs />
      {exams.status !== 'loaded' ? (
        <SectionState request={exams} what="your exams" />
      ) : rows.length === 0 ? (
        <Card><p className="py-4 text-[0.95rem] text-ink-muted">No exams scheduled yet.</p></Card>
      ) : (
        <Card>
          <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-4">
              <NavIcon name="calendar" className="size-10 text-portal-navy" />
              <span aria-hidden="true" className="h-12 w-0.5 bg-portal-cta" />
              <div>
                <h2 className="text-2xl font-bold text-ink">{sessionName}</h2>
                <p className="text-ink-muted">Bring your hall ticket and college ID.</p>
              </div>
            </div>
            <ButtonLink to="/erp/exams/hall-ticket">Open hall ticket</ButtonLink>
          </div>
          <div className="overflow-x-auto rounded-panel border border-line">
            <table className="w-full min-w-[46rem] text-left text-[0.95rem]">
              <caption className="sr-only">Your exams</caption>
              <thead className="bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3 font-semibold">Date</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Course</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Teacher</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Time</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Hall</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Seat</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((exam) => (
                  <tr key={exam.slotId} className="border-t border-line">
                    <th scope="row" className="px-4 py-3.5 font-semibold whitespace-nowrap">{formatDay(exam.examDate)}</th>
                    <td className="px-4 py-3.5">{`${exam.courseCode} – ${exam.courseName}`}</td>
                    <td className="px-4 py-3.5">{exam.teachers.join(', ') || '—'}</td>
                    <td className="px-4 py-3.5 whitespace-nowrap">{`${hhmm(exam.startsAt)} – ${hhmm(exam.endsAt)}`}</td>
                    <td className="px-4 py-3.5">{exam.hall}</td>
                    <td className="px-4 py-3.5">{exam.seatNumber ?? 'To be allotted'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </>
  )
}
