import type { LmsContentStatus } from '../types/lms'

const LABELS: Record<LmsContentStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  ARCHIVED: 'Archived',
}

type StatusTagProps = {
  status: LmsContentStatus
  label: string
}

const TONES: Record<string, string> = {
  PUBLISHED: 'bg-sky-100 text-portal-navy',
  DRAFT: 'bg-slate-100 text-ink',
}

export function StatusTag({ status, label }: StatusTagProps) {
  return (
    <span className={`inline-block shrink-0 rounded-control px-2 py-0.5 text-sm font-semibold ${TONES[status] ?? 'bg-slate-100 text-ink-muted'}`}>
      <span className="sr-only">{label}: </span>
      {LABELS[status] ?? status}
    </span>
  )
}
