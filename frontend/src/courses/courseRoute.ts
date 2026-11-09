// A positive whole number that JavaScript holds exactly. Anything else is never sent to the API.
const ROUTE_ID_PATTERN = /^[1-9][0-9]{0,14}$/

/** A record ID in a route, such as /courses/:lmsCourseId/assignments/:assignmentId, or null when it cannot be one. */
export function parseRouteId(value: string | undefined): number | null {
  return value !== undefined && ROUTE_ID_PATTERN.test(value) ? Number(value) : null
}

/** The LMS course ID in a route such as /courses/:lmsCourseId, or null when it cannot be one. */
export function parseCourseId(value: string | undefined): number | null {
  return parseRouteId(value)
}
