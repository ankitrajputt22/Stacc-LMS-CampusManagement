/**
 * The cache keys of every backend answer kept in the query cache, in one place, so a change can name exactly what it
 * makes out of date (for example `queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })`). Keys need no
 * account: the cache is emptied whenever a session ends or starts.
 */
export const queryKeys = {
  lms: {
    all: ['lms'] as const,
    myCourses: () => ['lms', 'my-courses'] as const,
    course: (lmsCourseId: number) => ['lms', 'courses', lmsCourseId] as const,
  },
} as const
