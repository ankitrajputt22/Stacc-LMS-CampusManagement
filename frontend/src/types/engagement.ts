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
