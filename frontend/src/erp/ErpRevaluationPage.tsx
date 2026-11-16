import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getMyStanding, requestRevaluation } from '../api/erpApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { SectionState } from '../courses/SectionState'
import { Alert, Button, Card, PageHeader, SelectField, TextAreaField } from '../ui'
import { ExamsTabs } from './ExamsTabs'
import { plain } from './examFormat'
import { RequestHistory } from './RequestHistory'
import { refusalOf } from './requestWords'

/** Exams & Results → Revaluation: ask for a published result to be checked again, and follow the requests. */
export function ErpRevaluationPage() {
  const queryClient = useQueryClient()
  const standing = useApiQuery(queryKeys.erp.standing(), getMyStanding)
  const [resultId, setResultId] = useState('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)
  const courses = standing.status === 'loaded' ? standing.data.semesters.flatMap((semester) => semester.courses) : []

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) {
      return
    }
    setBusy(true)
    setError(null)
    setSent(false)
    try {
      await requestRevaluation({ courseResultId: Number(resultId), reason: reason.trim() })
      await queryClient.invalidateQueries({ queryKey: queryKeys.erp.myRequests() })
      setResultId('')
      setReason('')
      setSent(true)
    } catch (failure) {
      setError(refusalOf(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <title>Revaluation · Stacc</title>
      <PageHeader title="Exams & Results" description="Examination schedule, results, grade cards and revaluation." />
      <ExamsTabs />
      <div className="grid gap-6 xl:grid-cols-2">
        <Card title="Apply for revaluation" icon="exams">
          {standing.status !== 'loaded' ? (
            <SectionState request={standing} what="your results" />
          ) : courses.length === 0 ? (
            <p className="text-[0.95rem] text-ink-muted">You can apply once a result has been published.</p>
          ) : (
            <form onSubmit={submit} className="space-y-4">
              <SelectField label="Course" required value={resultId} onChange={(event) => setResultId(event.target.value)}>
                <option value="">Choose a published result</option>
                {courses.map((course) => (
                  <option key={course.courseResultId} value={course.courseResultId}>
                    {`${course.courseCode} · ${course.courseName} · ${plain(course.marksObtained)} / ${plain(course.maxMarks)}`}
                  </option>
                ))}
              </SelectField>
              <TextAreaField label="Reason" required maxLength={1000} rows={4} value={reason} onChange={(event) => setReason(event.target.value)}
                hint="Say which answers you would like checked again. The office decides and tells you in your Inbox." />
              <Button type="submit" disabled={busy}>{busy ? 'Submitting…' : 'Submit request'}</Button>
              <div role="status">{sent && <p className="text-sm text-success">Your revaluation request was sent.</p>}</div>
              {error && <Alert tone="error">{error}</Alert>}
            </form>
          )}
        </Card>
        <Card title="Your revaluation requests" icon="documents">
          <RequestHistory kinds={['REVALUATION']} />
        </Card>
      </div>
    </>
  )
}
