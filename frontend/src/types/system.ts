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

/** AI health (v0.13.7): statuses and numbers only, the last 24 hours for jobs and requests. */
export type AiHealth = {
  enabled: boolean
  reachable: boolean
  courseIndexes: Record<string, number>
  teacherIndexes: Record<string, number>
  jobs: Record<string, number>
  running: number
  waiting: number
  requestsByOutcome: Record<string, number>
  requestsByKind: Record<string, number>
  /** Average time of successful requests in milliseconds, or null when there were none. */
  averageMillis: number | null
  coursesWithStudentAiOff: number
}
