export type MySession = {
  sessionId: string
  device: string | null
  signedInAt: string
  lastSeenAt: string
  expiresAt: string
  current: boolean
}
