export type ActionOwner = 'MENTOR' | 'MENTEE'

export type MentoringPairing = {
  assignmentId: number
  mentorLoginId: string
  menteeLoginId: string
  iAmMentor: boolean
  active: boolean
  startedAt: string
  endedAt: string | null
}

export type MentoringActionItem = { itemId: number; description: string; owner: ActionOwner; dueAt: string | null; completedAt: string | null }

export type MentoringMeeting = {
  meetingId: number
  title: string
  heldAt: string
  sharedNotes: string | null
  privateNotes: string | null
  actionItems: MentoringActionItem[]
}

export type MentoringPairingDetail = { pairing: MentoringPairing; meetings: MentoringMeeting[] }
