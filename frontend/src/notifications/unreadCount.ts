import { createContext, useContext } from 'react'

export type UnreadCountValue = {
  unreadCount: number | null
  refresh: () => void
}

export const UnreadCountContext = createContext<UnreadCountValue>({ unreadCount: null, refresh: () => {} })

export function useUnreadCount(): UnreadCountValue {
  return useContext(UnreadCountContext)
}

export function badgeText(count: number): string {
  return count > 99 ? '99+' : String(count)
}
