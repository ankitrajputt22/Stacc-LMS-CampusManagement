// The admins' system health view exactly as the backend sends it (SystemHealthService): statuses and numbers only.

export type SystemHealth = {
  status: string
  parts: Record<string, string>
  schemaVersion: string | null
  migrations: number
  mailEnabled: boolean
  aiEnabled: boolean
  metricsEnabled: boolean
  profiles: string[]
  startedAt: string
  uptimeMinutes: number
  heapUsedMb: number
  /** -1 when the JVM sets no maximum. */
  heapMaxMb: number
  processors: number
  threads: number
}
