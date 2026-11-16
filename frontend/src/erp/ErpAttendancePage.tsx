import { useState } from 'react'
import { Link } from 'react-router'
import { getMyAttendance } from '../api/attendanceApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { NavIcon } from '../components/navigation/navIcons'
import { SectionState } from '../courses/SectionState'
import { Badge, Button, Card, PageHeader } from '../ui'
import { CorrectionDialog, MedicalLeaveDialog } from './RequestDialogs'
import { RequestHistory } from './RequestHistory'

function percent(value: number | null): string {
  return value === null ? '—' : `${Math.round(Number(value))}%`
}

/** ERP Attendance: finalized attendance per course, low attendance marked by the backend, and the student's requests. */
export function ErpAttendancePage() {
  const attendance = useApiQuery(queryKeys.attendance.mine(), getMyAttendance)
  const [dialog, setDialog] = useState<'leave' | 'correction' | null>(null)
  const courses = attendance.status === 'loaded' ? attendance.data.courses : []
  const low = courses.filter((course) => course.lowAttendance)

  return (
    <>
      <title>Attendance · Stacc</title>
      <PageHeader title="Attendance" description="Course-wise attendance details and related requests." />
      {low.map((course) => (
        <div key={course.courseOfferingId} role="status" className="mb-4 flex items-center gap-4 rounded-panel border border-amber-300 bg-amber-50 px-5 py-3.5">
          <NavIcon name="warning" className="size-7 text-portal-cta" />
          <span aria-hidden="true" className="h-8 w-px bg-portal-cta/40" />
          <p className="text-lg font-semibold text-ink">
            Attendance in {course.courseCode} is below 75% ({percent(course.attendancePercentage)}).
          </p>
        </div>
      ))}
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <Card title="Course Attendance" icon="courses">
          {attendance.status !== 'loaded' ? (
            <SectionState request={attendance} what="your attendance" />
          ) : courses.length === 0 ? (
            <p className="text-[0.95rem] text-ink-muted">No attendance recorded yet.</p>
          ) : (
            <div className="overflow-x-auto rounded-panel border border-line">
              <table className="w-full min-w-[36rem] text-left text-[0.95rem]">
                <caption className="sr-only">Attendance per course</caption>
                <thead className="bg-surface-muted">
                  <tr>
                    <th scope="col" className="px-4 py-3 font-semibold">Course</th>
                    <th scope="col" className="px-3 py-3 text-center font-semibold">Present</th>
                    <th scope="col" className="px-3 py-3 text-center font-semibold">Absent</th>
                    <th scope="col" className="px-3 py-3 text-center font-semibold">Total</th>
                    <th scope="col" className="px-3 py-3 text-center font-semibold">Attendance</th>
                    <th scope="col" className="px-3 py-3 font-semibold">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {courses.map((course) => (
                    <tr key={course.courseOfferingId} className={`border-t border-line ${course.lowAttendance ? 'bg-orange-50' : ''}`}>
                      <th scope="row" className="px-4 py-3 font-normal">
                        <Link to={`/erp/attendance/${course.courseOfferingId}`} className="text-lg font-semibold text-teal-800 hover:underline focus-visible:outline-2 focus-visible:outline-stacc-primary">
                          {course.courseCode}
                        </Link>
                        <span className="block text-sm text-ink-muted">{course.courseName}</span>
                      </th>
                      <td className="px-3 py-3 text-center">{course.presentCount}</td>
                      <td className="px-3 py-3 text-center">{course.absentCount}</td>
                      <td className="px-3 py-3 text-center">{course.totalSessions}</td>
                      <td className="px-3 py-3 text-center text-lg font-medium">{percent(course.attendancePercentage)}</td>
                      <td className="px-3 py-3">
                        {course.lowAttendance ? <Badge tone="warning">Low — below 75%</Badge> : <Badge tone="success">Good</Badge>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
        <Card title="Requests" icon="documents">
          <div className="flex flex-wrap gap-3">
            <Button onClick={() => setDialog('leave')}>Request medical leave</Button>
            <Button onClick={() => setDialog('correction')} disabled={attendance.status !== 'loaded'}>Request a correction</Button>
          </div>
          <h3 className="mt-6 mb-3 border-t border-line pt-5 text-lg font-semibold text-ink">Request History</h3>
          <RequestHistory kinds={['MEDICAL_LEAVE', 'ATTENDANCE_CORRECTION']} />
        </Card>
      </div>
      <MedicalLeaveDialog open={dialog === 'leave'} onClose={() => setDialog(null)} />
      {dialog === 'correction' && <CorrectionDialog open courses={courses} onClose={() => setDialog(null)} />}
    </>
  )
}
