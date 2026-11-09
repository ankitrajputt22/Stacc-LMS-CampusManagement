/** One open sign-in of the signed-in account, from GET /api/v1/me/sessions. */
export type MySession = {
  sessionId: string
  /** Such as "Chrome on Android". */
  device: string | null
  signedInAt: string
  lastSeenAt: string
  expiresAt: string
  /** The session making the request: this browser. */
  current: boolean
}
