import { expect, test } from '@playwright/test'
import { ASSIGNMENT_TITLE, COURSE_NAME, signIn } from './support'

test('a student opens a course and hands in an assignment', async ({ page }) => {
  await signIn(page, 'lms', 'cse24001')

  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await expect(page.getByRole('heading', { level: 1, name: new RegExp(COURSE_NAME) })).toBeVisible()

  await page.getByRole('link', { name: ASSIGNMENT_TITLE }).first().click()
  await expect(page.getByRole('heading', { level: 1, name: ASSIGNMENT_TITLE })).toBeVisible()

  const another = page.getByRole('button', { name: 'Submit another attempt' })
  const response = page.getByLabel('Text response (optional)')
  await expect(another.or(response)).toBeVisible()
  if (await another.isVisible()) {
    await another.click()
  }
  await response.fill('My linked list: insert, delete, and search, with tests.')
  await page.getByRole('button', { name: /^Submit (assignment|new attempt)$/ }).click()

  await expect(page.getByRole('status').filter({ hasText: /Attempt \d+ submitted\./ })).toBeAttached()
  await expect(page.getByText('On time').first()).toBeVisible()
})
