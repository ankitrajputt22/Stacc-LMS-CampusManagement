import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react'
import { getUnreadNotificationCount } from '../api/notificationsApi'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { UnreadCountContext } from './unreadCount'

/**
 * Keeps the signed-in account's unread count for the whole app shell. It asks once when the shell appears and again
 * whenever something calls refresh, such as the Inbox after a change. There is no polling and no live connection. An
 * account without a role that has an Inbox asks nothing; a refused or failed answer just hides the badge.
 */
export function UnreadCountProvider({ children }: { children: ReactNode }) {
  const { account } = useAuth()
  const hasInbox = hasAnyRole(account?.roles ?? [], ['STUDENT', 'FACULTY', 'ADMIN'])
  const [unreadCount, setUnreadCount] = useState<number | null>(null)
  const [requests, setRequests] = useState(0)
  const refresh = useCallback(() => setRequests((previous) => previous + 1), [])

  useEffect(() => {
    if (!hasInbox) {
      return
    }
    const controller = new AbortController()
    getUnreadNotificationCount(controller.signal).then(
      (answer) => setUnreadCount(answer.unreadCount),
      () => {
        if (!controller.signal.aborted) {
          setUnreadCount(null)
        }
      },
    )
    return () => controller.abort()
  }, [hasInbox, account?.accountId, requests])

  const value = useMemo(() => ({ unreadCount: hasInbox ? unreadCount : null, refresh }), [hasInbox, unreadCount, refresh])
  return <UnreadCountContext.Provider value={value}>{children}</UnreadCountContext.Provider>
}
