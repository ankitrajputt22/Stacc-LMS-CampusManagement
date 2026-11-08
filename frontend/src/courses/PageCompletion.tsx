import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { completePage } from '../api/lmsContentApi'
import { queryKeys } from '../api/queryKeys'
import { Alert, Badge, Button } from '../ui'

type PageCompletionProps = {
  lmsCourseId: number
  pageId: number
  required: boolean
  completed: boolean
}

/**
 * The student's own completion of a page: Completed, or Mark as complete. Marking is recorded once by the backend and
 * cannot be undone; opening the page records nothing. A refusal (such as a module that became locked) is shown here.
 */
export function PageCompletion({ lmsCourseId, pageId, required, completed }: PageCompletionProps) {
  const queryClient = useQueryClient()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function complete() {
    if (busy) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await completePage(lmsCourseId, pageId)
      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        const message = getApiError(failure)?.message
        setError(message && status !== null && status < 500 ? message : 'Couldn’t save. Check your connection and try again.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="Your progress on this page" className="mt-6 rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-ink">
          {required ? 'This page is required for your progress in the course.' : 'This page is optional.'}
        </p>
        {completed ? (
          <Badge tone="success">Completed</Badge>
        ) : (
          <Button aria-disabled={busy || undefined} onClick={() => void complete()}>
            {busy ? 'Saving…' : 'Mark as complete'}
          </Button>
        )}
      </div>
      {error && (
        <Alert tone="error" className="mt-3">
          {error}
        </Alert>
      )}
    </section>
  )
}
