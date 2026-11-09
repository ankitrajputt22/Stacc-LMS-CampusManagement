import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { getMyApprovalTasks, getOverdueOutings } from '../../api/outingsApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { DecisionForm } from '../../approvals/DecisionForm'
import { EmptyState } from '../../components/states/EmptyState'
import { formatDateTime } from '../../courses/dates'
import { SectionState } from '../../courses/SectionState'
import { Alert, Badge, Card } from '../../ui'
import { KIND_LABEL } from './outingText'

/** The warden's outing requests to decide (through the approval task routes) and the overdue students of their hostels. */
export function WardenOutings() {
  const queryClient = useQueryClient()
  const tasks = useApiQuery(queryKeys.hostel.tasks(), (signal) => getMyApprovalTasks(signal))
  const overdue = useApiQuery(queryKeys.hostel.overdue(), (signal) => getOverdueOutings(signal))
  const [notice, setNotice] = useState<{ text: string; tone: 'success' | 'error' } | null>(null)
  const outingTasks = tasks.status === 'loaded' ? tasks.data.filter((task) => task.workflowCode === 'HOSTEL_OUTING') : []

  return (
    <div className="space-y-6">
      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
      <Card title="Requests to decide" padding="none">
        {tasks.status !== 'loaded' ? (
          <SectionState request={tasks} what="requests" />
        ) : outingTasks.length === 0 ? (
          <EmptyState title="Nothing to decide." description="Gate pass, leave, and extension requests of your residents appear here." />
        ) : (
          <ul className="divide-y divide-line">
            {outingTasks.map((task) => (
              <li key={task.approvalRequestStepId} className="space-y-2 px-4 py-4 sm:px-5">
                <p className="text-sm">
                  <span className="font-semibold text-ink">{task.subject}</span>
                  <span className="text-ink-muted"> · {task.requesterLoginId} · {formatDateTime(task.submittedAt)}</span>
                </p>
                <DecisionForm
                  stepId={task.approvalRequestStepId}
                  idPrefix={`outing-${task.approvalRequestStepId}`}
                  approvedMessage="Approved."
                  rejectedMessage="Rejected."
                  audienceHint="The student sees your decision and any comment."
                  onDecided={(text, tone) => {
                    setNotice({ text, tone: tone ?? 'success' })
                    void queryClient.invalidateQueries({ queryKey: ['hostel'] })
                  }}
                />
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card title="Overdue students" padding="none">
        {overdue.status !== 'loaded' ? (
          <SectionState request={overdue} what="overdue students" />
        ) : overdue.data.length === 0 ? (
          <EmptyState title="Nobody is overdue." description="Students still outside after their pass or leave ended appear here." />
        ) : (
          <ul className="divide-y divide-line text-sm">
            {overdue.data.map((outing) => (
              <li key={outing.outingId} className="flex flex-wrap items-center gap-2 px-4 py-3 sm:px-5">
                <Badge tone="danger">Overdue</Badge>
                <span className="font-medium text-ink">{outing.studentLoginId}</span>
                <span className="text-ink-muted">
                  {KIND_LABEL[outing.kind]} ended {formatDateTime(outing.leaveUntil)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
