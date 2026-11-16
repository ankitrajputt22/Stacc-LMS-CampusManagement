import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { getGradeScale } from '../api/erpApi'
import { queryKeys } from '../api/queryKeys'
import { getCourseResults, getMyResultCourses, publishCourseResult, saveCourseResult } from '../api/resultsApi'
import { useApiQuery } from '../api/useApiQuery'
import { NavIcon } from '../components/navigation/navIcons'
import { SectionState } from '../courses/SectionState'
import type { GradeScaleEntry } from '../types/erp'
import type { CourseResultRoster } from '../types/results'
import { Alert, Badge, Button, Card, PageHeader, TextField } from '../ui'
import { plain } from './examFormat'
import { refusalOf } from './requestWords'

type Row = { internal: string; external: string }

function gradeFor(scale: readonly GradeScaleEntry[], marks: number, max: number): string | null {
  const percent = (marks * 100) / max
  return scale.find((grade) => percent >= Number(grade.minPercent))?.code ?? null
}

function partError(value: string, max: number): string | null {
  if (value.trim() === '') {
    return null
  }
  const number = Number(value)
  if (!Number.isFinite(number) || number < 0 || number > max || Math.round(number * 100) !== number * 100) {
    return `Must be 0–${plain(max)}`
  }
  return null
}

