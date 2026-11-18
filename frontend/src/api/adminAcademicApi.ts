import type {
  AcademicSessionCreate,
  AcademicSessionUpdate,
  AdminAcademicSession,
  AdminCourse,
  AdminDepartment,
  AdminProgram,
  AdminSection,
  AdminSemester,
  CourseCreate,
  CourseUpdate,
  DepartmentCreate,
  DepartmentUpdate,
  ProgramCreate,
  ProgramUpdate,
  SectionCreate,
  SectionUpdate,
  SemesterCreate,
  SemesterRegistrationUpdate,
  SemesterUpdate,
} from '../types/admin'
import { apiClient } from './apiClient'

const ROOT = '/admin/academic'

async function list<T>(path: string, signal?: AbortSignal): Promise<T[]> {
  const response = await apiClient.get<T[]>(`${ROOT}/${path}`, { signal })
  return response.data
}

async function create<T>(path: string, body: unknown): Promise<T> {
  const response = await apiClient.post<T>(`${ROOT}/${path}`, body)
  return response.data
}

async function update<T>(path: string, id: number, body: unknown): Promise<T> {
  const response = await apiClient.put<T>(`${ROOT}/${path}/${id}`, body)
  return response.data
}

export const getDepartments = (signal?: AbortSignal) => list<AdminDepartment>('departments', signal)
export const createDepartment = (body: DepartmentCreate) => create<AdminDepartment>('departments', body)
export const updateDepartment = (id: number, body: DepartmentUpdate) => update<AdminDepartment>('departments', id, body)

export const getPrograms = (signal?: AbortSignal) => list<AdminProgram>('programs', signal)
export const createProgram = (body: ProgramCreate) => create<AdminProgram>('programs', body)
export const updateProgram = (id: number, body: ProgramUpdate) => update<AdminProgram>('programs', id, body)

export const getAcademicSessions = (signal?: AbortSignal) => list<AdminAcademicSession>('sessions', signal)
export const createAcademicSession = (body: AcademicSessionCreate) => create<AdminAcademicSession>('sessions', body)
export const updateAcademicSession = (id: number, body: AcademicSessionUpdate) =>
  update<AdminAcademicSession>('sessions', id, body)

export async function makeAcademicSessionCurrent(id: number): Promise<AdminAcademicSession> {
  const response = await apiClient.patch<AdminAcademicSession>(`${ROOT}/sessions/${id}/current`)
  return response.data
}

export const getSemesters = (signal?: AbortSignal) => list<AdminSemester>('semesters', signal)
export const createSemester = (body: SemesterCreate) => create<AdminSemester>('semesters', body)
export const updateSemester = (id: number, body: SemesterUpdate) => update<AdminSemester>('semesters', id, body)

export async function updateSemesterRegistration(id: number, body: SemesterRegistrationUpdate): Promise<AdminSemester> {
  const response = await apiClient.put<AdminSemester>(`${ROOT}/semesters/${id}/registration`, body)
  return response.data
}

export const getCourses = (signal?: AbortSignal) => list<AdminCourse>('courses', signal)
export const createCourse = (body: CourseCreate) => create<AdminCourse>('courses', body)
export const updateCourse = (id: number, body: CourseUpdate) => update<AdminCourse>('courses', id, body)

export async function getSections(semesterId?: number, signal?: AbortSignal): Promise<AdminSection[]> {
  const response = await apiClient.get<AdminSection[]>(`${ROOT}/sections`, { params: { semesterId }, signal })
  return response.data
}
export const createSection = (body: SectionCreate) => create<AdminSection>('sections', body)
export const updateSection = (id: number, body: SectionUpdate) => update<AdminSection>('sections', id, body)
