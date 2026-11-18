import { getMyAcademicProfile } from '../api/academicProfileApi'
import { getMyExams } from '../api/erpApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { SectionState } from '../courses/SectionState'
import { PortalEmblem } from '../login/PortalEmblem'
import { initialsOf, useMyProfile } from '../person/useDisplayName'
import { Button, Card, PageHeader } from '../ui'
import { ExamsTabs } from './ExamsTabs'
import { hhmm } from './examFormat'
import { formatDay } from './requestWords'

export function ErpHallTicketPage() {
  const exams = useApiQuery(queryKeys.erp.myExams(), getMyExams)
  const academic = useApiQuery(queryKeys.academic.myProfile(), getMyAcademicProfile)
  const person = useMyProfile()
  const student = academic.status === 'loaded' ? academic.data.student : null
  const name = person.status === 'loaded' ? (person.data.fullName ?? person.data.displayName) : (student?.loginId ?? '')
  const rows = exams.status === 'loaded' ? exams.data : []

  return (
    <>
      <title>Hall ticket · Stacc</title>
      <PageHeader title="Exams & Results" description="Examination schedule, results, grade cards and revaluation." />
      <ExamsTabs />
      {exams.status !== 'loaded' ? (
        <SectionState request={exams} what="your hall ticket" />
      ) : rows.length === 0 || !student ? (
        <Card><p className="py-4 text-[0.95rem] text-ink-muted">Your hall ticket appears once your exams are published.</p></Card>
      ) : (
        <>
          <div className="mb-4 flex justify-end">
            <Button onClick={() => window.print()}>Print hall ticket</Button>
          </div>
          <article aria-label="Hall ticket" className="print-area mx-auto max-w-3xl rounded-panel border border-line bg-surface p-8 text-ink">
            <header className="flex items-center gap-4 border-b-2 border-portal-navy pb-4">
              <PortalEmblem className="size-16" />
              <div>
                <p className="text-2xl font-extrabold tracking-[0.06em] text-portal-navy">STACC</p>
                <p className="text-sm font-semibold">Hall Ticket — {rows[0].sessionName}</p>
              </div>
            </header>
            <div className="mt-5 flex gap-6">
              <span aria-hidden="true" className="flex size-24 shrink-0 items-center justify-center rounded-md border border-line bg-surface-muted text-3xl font-semibold">
                {initialsOf(name || student.loginId)}
              </span>
              <dl className="grid flex-1 grid-cols-[9rem_1fr] gap-x-3 gap-y-1 text-[0.95rem]">
                <dt className="text-ink-muted">Name</dt><dd className="font-semibold">{name}</dd>
                <dt className="text-ink-muted">College ID</dt><dd>{student.loginId}</dd>
                <dt className="text-ink-muted">Roll number</dt><dd>{student.rollNumber ?? '—'}</dd>
                <dt className="text-ink-muted">Programme</dt><dd>{`${student.programName} (${student.programCode})`}</dd>
                {student.current && <><dt className="text-ink-muted">Semester</dt><dd>{`Semester ${student.current.semesterNumber}${student.current.sectionCode ? ` · Section ${student.current.sectionCode}` : ''}`}</dd></>}
              </dl>
            </div>
            <table className="mt-6 w-full border-collapse text-left text-sm">
              <caption className="sr-only">Exams on this hall ticket</caption>
              <thead>
                <tr className="border-y border-line bg-surface-muted">
                  <th scope="col" className="px-3 py-2">Date</th>
                  <th scope="col" className="px-3 py-2">Course</th>
                  <th scope="col" className="px-3 py-2">Time</th>
                  <th scope="col" className="px-3 py-2">Hall</th>
                  <th scope="col" className="px-3 py-2">Seat</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((exam) => (
                  <tr key={exam.slotId} className="border-b border-line">
                    <td className="px-3 py-2 whitespace-nowrap">{formatDay(exam.examDate)}</td>
                    <td className="px-3 py-2">{`${exam.courseCode} – ${exam.courseName}`}</td>
                    <td className="px-3 py-2 whitespace-nowrap">{`${hhmm(exam.startsAt)} – ${hhmm(exam.endsAt)}`}</td>
                    <td className="px-3 py-2">{exam.hall}</td>
                    <td className="px-3 py-2">{exam.seatNumber ?? 'To be allotted'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <h2 className="mt-6 font-semibold">Instructions</h2>
            <ul className="mt-1 list-disc space-y-0.5 pl-5 text-sm">
              <li>Bring this hall ticket and your college ID to every exam.</li>
              <li>Be in the hall 15 minutes before the exam starts.</li>
              <li>Phones, notes, and smart watches are not allowed in the hall.</li>
            </ul>
            <p className="mt-10 text-right text-sm">Controller of Examinations</p>
          </article>
        </>
      )}
    </>
  )
}
