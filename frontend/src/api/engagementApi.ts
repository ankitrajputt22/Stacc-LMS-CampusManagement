import type {
  CampusEvent,
  CampusFacility,
  CampusFest,
  FestApplication,
  FestInput,
  FixtureInput,
  FixtureResult,
  CampusGroup,
  CampusTeam,
  EventAttendanceRow,
  EventAttendee,
  EventCheckIn,
  EventCheckInCode,
  EventInput,
  FacilityBooking,
  FacilityKind,
  GroupActivity,
  GroupAnnouncement,
  GroupKind,
  GroupMember,
  GroupTeams,
  GroupSession,
  JoinPolicy,
  MyEventAttendance,
  OfficerTitle,
  TeamOption,
  TeamRole,
  Tournament,
  TrialOutcome,
} from '../types/engagement'
import { apiClient } from './apiClient'

// The signed-in account is the one in the session. The backend checks officers, organizers, and bookers per record.
const BASE = '/engagement'

export const getGroups = async (signal?: AbortSignal) => (await apiClient.get<CampusGroup[]>(`${BASE}/groups`, { signal })).data
export const joinGroup = async (groupId: number) => (await apiClient.post<CampusGroup>(`${BASE}/groups/${groupId}/join`)).data
export const leaveGroup = async (groupId: number) => (await apiClient.post<CampusGroup>(`${BASE}/groups/${groupId}/leave`)).data
export const getGroupMembers = async (groupId: number, signal?: AbortSignal) =>
  (await apiClient.get<GroupMember[]>(`${BASE}/groups/${groupId}/members`, { signal })).data
export const decideMembership = async (membershipId: number, accept: boolean) =>
  (await apiClient.post<GroupMember[]>(`${BASE}/memberships/${membershipId}/decision`, { accept })).data
export const createGroup = async (body: { code: string; name: string; kind: GroupKind; description: string | null; joinPolicy: JoinPolicy }) =>
  (await apiClient.post<CampusGroup>(`${BASE}/admin/groups`, body)).data
export const updateGroup = async (
  groupId: number,
  body: { name: string; kind: GroupKind; description: string | null; joinPolicy: JoinPolicy; active: boolean },
) => (await apiClient.put<CampusGroup>(`${BASE}/admin/groups/${groupId}`, body)).data
export const addOfficer = async (groupId: number, loginId: string, title: OfficerTitle) =>
  (await apiClient.post<CampusGroup>(`${BASE}/admin/groups/${groupId}/officers`, { loginId, title })).data
export const endOfficer = async (officerId: number) => (await apiClient.post<CampusGroup>(`${BASE}/admin/officers/${officerId}/end`)).data

export const getFacilities = async (signal?: AbortSignal) => (await apiClient.get<CampusFacility[]>(`${BASE}/facilities`, { signal })).data
export const getFacilityBookings = async (facilityId: number, signal?: AbortSignal) =>
  (await apiClient.get<FacilityBooking[]>(`${BASE}/facilities/${facilityId}/bookings`, { signal })).data
export const bookFacility = async (facilityId: number, body: { startsAt: string; endsAt: string; purpose: string }) =>
  (await apiClient.post<FacilityBooking>(`${BASE}/facilities/${facilityId}/bookings`, body)).data
export const getMyBookings = async (signal?: AbortSignal) => (await apiClient.get<FacilityBooking[]>(`${BASE}/bookings/mine`, { signal })).data
export const cancelBooking = async (bookingId: number) => (await apiClient.post<FacilityBooking>(`${BASE}/bookings/${bookingId}/cancel`)).data
export const createFacility = async (body: {
  code: string
  name: string
  kind: FacilityKind
  location: string | null
  capacity: number | null
  opensAt: string
  closesAt: string
  needsApproval: boolean
}) => (await apiClient.post<CampusFacility>(`${BASE}/admin/facilities`, body)).data
export const closeFacility = async (facilityId: number, body: { startDate: string; endDate: string; reason: string }) =>
  (await apiClient.post<CampusFacility>(`${BASE}/admin/facilities/${facilityId}/closures`, body)).data

export const getEvents = async (signal?: AbortSignal) => (await apiClient.get<CampusEvent[]>(`${BASE}/events`, { signal })).data
export const getManagedEvents = async (signal?: AbortSignal) => (await apiClient.get<CampusEvent[]>(`${BASE}/events/managed`, { signal })).data
export const getEventAttendees = async (eventId: number, signal?: AbortSignal) =>
  (await apiClient.get<EventAttendee[]>(`${BASE}/events/${eventId}/attendees`, { signal })).data
