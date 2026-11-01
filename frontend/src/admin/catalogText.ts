import type { CourseType, DegreeType } from '../types/admin'

const DEGREE_WORDS: Record<DegreeType, string> = {
  UNDERGRADUATE: 'Undergraduate',
  POSTGRADUATE: 'Postgraduate',
  DIPLOMA: 'Diploma',
  DOCTORATE: 'Doctorate',
  CERTIFICATE: 'Certificate',
}

const COURSE_TYPE_WORDS: Record<CourseType, string> = {
  CORE: 'Core',
  ELECTIVE: 'Elective',
  LAB: 'Lab',
  PROJECT: 'Project',
  AUDIT: 'Audit',
}

export const DEGREE_TYPES = Object.keys(DEGREE_WORDS) as DegreeType[]
export const COURSE_TYPES = Object.keys(COURSE_TYPE_WORDS) as CourseType[]

export const degreeWord = (degree: DegreeType) => DEGREE_WORDS[degree] ?? degree
export const courseTypeWord = (type: CourseType) => COURSE_TYPE_WORDS[type] ?? type

export const degreeOptions = DEGREE_TYPES.map((value) => ({ value, label: degreeWord(value) }))
export const courseTypeOptions = COURSE_TYPES.map((value) => ({ value, label: courseTypeWord(value) }))

/** Optional text as the API takes it: blank becomes null. */
export const optional = (text: string) => (text.trim() === '' ? null : text.trim())
