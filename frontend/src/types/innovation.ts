// The innovation API exactly as the backend sends it (InnovationService). Submitters get reviewers' comments only once
// decided, without names or scores (reviewerLoginId null, score 0); reviewers get only their own review.

export type IdeaStatus = 'SUBMITTED' | 'SHORTLISTED' | 'ACCEPTED' | 'NOT_SELECTED' | 'WITHDRAWN'

export type IdeaReview = { reviewerLoginId: string | null; score: number; comment: string; reviewedAt: string }

export type Idea = {
  ideaId: number
  title: string
  /** Plain text, as is the solution. */
  problem: string
  solution: string
  status: IdeaStatus
  submitterLoginId: string | null
  submittedAt: string
  decisionNote: string | null
  decidedAt: string | null
  reviews: IdeaReview[]
}
