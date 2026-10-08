import type { AuthSession } from './types'

// Keeps the signed-in session for this browser tab, and nothing else.
//
// sessionStorage is used on purpose: the session survives a page reload, belongs to one tab,
// and is gone when the tab is closed. Only the access token and the account's ID, login ID,
// and roles are stored. A password is never stored, and neither is any secret.

const STORAGE_KEY = 'stacc.auth.session'

// undefined means the storage has not been read yet in this page load.
let current: AuthSession | null | undefined

function isAuthSession(value: unknown): value is AuthSession {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Record<string, unknown>

  return (
    typeof candidate.accessToken === 'string' &&
    candidate.accessToken !== '' &&
    typeof candidate.accountId === 'number' &&
    typeof candidate.loginId === 'string' &&
    Array.isArray(candidate.roles) &&
    candidate.roles.every((role) => typeof role === 'string') &&
    typeof candidate.expiresAt === 'number'
  )
}

function readStorage(): AuthSession | null {
  try {
    const saved = window.sessionStorage.getItem(STORAGE_KEY)
    if (saved === null) {
      return null
    }

    const parsed: unknown = JSON.parse(saved)
    if (isAuthSession(parsed)) {
      return parsed
    }
  } catch {
    // Unreadable or damaged storage is treated like no session at all.
  }

  removeFromStorage()
  return null
}

function removeFromStorage() {
  try {
    window.sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nothing more can be done if the browser refuses storage access.
  }
}

/** The saved session, or null. Anything that does not look like a session is discarded. */
export function loadSession(): AuthSession | null {
  if (current === undefined) {
    current = readStorage()
  }
  return current
}

export function saveSession(session: AuthSession): void {
  current = session
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // The session still works until the page is reloaded.
  }
}

export function clearSession(): void {
  current = null
  removeFromStorage()
}
