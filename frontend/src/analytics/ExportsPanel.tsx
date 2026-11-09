import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { downloadExport, getMyExports, startExport } from '../api/analyticsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { useFileDownload } from '../courses/useFileDownload'
import { failureText } from '../engagement/engagementText'
import type { ExportKind } from '../types/analytics'
import { Alert, Badge, Button, Card } from '../ui'

const STATUS = { QUEUED: 'Waiting', RUNNING: 'Being made', READY: 'Ready', FAILED: 'Failed', EXPIRED: 'Expired' } as const

/**
 * Background XLSX exports: start one, then download it once ready (until it expires after a day). The list loads again
 * only when the person presses Refresh; nothing polls.
 */
export function ExportsPanel({ kind, parameter, label }: { kind: ExportKind; parameter: number | null; label: string }) {
  const queryClient = useQueryClient()
  const exports = useApiQuery(queryKeys.analytics.exports(), (signal) => getMyExports(signal))
  const downloads = useFileDownload(downloadExport)
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const mine = exports.status === 'loaded' ? exports.data.filter((e) => e.kind === kind && (parameter === null || e.parameter === parameter)) : []
  return (
    <Card
      title="Exports"
      description="Made in the background as an Excel file; each download is recorded. Files expire after a day."
      action={
        <Button size="sm" variant="secondary" onClick={() => void queryClient.invalidateQueries({ queryKey: queryKeys.analytics.exports() })}>
          Refresh
        </Button>
      }
    >
      <div className="space-y-3 text-sm">
        <Button
          onClick={() =>
            void startExport(kind, parameter)
              .then(() => {
                setMessage({ tone: 'success', text: 'Export started. Refresh in a moment to download it.' })
                return queryClient.invalidateQueries({ queryKey: queryKeys.analytics.exports() })
              })
              .catch((caught: unknown) => setMessage({ tone: 'error', text: failureText(caught, 'Couldn’t start the export.') ?? '' }))
          }
        >
          {label}
        </Button>
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        {exports.status !== 'loaded' ? (
          <SectionState request={exports} what="exports" />
        ) : mine.length === 0 ? (
          <p className="text-ink-muted">No exports yet.</p>
        ) : (
          <ul className="divide-y divide-line">
            {mine.map((item) => (
              <li key={item.exportId} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <span>
                  <Badge tone={item.status === 'READY' ? 'success' : item.status === 'FAILED' ? 'danger' : 'neutral'}>{STATUS[item.status]}</Badge>
                  <span className="ml-2 text-ink-muted">Started {formatDateTime(item.createdAt)}</span>
                  {item.failure && <span className="ml-2 text-danger">{item.failure}</span>}
                </span>
                {item.status === 'READY' && item.fileName && (
                  <Button size="sm" variant="secondary" onClick={() => void downloads.download({ id: item.exportId, originalFileName: item.fileName ?? 'export.xlsx' })}>
                    Download
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  )
}
