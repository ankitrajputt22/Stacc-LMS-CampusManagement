import { type RefObject, useCallback, useEffect, useRef } from 'react'

export function usePendingFocus(container: RefObject<HTMLElement | null>) {
  const pending = useRef<string | null>(null)

  useEffect(() => {
    const key = pending.current
    if (key === null) {
      return
    }
    const target = [...(container.current?.querySelectorAll<HTMLElement>(`[data-focus-key="${key}"]`) ?? [])].find(
      (element) => element.offsetParent !== null,
    )
    if (target) {
      target.focus()
      pending.current = null
    }
  })

  return useCallback((key: string) => {
    pending.current = key
  }, [])
}
