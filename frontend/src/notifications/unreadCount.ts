import { createContext, useContext } from 'react'

export type UnreadCountValue = {
  /** The unread count from the backend, or null before it has answered or when it could not. */
  unreadCount: number | null
  /** Asks the backend again. Call it after a change to read state. */
  refresh: () => void
}

export const UnreadCountContext = createContext<UnreadCountValue>({ unreadCount: null, refresh: () => {} })

/** The one unread count shared by the navigation badge and the Inbox, so they never disagree. */
export function useUnreadCount(): UnreadCountValue {
  return useContext(UnreadCountContext)
}

/** "3", or "99+" from a hundred on. Nothing is shown for 0. */
export function badgeText(count: number): string {
  return count > 99 ? '99+' : String(count)
}
