import type { ApprovalStep } from '../types/approvals'
import { Timeline, type TimelineTone } from '../ui'
import { approvalTagClasses, stepStatusLabel } from './approvalText'

function toneOf(status: ApprovalStep['status']): TimelineTone {
  switch (status) {
    case 'APPROVED':
      return 'done'
    case 'REJECTED':
      return 'danger'
    case 'PENDING':
      return 'current'
    case 'WAITING':
      return 'waiting'
    default:
      return 'neutral'
  }
}

export function ApprovalSteps({ steps }: { steps: ApprovalStep[] }) {
  return (
    <Timeline
      label="Approval steps"
      empty="No approval steps are available."
      entries={steps.map((step) => ({
        key: step.stepOrder,
        title: step.stepName,
        badge: <span className={approvalTagClasses(step.status)}>{stepStatusLabel(step.status)}</span>,
        actor: step.actedByLoginId ? `${step.status === 'REJECTED' ? 'Rejected' : 'Approved'} by ${step.actedByLoginId}` : null,
        at: step.actedByLoginId ? step.actedAt : null,
        tone: toneOf(step.status),
        detail: step.comment ? (
          <div className="rounded-control border border-line bg-surface-muted px-3 py-2">
            <p className="text-xs font-medium text-ink-muted">{step.status === 'REJECTED' ? 'Rejection note' : 'Comment'}</p>
            <p className="mt-0.5 break-words whitespace-pre-wrap text-ink">{step.comment}</p>
          </div>
        ) : undefined,
      }))}
    />
  )
}
