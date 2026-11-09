export type IdCard = {
  name: string
  loginId: string
  roles: string[]
  program: string | null
  department: string | null
  code: string
  codeValidUntil: string
}

export type IdCheck = {
  valid: boolean
  message: string
  name: string | null
  loginId: string | null
  roles: string[]
  program: string | null
  department: string | null
}

export type VaultDocument = {
  documentId: number
  origin: 'ISSUED' | 'UPLOADED'
  title: string
  fileName: string
  sizeBytes: number
  createdAt: string
  issuedByLoginId: string | null
  /** For issued documents: the code anyone can check on the public verification page. */
  verificationCode: string | null
  withdrawn: boolean
}

export type CertificateType = 'BONAFIDE' | 'TRANSCRIPT' | 'CHARACTER' | 'COURSE_COMPLETION' | 'OTHER'

export type CertificateRequest = {
  requestId: number
  requesterLoginId: string
  type: CertificateType
  purpose: string
  status: 'REQUESTED' | 'ISSUED' | 'REFUSED' | 'CANCELLED'
  decisionNote: string | null
  createdAt: string
  decidedAt: string | null
}

export type Authenticity = { authentic: boolean; message: string; title: string | null; issuedAt: string | null }

export type CareerProfile = {
  headline: string | null
  skills: string | null
  links: string | null
  resumes: { resumeId: number; label: string; fileName: string; createdAt: string; archived: boolean }[]
}

export type OpportunityKind = 'PLACEMENT' | 'INTERNSHIP' | 'RESEARCH' | 'SCHOLARSHIP'

export type Opportunity = {
  opportunityId: number
  kind: OpportunityKind
  title: string
  company: string | null
  organization: string | null
  description: string | null
  deadline: string
  status: 'DRAFT' | 'OPEN' | 'CLOSED'
  stages: string[]
  rounds: { stageIndex: number; stage: string; startsAt: string; venue: string | null }[]
  eligibleProgramIds: number[]
  notEligibleBecause: string[]
  myApplicationStatus: string | null
  applicants: number
}

export type CareerApplication = {
  applicationId: number
  opportunityId: number
  opportunityTitle: string
  company: string | null
  applicantLoginId: string
  resumeLabel: string | null
  shareConsent: boolean
  stage: string
  status: 'APPLIED' | 'IN_PROGRESS' | 'SELECTED' | 'REJECTED' | 'WITHDRAWN'
  appliedAt: string
  offer: { offerId: number; roleTitle: string; compensation: string | null; respondBy: string | null; status: 'MADE' | 'ACCEPTED' | 'DECLINED' } | null
}

export type Company = { companyId: number; name: string; website: string | null; description: string | null; active: boolean }