/** The offerings the faculty member teaches, each opening its marks sheet. */
export function ErpMarksCoursesPage() {
  const courses = useApiQuery(queryKeys.results.myCourses(), getMyResultCourses)
  return (
    <>
      <title>Marks Entry · Stacc</title>
      <PageHeader title="Marks Entry" description="Enter and update internal and external marks for course results." />
      <Card title="Courses you teach" icon="courses">
        {courses.status !== 'loaded' ? (
          <SectionState request={courses} what="your courses" />
        ) : courses.data.length === 0 ? (
          <p className="text-[0.95rem] text-ink-muted">You are not teaching any course offering now.</p>
        ) : (
          <ul className="divide-y divide-line">
            {courses.data.map((course) => (
              <li key={course.courseOfferingId} className="flex flex-wrap items-center justify-between gap-3 py-3">
                <span>
                  <span className="font-semibold">{`${course.courseCode}: ${course.courseName}`}</span>
                  <span className="block text-sm text-ink-muted">{`${course.programCode} · Semester ${course.semesterNumber} · ${course.academicSessionCode}`}</span>
                </span>
                <Link to={`/erp/marks/${course.courseOfferingId}`} className="font-semibold text-portal-cta hover:underline focus-visible:outline-2 focus-visible:outline-stacc-primary">
                  Enter marks<span className="sr-only"> for {course.courseCode}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  )
}

function Sheet({ roster, scale, courseOfferingId }: { roster: CourseResultRoster; scale: readonly GradeScaleEntry[]; courseOfferingId: number }) {
  const queryClient = useQueryClient()
  const firstSplit = roster.students.find((student) => student.result?.internalMax != null)?.result
  const [internalMax, setInternalMax] = useState(String(firstSplit?.internalMax ?? 40))
  const [externalMax, setExternalMax] = useState(String(firstSplit?.externalMax ?? 60))
  const [rows, setRows] = useState<Record<number, Row>>(() =>
    Object.fromEntries(roster.students.map((student) => [student.courseEnrollmentId, {
      internal: student.result?.internalMarks != null ? String(Number(student.result.internalMarks)) : '',
      external: student.result?.externalMarks != null ? String(Number(student.result.externalMarks)) : '',
    }])),
  )
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const inMax = Number(internalMax)
  const exMax = Number(externalMax)
  const maxValid = inMax > 0 && exMax > 0
  const students = roster.students.filter((student) => student.courseEnrollmentStatus !== 'DROPPED' && student.courseEnrollmentStatus !== 'WITHDRAWN')
  const errors = students.some((student) => partError(rows[student.courseEnrollmentId].internal, inMax) || partError(rows[student.courseEnrollmentId].external, exMax))
  const drafts = roster.students.filter((student) => student.result?.status === 'DRAFT')

  function set(id: number, part: keyof Row, value: string) {
    setRows((current) => ({ ...current, [id]: { ...current[id], [part]: value } }))
  }

  async function saveAll() {
    if (busy || errors || !maxValid) {
      return
    }
    setBusy(true)
    setMessage(null)
    let saved = 0
    try {
      for (const student of students) {
        const row = rows[student.courseEnrollmentId]
        if (row.internal.trim() === '' || row.external.trim() === '') {
          continue
        }
        const internal = Number(row.internal)
        const external = Number(row.external)
        const result = student.result
        if (result && Number(result.internalMarks) === internal && Number(result.externalMarks) === external
          && Number(result.internalMax) === inMax && Number(result.externalMax) === exMax) {
          continue
        }
        const total = Math.round((internal + external) * 100) / 100
        await saveCourseResult(courseOfferingId, student.courseEnrollmentId, {
          marksObtained: total, maxMarks: inMax + exMax, gradeCode: gradeFor(scale, total, inMax + exMax), remarks: result?.remarks ?? null,
          internalMarks: internal, internalMax: inMax, externalMarks: external, externalMax: exMax,
        })
        saved += 1
      }
      setMessage({ tone: 'success', text: saved === 0 ? 'Nothing changed.' : `Saved ${saved} ${saved === 1 ? 'result' : 'results'} as drafts.` })
    } catch (failure) {
      setMessage({ tone: 'error', text: refusalOf(failure) ?? 'Your session ended.' })
    } finally {
      await queryClient.invalidateQueries({ queryKey: queryKeys.results.all })
      setBusy(false)
    }
  }

  async function publishAll() {
    if (busy || drafts.length === 0 || !window.confirm(`Publish ${drafts.length} ${drafts.length === 1 ? 'result' : 'results'}? Students see them at once, and published results cannot be unpublished.`)) {
      return
    }
    setBusy(true)
    setMessage(null)
    try {
      for (const student of drafts) {
        await publishCourseResult(courseOfferingId, student.courseEnrollmentId)
      }
      setMessage({ tone: 'success', text: 'Results published.' })
    } catch (failure) {
      setMessage({ tone: 'error', text: refusalOf(failure) ?? 'Your session ended.' })
    } finally {
      await queryClient.invalidateQueries({ queryKey: queryKeys.results.all })
      setBusy(false)
    }
  }

  return (
    <>
      <div className="mb-6 flex items-center gap-4 rounded-panel border border-amber-300 bg-amber-50 px-5 py-3.5">
        <NavIcon name="info" className="size-7 text-portal-cta" />
        <span aria-hidden="true" className="h-8 w-px bg-portal-cta/40" />
        <p className="text-lg font-semibold text-portal-cta">Results remain draft until published.</p>
      </div>
      <Card>
        <div className="mb-4 flex flex-wrap gap-4">
          <TextField label="Internal out of" type="number" min={1} className="w-40" value={internalMax} onChange={(event) => setInternalMax(event.target.value)} />
          <TextField label="External out of" type="number" min={1} className="w-40" value={externalMax} onChange={(event) => setExternalMax(event.target.value)} />
        </div>
        {students.length === 0 ? (
          <p className="text-[0.95rem] text-ink-muted">No students are enrolled in this offering.</p>
        ) : (
          <div className="overflow-x-auto rounded-panel border border-line">
            <table className="w-full min-w-[40rem] text-left text-[0.95rem]">
              <caption className="sr-only">Marks of each student</caption>
              <thead className="bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3 font-semibold">College ID</th>
                  <th scope="col" className="px-4 py-3 font-semibold">{`Internal / ${plain(inMax)}`}</th>
                  <th scope="col" className="px-4 py-3 font-semibold">{`External / ${plain(exMax)}`}</th>
                  <th scope="col" className="px-4 py-3 text-center font-semibold">{`Total / ${plain(inMax + exMax)}`}</th>
                  <th scope="col" className="px-4 py-3 text-center font-semibold">Grade</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Status</th>
                </tr>
              </thead>
              <tbody>
                {students.map((student) => {
                  const row = rows[student.courseEnrollmentId]
                  const inError = partError(row.internal, inMax)
                  const exError = partError(row.external, exMax)
                  const complete = row.internal.trim() !== '' && row.external.trim() !== '' && !inError && !exError
                  const total = complete ? Number(row.internal) + Number(row.external) : null
                  return (
                    <tr key={student.courseEnrollmentId} className="border-t border-line align-top">
                      <th scope="row" className="px-4 py-3 font-normal">{student.studentLoginId}</th>
                      <td className="px-4 py-2">
                        <TextField label={`Internal marks of ${student.studentLoginId}`} className="w-28 [&>label]:sr-only" inputMode="decimal"
                          value={row.internal} error={inError} onChange={(event) => set(student.courseEnrollmentId, 'internal', event.target.value)} />
                      </td>
                      <td className="px-4 py-2">
                        <TextField label={`External marks of ${student.studentLoginId}`} className="w-28 [&>label]:sr-only" inputMode="decimal"
                          value={row.external} error={exError} onChange={(event) => set(student.courseEnrollmentId, 'external', event.target.value)} />
                      </td>
                      <td className="px-4 py-3 text-center font-semibold">{total === null ? '—' : plain(total)}</td>
                      <td className="px-4 py-3 text-center font-semibold">{total === null ? '—' : (gradeFor(scale, total, inMax + exMax) ?? '—')}</td>
                      <td className="px-4 py-3">
                        {student.result ? <Badge tone={student.result.status === 'PUBLISHED' ? 'success' : 'warning'}>{student.result.status === 'PUBLISHED' ? 'Published' : 'Draft'}</Badge> : <Badge>Not saved</Badge>}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <Button variant="secondary" disabled={busy || errors || !maxValid} onClick={saveAll}>{busy ? 'Saving…' : 'Save Draft'}</Button>
          <Button disabled={busy || drafts.length === 0} onClick={publishAll}>Publish Results</Button>
        </div>
        <div role="status" className="mt-3">
          {message && <Alert tone={message.tone === 'success' ? 'success' : 'error'}>{message.text}</Alert>}
        </div>
      </Card>
    </>
  )
}

/** One offering's marks sheet: internal and external marks per student, saved as drafts, then published together. */
export function ErpMarksEntryPage() {
  const { courseOfferingId } = useParams()
  const id = Number(courseOfferingId)
  const roster = useApiQuery(queryKeys.results.course(id), (signal) => getCourseResults(id, signal))
  const scale = useApiQuery(queryKeys.erp.gradeScale(), getGradeScale)

  return (
    <>
      <title>Marks Entry · Stacc</title>
      <PageHeader title="Marks Entry" description="Enter and update internal and external marks for course results." />
      {roster.status !== 'loaded' || scale.status !== 'loaded' ? (
        <SectionState request={roster.status !== 'loaded' ? roster : scale} what="the class's marks" />
      ) : (
        <>
          <div className="mb-6 flex items-center gap-4 rounded-panel border border-line bg-surface px-5 py-4">
            <span aria-hidden="true" className="h-12 w-1.5 rounded bg-portal-cta" />
            <div>
              <p className="text-2xl font-bold text-ink">{`${roster.data.courseCode}: ${roster.data.courseName}`}</p>
              <p className="text-ink-muted">{`${roster.data.programCode} · Semester ${roster.data.semesterNumber} · ${roster.data.academicSessionCode}`}</p>
            </div>
          </div>
          <Sheet key={roster.data.courseOfferingId} roster={roster.data} scale={scale.data} courseOfferingId={id} />
        </>
      )}
    </>
  )
}
