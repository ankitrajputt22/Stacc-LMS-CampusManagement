import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { cancelRequest, getMyRequests } from '../api/erpApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { SectionState } from '../courses/SectionState'
import type { RequestKind } from '../types/erp'
import { Alert, Badge, Button } from '../ui'
import { formatDay, KIND_WORDS, refusalOf, STATUS_TONES, STATUS_WORDS } from './requestWords'

/** The student's own requests of these kinds, newest first, with Cancel while still pending. */
export function RequestHistory({ kinds }: { kinds: readonly RequestKind[] }) {
  const queryClient = useQueryClient()
  const requests = useApiQuery(queryKeys.erp.myRequests(), getMyRequests)
  const [error, setError] = useState<string | null>(null)
  if (requests.status !== 'loaded') {
    return <SectionState request={requests} what="your requests" />
  }
  const shown = requests.data.filter((request) => kinds.includes(request.kind))

  async function cancel(requestId: number, reference: string) {
    if (!window.confirm(`Cancel request ${reference}? This cannot be undone.`)) {
      return
    }
    setError(null)
    try {
      await cancelRequest(requestId)
      await queryClient.invalidateQueries({ queryKey: queryKeys.erp.myRequests() })
    } catch (failure) {
      setError(refusalOf(failure))
    }
  }

  return (
    <>
      {error && <Alert tone="error" className="mb-3">{error}</Alert>}
      {shown.length === 0 ? (
        <p className="text-[0.95rem] text-ink-muted">No requests yet.</p>
      ) : (
        <ul className="space-y-3">
          {shown.map((request) => (
            <li key={request.requestId} className="rounded-panel border border-line px-4 py-3">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-lg font-semibold text-ink">{request.reference}</p>
                  <p className="text-ink-muted">
                    {KIND_WORDS[request.kind]}
                    {request.courseCode && ` · ${request.courseCode}`}
                    {request.fromDate && request.toDate && ` · ${formatDay(request.fromDate)} – ${formatDay(request.toDate)}`}
                  </p>
                </div>
                <Badge tone={STATUS_TONES[request.status]}>{STATUS_WORDS[request.status]}</Badge>
              </div>
              {(request.outcome || request.decisionComment) && (
                <p className="mt-2 text-sm text-ink">
                  {[request.outcome, request.decisionComment].filter(Boolean).join(' — ')}
                </p>
              )}
              {request.status === 'SUBMITTED' && (
                <Button variant="ghost" size="sm" className="mt-1 -ml-3" onClick={() => cancel(request.requestId, request.reference)}>
                  Cancel request<span className="sr-only"> {request.reference}</span>
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
