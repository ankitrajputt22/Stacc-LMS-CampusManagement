export type GroupKind = 'CLUB' | 'SPORT'
export type JoinPolicy = 'OPEN' | 'APPROVAL' | 'CLOSED'
export type OfficerTitle = 'ADVISOR' | 'COACH' | 'PRESIDENT' | 'SECRETARY' | 'OFFICER'

export type CampusGroup = {
  groupId: number
  code: string
  name: string
  kind: GroupKind
  description: string | null
  joinPolicy: JoinPolicy
  active: boolean
  members: number
  officers: { officerId: number; loginId: string; title: OfficerTitle }[]
  /** PENDING or ACTIVE when the signed-in account has asked or belongs; null otherwise. */
  myMembership: 'PENDING' | 'ACTIVE' | null
  iAmOfficer: boolean
}

export type GroupMember = { membershipId: number; loginId: string; status: 'PENDING' | 'ACTIVE'; requestedAt: string }

export type FacilityKind = 'ROOM' | 'LAB' | 'HALL' | 'COURT' | 'GROUND' | 'OTHER'

export type CampusFacility = {
  facilityId: number
  code: string
  name: string
  kind: FacilityKind
  location: string | null
  capacity: number | null
  /** Daily hours in the college's time zone, such as 08:00. */
  opensAt: string
  closesAt: string
  needsApproval: boolean
  active: boolean
  closures: { closureId: number; startDate: string; endDate: string; reason: string }[]
}

export type FacilityBooking = {
  bookingId: number
  facilityId: number
  facilityName: string
  bookedByLoginId: string
  startsAt: string
  endsAt: string
  purpose: string
  status: 'CONFIRMED' | 'PENDING' | 'REJECTED' | 'CANCELLED'
}

export type CampusEvent = {
  eventId: number
  title: string
  description: string | null
  groupId: number | null
  groupName: string | null
  organizerLoginId: string
  venue: string | null
  startsAt: string
  endsAt: string
  capacity: number | null
  registrationOpensAt: string | null
  registrationClosesAt: string | null
  forStudents: boolean
  forFaculty: boolean
  membersOnly: boolean
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'PUBLISHED' | 'CANCELLED'
  registered: number
  waitlisted: number
  myRegistration: 'REGISTERED' | 'WAITLISTED' | null
  myWaitlistPosition: number | null
  /** Why the signed-in account cannot register, in words from the backend; empty when it can. */
  cannotRegisterBecause: string[]
  canManage: boolean
}

export type EventInput = {
  title: string
  description: string | null
  groupId: number | null
  venue: string | null
  startsAt: string
  endsAt: string
  capacity: number | null
  registrationOpensAt: string | null
  registrationClosesAt: string | null
  forStudents: boolean
  forFaculty: boolean
  membersOnly: boolean
}

export type EventAttendee = { loginId: string; status: 'REGISTERED' | 'WAITLISTED'; registeredAt: string }

export type GroupSession = {
  sessionId: number
  groupId: number
  groupName: string
  kind: 'MEETING' | 'PRACTICE'
  title: string
  location: string | null
  startsAt: string
  endsAt: string
  status: 'SCHEDULED' | 'CANCELLED'
}

export type GroupAnnouncement = { announcementId: number; title: string; message: string; createdAt: string }

/** A group's sessions and announcements; canManage when the account may add them (officers, the campus office). */
export type GroupActivity = { canManage: boolean; sessions: GroupSession[]; announcements: GroupAnnouncement[] }

export type EventCheckInCode = { code: string; validFrom: string; validUntil: string }
/** certificateEligible: attended and the event has ended. */
export type MyEventAttendance = { registered: boolean; attendedAt: string | null; certificateEligible: boolean }
export type EventAttendanceRow = {
  loginId: string
  status: string
  registeredAt: string
  attendedAt: string | null
  checkInMethod: 'MANUAL' | 'PASS' | null
  certificateEligible: boolean
}
export type EventCheckIn = { loginId: string; attendedAt: string; alreadyCheckedIn: boolean }

// --- Fests (FestService views)

export type FestStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type VolunteerStatus = 'APPLIED' | 'ACCEPTED' | 'DECLINED' | 'WITHDRAWN'

export type FestScheduleItem = { eventId: number; title: string; venue: string | null; startsAt: string; endsAt: string; status: string }

export type FestShift = {
  shiftId: number
  roleTitle: string
  startsAt: string
  endsAt: string
  places: number
  accepted: number
  myApplicationId: number | null
  myApplication: VolunteerStatus | null
}

export type CampusFest = {
  festId: number
  name: string
  /** Plain text. */
  description: string | null
  startsOn: string
  endsOn: string
  status: FestStatus
  schedule: FestScheduleItem[]
  shifts: FestShift[]
}

export type FestInput = { name: string; description: string | null; startsOn: string; endsOn: string }

export type FestApplication = {
  applicationId: number
  shiftId: number
  roleTitle: string
  loginId: string
  note: string | null
  status: VolunteerStatus
  appliedAt: string
}
