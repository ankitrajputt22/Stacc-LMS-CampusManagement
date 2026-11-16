import { useState } from 'react'
import { useParams } from 'react-router'
import { getMyAttendance } from '../api/attendanceApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import { Badge, Button, Card, PageHeader } from '../ui'
import { CorrectionDialog, MedicalLeaveDialog } from './RequestDialogs'
import { formatMomentDay } from './requestWords'

/** One course's attendance: every finalized class with its topic and status, a summary, and requests. */
export function ErpCourseAttendancePage() {
  const { courseOfferingId } = useParams()
  const attendance = useApiQuery(queryKeys.attendance.mine(), getMyAttendance)
  const [dialog, setDialog] = useState<'leave' | number | null>(null)
  const course = attendance.status === 'loaded'
    ? attendance.data.courses.find((entry) => String(entry.courseOfferingId) === courseOfferingId) ?? null
    : null

  if (attendance.status !== 'loaded') {
    return <SectionState request={attendance} what="your attendance" />
  }
  if (!course) {
    return <EmptyState title="No attendance recorded for this course." />
  }
  const sessions = [...course.sessions].sort((a, b) => a.heldAt.localeCompare(b.heldAt))

  return (
    <>
      <title>{`Attendance · ${course.courseCode} · Stacc`}</title>
      <PageHeader
        title={`Attendance / ${course.courseCode}`}
        description={`${course.courseCode} · ${course.courseName}`}
        actions={<Button onClick={() => setDialog('leave')}>Request medical leave</Button>}
      />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <Card title="Attendance Sessions" padding="none">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[34rem] text-left text-[0.95rem]">
              <caption className="sr-only">{`Classes of ${course.courseCode}`}</caption>
              <thead className="bg-surface-muted">
                <tr>
                  <th scope="col" className="px-5 py-3 font-semibold">Date</th>
                  <th scope="col" className="px-5 py-3 font-semibold">Topic</th>
                  <th scope="col" className="px-5 py-3 font-semibold">Status</th>
                  <th scope="col" className="px-5 py-3"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {sessions.map((session) => (
                  <tr key={session.attendanceSessionId} className="border-t border-line">
                    <td className="px-5 py-2.5 whitespace-nowrap">{formatMomentDay(session.heldAt)}</td>
                    <td className="px-5 py-2.5">{session.topic ?? '—'}</td>
                    <td className="px-5 py-2.5">
                      {session.status === 'PRESENT' ? <Badge tone="success">Present</Badge> : <Badge tone="danger">Absent</Badge>}
                    </td>
                    <td className="px-5 py-2.5 text-right">
                      {session.status === 'ABSENT' && (
                        <Button variant="ghost" size="sm" onClick={() => setDialog(session.attendanceRecordId)}>
                          Request correction<span className="sr-only"> for {formatMomentDay(session.heldAt)}</span>
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
        <Card title="Attendance Summary">
          <dl className="divide-y divide-line text-lg">
            <div className="flex justify-between py-2.5"><dt>Present</dt><dd className="font-semibold">{course.presentCount}</dd></div>
            <div className="flex justify-between py-2.5"><dt>Absent</dt><dd className="font-semibold">{course.absentCount}</dd></div>
            <div className="flex justify-between py-2.5"><dt>Total Sessions</dt><dd className="font-semibold">{course.totalSessions}</dd></div>
            <div className="flex justify-between py-3 text-xl font-bold">
              <dt>Attendance</dt>
              <dd>{course.attendancePercentage === null ? '—' : `${Math.round(Number(course.attendancePercentage))}%`}</dd>
            </div>
          </dl>
          {course.lowAttendance && <p className="mt-2"><Badge tone="warning">Low — below 75%</Badge></p>}
        </Card>
      </div>
      <MedicalLeaveDialog open={dialog === 'leave'} onClose={() => setDialog(null)} />
      {typeof dialog === 'number' && (
        <CorrectionDialog open courses={[course]} recordId={dialog} onClose={() => setDialog(null)} />
      )}
    </>
  )
}
