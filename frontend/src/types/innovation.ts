export type IdeaStatus = 'SUBMITTED' | 'SHORTLISTED' | 'ACCEPTED' | 'NOT_SELECTED' | 'WITHDRAWN'

export type IdeaReview = { reviewerLoginId: string | null; score: number; comment: string; reviewedAt: string }

export type Idea = {
  ideaId: number
  title: string
  problem: string
  solution: string
  status: IdeaStatus
  submitterLoginId: string | null
  submittedAt: string
  decisionNote: string | null
  decidedAt: string | null
  reviews: IdeaReview[]
}
