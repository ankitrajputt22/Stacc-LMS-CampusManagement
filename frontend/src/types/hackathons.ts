// The hackathon API exactly as the backend sends it (HackathonService). Participants never get scores before the
// results are released; judges get only their own scores.

export type HackathonStatus = 'DRAFT' | 'PUBLISHED' | 'RESULTS_RELEASED' | 'ARCHIVED'

export type HackathonSubmission = { title: string; summary: string; link: string | null; submittedAt: string }
export type HackathonTeam = { teamId: number; name: string; memberLoginIds: string[]; submitted: boolean; submission: HackathonSubmission | null }
export type HackathonResult = { place: number; teamName: string; submissionTitle: string; total: number }

export type Hackathon = {
  hackathonId: number
  title: string
  description: string | null
  startsAt: string
  endsAt: string
  maxTeamSize: number
  status: HackathonStatus
  takingWork: boolean
  myTeamId: number | null
  teams: HackathonTeam[]
  results: HackathonResult[]
}

export type JudgeItem = { submissionId: number; teamName: string; title: string; summary: string; link: string | null; myScore: number | null; myComment: string | null }
export type JudgeHackathon = { hackathonId: number; title: string; endsAt: string; judging: boolean; submissions: JudgeItem[] }

export type OfficeHackathon = {
  hackathonId: number
  title: string
  startsAt: string
  endsAt: string
  maxTeamSize: number
  status: HackathonStatus
  judgeLoginIds: string[]
  teams: number
  standings: HackathonResult[]
}
