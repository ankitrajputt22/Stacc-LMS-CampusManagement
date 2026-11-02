/**
 * The cache keys of every backend answer kept in the query cache, in one place, so a change can name exactly what it
 * makes out of date (for example `queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })`). Keys need no
 * account: the cache is emptied whenever a session ends or starts.
 */
export const queryKeys = {
  me: {
    profile: () => ['me', 'profile'] as const,
    photo: () => ['me', 'photo'] as const,
    sessions: () => ['me', 'sessions'] as const,
  },
  audit: {
    events: (filters: object) => ['admin', 'audit', filters] as const,
  },
  users: {
    all: ['admin', 'users'] as const,
    list: (filters: object) => ['admin', 'users', 'list', filters] as const,
    one: (accountId: number) => ['admin', 'users', accountId] as const,
    photo: (accountId: number, version: string) => ['admin', 'users', accountId, 'photo', version] as const,
  },
  admin: {
    /** Everything the academic admin pages load. A change there makes all of it out of date, since the lists feed each
     * other's choices (departments for programs, semesters for offerings, and so on). */
    academic: ['admin', 'academic'] as const,
    departments: () => ['admin', 'academic', 'departments'] as const,
    programs: () => ['admin', 'academic', 'programs'] as const,
    sessions: () => ['admin', 'academic', 'sessions'] as const,
    semesters: () => ['admin', 'academic', 'semesters'] as const,
    courses: () => ['admin', 'academic', 'courses'] as const,
    sections: (semesterId?: number) => ['admin', 'academic', 'sections', { semesterId }] as const,
    offerings: (filters: { academicSessionId?: number; semesterId?: number }) =>
      ['admin', 'academic', 'offerings', filters] as const,
    offering: (courseOfferingId: number) => ['admin', 'academic', 'offerings', courseOfferingId, 'detail'] as const,
    offeringFaculty: (courseOfferingId: number) => ['admin', 'academic', 'offerings', courseOfferingId, 'faculty'] as const,
    facultySearch: (query: string) => ['admin', 'academic', 'faculty', { query }] as const,
    offeringRoster: (courseOfferingId: number) => ['admin', 'academic', 'offerings', courseOfferingId, 'enrollments'] as const,
    enrollmentMonitor: (filters: object) => ['admin', 'academic', 'enrollment-monitor', filters] as const,
    studentSearch: (query: string) => ['admin', 'academic', 'students', { query }] as const,
    studentEnrollments: (studentProfileId: number) => ['admin', 'academic', 'students', studentProfileId] as const,
    calendarEvents: (filters: Record<string, string | undefined>) =>
      ['admin', 'academic', 'calendar-events', filters] as const,
    studentProfiles: (query: string) => ['admin', 'academic', 'profiles', 'students', { query }] as const,
    facultyProfiles: (query: string) => ['admin', 'academic', 'profiles', 'faculty', { query }] as const,
    courseEnrollments: (semesterEnrollmentId: number) =>
      ['admin', 'academic', 'semester-enrollments', semesterEnrollmentId] as const,
  },
  academic: {
    myProfile: () => ['academic', 'me'] as const,
  },
  attendance: {
    myCourses: () => ['attendance', 'my-courses'] as const,
    sessions: (courseOfferingId: number) => ['attendance', 'offerings', courseOfferingId, 'sessions'] as const,
    session: (sessionId: number) => ['attendance', 'sessions', sessionId] as const,
    mine: () => ['attendance', 'me'] as const,
  },
  results: {
    myCourses: () => ['results', 'my-courses'] as const,
    course: (courseOfferingId: number) => ['results', 'offerings', courseOfferingId] as const,
    mine: () => ['results', 'me'] as const,
  },
  classLists: {
    mine: () => ['class-lists', 'me'] as const,
    offering: (courseOfferingId: number) => ['class-lists', courseOfferingId] as const,
  },
  registration: {
    mine: () => ['registration', 'me'] as const,
  },
  calendar: {
    mine: (range: { from: string; to: string; timeZone: string }) => ['calendar', 'me', range] as const,
  },
  lms: {
    all: ['lms'] as const,
    myCourses: () => ['lms', 'my-courses'] as const,
    managedCourses: () => ['lms', 'managed-courses'] as const,
    course: (lmsCourseId: number) => ['lms', 'courses', lmsCourseId] as const,
  },
} as const
