import { useCallback, useEffect, useRef, useState } from 'react'
import { type AiFailure, aiFailure } from './aiText'

export type AiActionState<T> =
  | { status: 'idle' }
  | { status: 'running' }
  | { status: 'done'; data: T }
  | { status: 'failed'; failure: AiFailure }

/**
 * One AI request started by a button, never by typing. A new request replaces the previous one, a request still
 * running when the page closes is cancelled, and nothing is kept once the page is left.
 */
export function useAiAction<T>(audience: 'student' | 'faculty') {
  const [state, setState] = useState<AiActionState<T>>({ status: 'idle' })
  const controller = useRef<AbortController | null>(null)

  useEffect(() => () => controller.current?.abort(), [])

  const run = useCallback(
    async (request: (signal: AbortSignal) => Promise<T>): Promise<T | null> => {
      controller.current?.abort()
      const current = new AbortController()
      controller.current = current
      setState({ status: 'running' })
      try {
        const data = await request(current.signal)
        if (!current.signal.aborted) {
          setState({ status: 'done', data })
        }
        return data
      } catch (error) {
        if (!current.signal.aborted) {
          const failure = aiFailure(error, audience)
          setState(failure === null ? { status: 'idle' } : { status: 'failed', failure })
        }
        return null
      }
    },
    [audience],
  )

  return { state, run }
}
