import { expect, type Page } from '@playwright/test'

/** The demo accounts' password for this run, from the stack's generated settings. Never printed. */
function password(): string {
  const value = process.env.STACC_TEST_PASSWORD
  if (!value) {
    throw new Error('STACC_TEST_PASSWORD is not set. Start the stack with ./e2e/stack.sh up first.')
  }
  return value
}

/** Signs in through the ERP or LMS entry point, the way a person does. */
export async function signIn(page: Page, portal: 'erp' | 'lms', loginId: string): Promise<void> {
  await page.goto(`/login?portal=${portal}`)
  await page.getByLabel('College ID / Employee ID').fill(loginId)
  await page.getByLabel('Password', { exact: true }).fill(password())
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).not.toHaveURL(/\/login/)
}

/** Signs out from the Account tray of the navigation rail. */
export async function signOut(page: Page): Promise<void> {
  await page.getByRole('navigation', { name: 'Main' }).getByRole('button', { name: 'Account' }).click()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login/)
}

/** Opens the LMS assignment every journey uses: CS301 Data Structures, "Linked list implementation" (future deadline). */
export const COURSE_CODE = 'CS301'
export const COURSE_NAME = 'Data Structures'
export const ASSIGNMENT_TITLE = 'Linked list implementation'
