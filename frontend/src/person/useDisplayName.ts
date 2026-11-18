import { getMyProfile } from '../api/personApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'

export function useMyProfile() {
  const { isAuthenticated } = useAuth()
  return useApiQuery(isAuthenticated ? queryKeys.me.profile() : null, getMyProfile)
}

export function useDisplayName(): string | null {
  const { account } = useAuth()
  const profile = useMyProfile()
  if (account === null) {
    return null
  }
  return profile.status === 'loaded' ? profile.data.displayName : account.loginId
}

export function initialsOf(name: string): string {
  const words = name.split(/\s+/).filter((word) => /\p{L}/u.test(word))
  if (words.length === 0) {
    return name.replace(/[^\p{L}\p{N}]/gu, '').slice(0, 2).toUpperCase()
  }
  const first = Array.from(words[0])[0] ?? ''
  const last = words.length > 1 ? (Array.from(words[words.length - 1])[0] ?? '') : (Array.from(words[0])[1] ?? '')
  return (first + last).toUpperCase()
}
