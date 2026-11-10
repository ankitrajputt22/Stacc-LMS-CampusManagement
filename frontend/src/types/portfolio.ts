// The portfolio API exactly as the backend sends it (PortfolioService). Records are read live from their sources.

export type PortfolioKind = 'ACHIEVEMENT' | 'PROJECT' | 'COURSE' | 'SKILL' | 'SERVICE' | 'OTHER'
export type PortfolioVerification = 'NONE' | 'REQUESTED' | 'VERIFIED' | 'DECLINED'

export type PortfolioRecord = { source: 'DOCUMENT' | 'EVENT_ATTENDED' | 'VOLUNTEERED' | 'TEAM'; title: string; detail: string; at: string | null }

export type PortfolioItem = {
  itemId: number
  kind: PortfolioKind
  title: string
  /** Plain text. */
  description: string | null
  occurredOn: string | null
  /** Always one https:// address. */
  link: string | null
  verification: PortfolioVerification
  verifierLoginId: string | null
  verificationNote: string | null
  decidedAt: string | null
}

export type Portfolio = { records: PortfolioRecord[]; items: PortfolioItem[] }

export type PortfolioItemInput = { kind: PortfolioKind; title: string; description: string | null; occurredOn: string | null; link: string | null }

export type WaitingPortfolioItem = {
  itemId: number
  ownerLoginId: string
  kind: PortfolioKind
  title: string
  description: string | null
  occurredOn: string | null
  link: string | null
  requestedAt: string
}
