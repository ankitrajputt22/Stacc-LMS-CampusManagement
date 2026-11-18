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
  heapMaxMb: number
  processors: number
  threads: number
}

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
  averageMillis: number | null
  coursesWithStudentAiOff: number
}
