import { formatDateTime } from '../courses/dates'
import type { ApprovalStep } from '../types/approvals'
import { approvalTagClasses, stepStatusLabel } from './hostelText'

/**
 * The approval progress, step by step, as the approval engine reports it: the step's name, its status in words, who
 * decided and when, and the comment as plain text. A rejected step's comment is shown as the rejection note.
 */
export function ApprovalSteps({ steps }: { steps: ApprovalStep[] }) {
  if (steps.length === 0) {
    return <p className="text-sm text-ink-muted">No approval steps are available.</p>
  }
  return (
    <ol className="space-y-3">
      {steps.map((step) => (
        <li key={step.stepOrder} className="text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium text-ink">{step.stepName}</span>
            <span className={approvalTagClasses(step.status)}>{stepStatusLabel(step.status)}</span>
          </div>
          {step.actedByLoginId && step.actedAt && (
            <p className="mt-0.5 text-xs text-ink-muted">
              {step.status === 'REJECTED' ? 'Rejected' : 'Approved'} by {step.actedByLoginId} ·{' '}
              <time dateTime={step.actedAt}>{formatDateTime(step.actedAt)}</time>
            </p>
          )}
          {step.comment && (
            <div className="mt-1 rounded-control border border-line bg-surface-muted px-3 py-2">
              <p className="text-xs font-medium text-ink-muted">{step.status === 'REJECTED' ? 'Rejection note' : 'Comment'}</p>
              {/* Plain text: React shows it as text, never as HTML. */}
              <p className="mt-0.5 break-words whitespace-pre-wrap text-ink">{step.comment}</p>
            </div>
          )}
        </li>
      ))}
    </ol>
  )
}
