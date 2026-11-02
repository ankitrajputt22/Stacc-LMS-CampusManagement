/** What a course needs to be placed in a term: its session, program, and semester, as the backend sent them. */
type Termed = { academicSessionCode: string; programCode: string; semesterNumber: number }

export type Term<T> = { key: string; heading: string; programCode: string; items: T[] }

/**
 * Courses grouped by term (one semester of one program in one session): the latest session first, then by program
 * and semester. Within a term the backend's order is kept. Nothing is worked out beyond what the API sent.
 */
export function groupByTerm<T extends Termed>(items: readonly T[]): Term<T>[] {
  const terms = new Map<string, Term<T>>()
  for (const item of items) {
    const key = `${item.academicSessionCode}|${item.programCode}|${item.semesterNumber}`
    let term = terms.get(key)
    if (!term) {
      term = {
        key,
        heading: `${item.academicSessionCode} · Semester ${item.semesterNumber}`,
        programCode: item.programCode,
        items: [],
      }
      terms.set(key, term)
    }
    term.items.push(item)
  }
  return [...terms.values()].sort((a, b) => {
    const [sessionA, programA, semesterA] = a.key.split('|')
    const [sessionB, programB, semesterB] = b.key.split('|')
    return (
      sessionB.localeCompare(sessionA) || programA.localeCompare(programB) || Number(semesterA) - Number(semesterB)
    )
  })
}
