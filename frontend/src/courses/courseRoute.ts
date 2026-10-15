// A positive whole number that JavaScript holds exactly. Anything else is never sent to the API.
const COURSE_ID_PATTERN = /^[1-9][0-9]{0,14}$/

/** The LMS course ID in a route such as /courses/:lmsCourseId, or null when it cannot be one. */
export function parseCourseId(value: string | undefined): number | null {
  return value !== undefined && COURSE_ID_PATTERN.test(value) ? Number(value) : null
}
