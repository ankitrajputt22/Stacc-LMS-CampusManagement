import { getMyTimetable } from '../api/erpApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { SectionState } from '../courses/SectionState'
import { Button, Card, PageHeader } from '../ui'
import { AcademicsTabs } from './AcademicsTabs'
import { StudentStrip } from './StudentStrip'
import { TimetableGrid } from './TimetableGrid'

/**
 * The weekly class timetable. Students see it as a tab of Academics, the classes of the courses they are enrolled in
 * now; faculty see the classes they teach. Printing prints the timetable alone.
 */
export function ErpTimetablePage({ teaching = false }: { teaching?: boolean }) {
  const timetable = useApiQuery(queryKeys.erp.timetable(), getMyTimetable)
  const slots = timetable.status === 'loaded' ? (teaching ? timetable.data.teaching : timetable.data.studying) ?? [] : []

  return (
    <>
      <title>Timetable · Stacc</title>
      {teaching ? (
        <PageHeader title="Timetable" description="The classes you teach this week." />
      ) : (
        <>
          <PageHeader title="Academics" description="Your program, courses, timetable, attendance and exam information." />
          <StudentStrip />
          <AcademicsTabs />
        </>
      )}
      {timetable.status !== 'loaded' ? (
        <SectionState request={timetable} what="your timetable" />
      ) : (
        <Card
          title="Class Timetable"
          icon="calendar"
          description="Weekly schedule; times are the college's local time."
          action={slots.length > 0 && <Button variant="secondary" onClick={() => window.print()}>Print timetable</Button>}
        >
          {slots.length === 0 ? (
            <p className="text-[0.95rem] text-ink-muted">No classes have been scheduled for you yet.</p>
          ) : (
            <div className="print-area">
              <TimetableGrid slots={slots} caption={teaching ? 'Classes you teach' : 'Your weekly classes'} />
            </div>
          )}
        </Card>
      )}
    </>
  )
}
