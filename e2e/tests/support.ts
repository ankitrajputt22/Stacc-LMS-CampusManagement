import { expect, type Page } from '@playwright/test'

/** The demo accounts' password for this run, from the stack's generated settings. Never printed. */
export function password(): string {
  const value = process.env.STACC_TEST_PASSWORD
  if (!value) {
    throw new Error('STACC_TEST_PASSWORD is not set. Start the stack with ./e2e/stack.sh up first.')
  }
  return value
}

/**
 * Signs in at the one sign-in page, the way a person does. An `lms` entry uses an old LMS link, which lands a student
 * on Courses and a teacher on Teaching; `erp` lands on the Overview.
 */
export async function signIn(page: Page, entry: 'erp' | 'lms', loginId: string, withPassword = password()): Promise<void> {
  await page.goto(`/login?portal=${entry}`)
  await page.getByLabel('College ID / Employee ID').fill(loginId)
  await page.getByLabel('Password', { exact: true }).fill(withPassword)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).not.toHaveURL(/\/login/)
}

/** Signs out from the account menu in the top bar. */
export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Account', exact: true }).click()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login/)
}

/** Opens the LMS assignment every journey uses: CS301 Data Structures, "Linked list implementation" (future deadline). */
export const COURSE_CODE = 'CS301'
export const COURSE_NAME = 'Data Structures'
export const ASSIGNMENT_TITLE = 'Linked list implementation'

/** Opens a section of the current course from its course navigation, such as Modules or Files. */
export async function openCourseSection(page: Page, section: string): Promise<void> {
  await page.getByRole('navigation', { name: 'Course navigation' }).getByRole('link', { name: section, exact: true }).click()
}