export const createEvent = async (body: EventInput) => (await apiClient.post<CampusEvent>(`${BASE}/events`, body)).data
export const publishEvent = async (eventId: number) => (await apiClient.post<CampusEvent>(`${BASE}/events/${eventId}/publish`)).data
export const cancelEvent = async (eventId: number) => (await apiClient.post<CampusEvent>(`${BASE}/events/${eventId}/cancel`)).data
export const registerForEvent = async (eventId: number) => (await apiClient.post<CampusEvent>(`${BASE}/events/${eventId}/registration`)).data
export const unregisterFromEvent = async (eventId: number) =>
  (await apiClient.post<CampusEvent>(`${BASE}/events/${eventId}/registration/cancel`)).data

// --- Group sessions and announcements

export async function getGroupActivity(groupId: number, signal?: AbortSignal) {
  return (await apiClient.get<GroupActivity>(`${BASE}/groups/${groupId}/activity`, { signal })).data
}

export async function addGroupSession(groupId: number, input: { kind: GroupSession['kind']; title: string; location: string | null; startsAt: string; endsAt: string }) {
  return (await apiClient.post<GroupSession>(`${BASE}/groups/${groupId}/sessions`, input)).data
}

export async function cancelGroupSession(sessionId: number) {
  return (await apiClient.post<GroupSession>(`${BASE}/sessions/${sessionId}/cancel`)).data
}

/** Posts an announcement; active members are told in their Inbox. */
export async function postGroupAnnouncement(groupId: number, title: string, message: string) {
  return (await apiClient.post<GroupAnnouncement>(`${BASE}/groups/${groupId}/announcements`, { title, message })).data
}

/** Scheduled sessions of the account's groups overlapping the range (moments in UTC). */
export async function getMyGroupSessions(from: string, to: string, signal?: AbortSignal) {
  return (await apiClient.get<GroupSession[]>(`${BASE}/me/sessions`, { params: { from, to }, signal })).data
}

// --- Event attendance

/** A fresh check-in code for my registration; earlier codes stop working. */
export async function getEventCheckInCode(eventId: number) {
  return (await apiClient.post<EventCheckInCode>(`${BASE}/events/${eventId}/check-in-code`)).data
}

export async function getMyEventAttendance(eventId: number, signal?: AbortSignal) {
  return (await apiClient.get<MyEventAttendance>(`${BASE}/events/${eventId}/my-attendance`, { signal })).data
}

export async function getEventAttendance(eventId: number, signal?: AbortSignal) {
  return (await apiClient.get<EventAttendanceRow[]>(`${BASE}/events/${eventId}/attendance`, { signal })).data
}

/** Checks an attendee in by their check-in code or college ID. */
export async function checkInEventAttendee(eventId: number, input: { code?: string; loginId?: string }) {
  return (await apiClient.post<EventCheckIn>(`${BASE}/events/${eventId}/check-ins`, input)).data
}

// --- Trials

/** Marks an event the account manages as a trial, before it starts. */
export async function markEventAsTrial(eventId: number) {
  await apiClient.post(`${BASE}/events/${eventId}/trial`)
}

/** Records or corrects Selected or Not selected for a registered attendee, once the trial has started. */
export async function decideTrialOutcome(eventId: number, loginId: string, outcome: TrialOutcome) {
  return (await apiClient.post<EventAttendanceRow>(`${BASE}/events/${eventId}/trial-outcomes`, { loginId, outcome })).data
}

// --- Fests

export const getFests = async (signal?: AbortSignal) => (await apiClient.get<CampusFest[]>(`${BASE}/fests`, { signal })).data
/** Applies to volunteer for a shift; the campus office accepts or declines. */
export const applyForFestShift = async (shiftId: number, note: string | null) =>
  (await apiClient.post<CampusFest>(`${BASE}/fests/shifts/${shiftId}/apply`, { note })).data
export const withdrawFestApplication = async (applicationId: number) =>
  (await apiClient.post<CampusFest>(`${BASE}/fests/applications/${applicationId}/withdraw`)).data

