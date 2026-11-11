// The projects API exactly as the backend sends it (ProjectService). People are shown by login ID only; join request
// messages reach the lead only.

export type ProjectStatus = 'OPEN' | 'CLOSED' | 'ARCHIVED'
export type ProjectMemberStatus = 'REQUESTED' | 'ACTIVE' | 'DECLINED' | 'WITHDRAWN' | 'LEFT'
export type ProjectMentorStatus = 'INVITED' | 'ACCEPTED' | 'DECLINED' | 'ENDED'

export type ProjectMember = { memberId: number; loginId: string; status: ProjectMemberStatus; lead: boolean; message: string | null }
export type ProjectMentor = { mentorId: number; loginId: string; status: ProjectMentorStatus }

export type Project = {
  projectId: number
  title: string
  /** Plain text. */
  summary: string
  leadLoginId: string
  maxMembers: number
  activeMembers: number
  status: ProjectStatus
  iAmLead: boolean
  myMembership: ProjectMemberStatus | null
  myMemberId: number | null
  myMentorship: ProjectMentorStatus | null
  myMentorId: number | null
  members: ProjectMember[]
  mentors: ProjectMentor[]
}
