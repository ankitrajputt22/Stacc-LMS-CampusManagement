const ROUTE_ID_PATTERN = /^[1-9][0-9]{0,14}$/

export function parseRouteId(value: string | undefined): number | null {
  return value !== undefined && ROUTE_ID_PATTERN.test(value) ? Number(value) : null
}

export function parseCourseId(value: string | undefined): number | null {
  return parseRouteId(value)
}