export const getAllFests = async (signal?: AbortSignal) => (await apiClient.get<CampusFest[]>(`${BASE}/admin/fests`, { signal })).data
export const createFest = async (input: FestInput) => (await apiClient.post<CampusFest>(`${BASE}/admin/fests`, input)).data
export const publishFest = async (festId: number) => (await apiClient.post<CampusFest>(`${BASE}/admin/fests/${festId}/publish`)).data
export const archiveFest = async (festId: number) => (await apiClient.post<CampusFest>(`${BASE}/admin/fests/${festId}/archive`)).data
export const scheduleFestEvent = async (festId: number, eventId: number) =>
  (await apiClient.post<CampusFest>(`${BASE}/admin/fests/${festId}/schedule`, { eventId })).data
export const unscheduleFestEvent = async (festId: number, eventId: number) =>
  (await apiClient.post<CampusFest>(`${BASE}/admin/fests/${festId}/unschedule`, { eventId })).data
export const addFestShift = async (festId: number, input: { roleTitle: string; startsAt: string; endsAt: string; places: number }) =>
  (await apiClient.post<CampusFest>(`${BASE}/admin/fests/${festId}/shifts`, input)).data
export const getFestApplications = async (festId: number, signal?: AbortSignal) =>
  (await apiClient.get<FestApplication[]>(`${BASE}/admin/fests/${festId}/applications`, { signal })).data
export const decideFestApplication = async (applicationId: number, accept: boolean) =>
  (await apiClient.post<FestApplication>(`${BASE}/admin/fests/applications/${applicationId}/decision`, { accept })).data

// --- Teams

export const getGroupTeams = async (groupId: number, signal?: AbortSignal) =>
  (await apiClient.get<GroupTeams>(`${BASE}/groups/${groupId}/teams`, { signal })).data
export const createTeam = async (groupId: number, name: string, season: string) =>
  (await apiClient.post<CampusTeam>(`${BASE}/groups/${groupId}/teams`, { name, season })).data
export const deactivateTeam = async (teamId: number) => (await apiClient.post<CampusTeam>(`${BASE}/teams/${teamId}/deactivate`)).data
/** Adds an active member of the group by college ID; a new captain or vice-captain takes over that role. */
export const addTeamPlayer = async (teamId: number, loginId: string, role: TeamRole) =>
  (await apiClient.post<CampusTeam>(`${BASE}/teams/${teamId}/players`, { loginId, role })).data
export const changeTeamRole = async (playerId: number, role: TeamRole) =>
  (await apiClient.post<CampusTeam>(`${BASE}/teams/players/${playerId}/role`, { role })).data
export const removeTeamPlayer = async (playerId: number) => (await apiClient.post<CampusTeam>(`${BASE}/teams/players/${playerId}/remove`)).data

// --- Tournaments

export const getTournaments = async (signal?: AbortSignal) => (await apiClient.get<Tournament[]>(`${BASE}/tournaments`, { signal })).data
export const getAllTournaments = async (signal?: AbortSignal) => (await apiClient.get<Tournament[]>(`${BASE}/admin/tournaments`, { signal })).data
export const getTournamentTeams = async (signal?: AbortSignal) => (await apiClient.get<TeamOption[]>(`${BASE}/admin/tournaments/teams`, { signal })).data
export const createTournament = async (input: { name: string; sport: string; season: string }) =>
  (await apiClient.post<Tournament>(`${BASE}/admin/tournaments`, input)).data
export const publishTournament = async (tournamentId: number) => (await apiClient.post<Tournament>(`${BASE}/admin/tournaments/${tournamentId}/publish`)).data
export const archiveTournament = async (tournamentId: number) => (await apiClient.post<Tournament>(`${BASE}/admin/tournaments/${tournamentId}/archive`)).data
export const addFixture = async (tournamentId: number, input: FixtureInput) =>
  (await apiClient.post<Tournament>(`${BASE}/admin/tournaments/${tournamentId}/fixtures`, input)).data
export const recordFixtureResult = async (fixtureId: number, input: { homeScore: string; awayScore: string; result: FixtureResult }) =>
  (await apiClient.post<Tournament>(`${BASE}/admin/tournaments/fixtures/${fixtureId}/result`, input)).data
export const cancelFixture = async (fixtureId: number) => (await apiClient.post<Tournament>(`${BASE}/admin/tournaments/fixtures/${fixtureId}/cancel`)).data
