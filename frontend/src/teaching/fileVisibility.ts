import type { LmsFacultyCourseFile, LmsFacultyCourseFiles } from '../types/lms'

/** Why students cannot see the file, from the statuses the API sent, or null when they can. */
export function hiddenBecause(file: LmsFacultyCourseFile, lmsCourseStatus: LmsFacultyCourseFiles['lmsCourseStatus']): string | null {
  const reasons = [
    file.status === 'DRAFT' && 'the file is a draft',
    file.pageStatus === 'DRAFT' && 'its page is a draft',
    file.moduleStatus === 'DRAFT' && 'its module is a draft',
    lmsCourseStatus === 'DRAFT' && 'the course is not published',
  ].filter((reason): reason is string => typeof reason === 'string')
  return reasons.length === 0 ? null : `Hidden: ${reasons.join(', ')}.`
}
