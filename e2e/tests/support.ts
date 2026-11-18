import { expect, type Page } from '@playwright/test'

export function password(): string {
  const value = process.env.STACC_TEST_PASSWORD
  if (!value) {
    throw new Error('STACC_TEST_PASSWORD is not set. Start the stack with ./e2e/stack.sh up first.')
  }
  return value
}

export async function signIn(page: Page, entry: 'erp' | 'lms', loginId: string, withPassword = password()): Promise<void> {
  await page.goto(`/login?portal=${entry}`)
  await page.getByLabel('College ID / Employee ID').fill(loginId)
  await page.getByLabel('Password', { exact: true }).fill(withPassword)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).not.toHaveURL(/\/login/)
}

export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Account', exact: true }).click()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login/)
}

export const COURSE_CODE = 'CS301'
export const COURSE_NAME = 'Data Structures'
export const ASSIGNMENT_TITLE = 'Linked list implementation'

export async function openCourseSection(page: Page, section: string): Promise<void> {
  await page.getByRole('navigation', { name: 'Course navigation' }).getByRole('link', { name: section, exact: true }).click()
}
