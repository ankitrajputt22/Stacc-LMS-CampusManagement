import { expect, test } from '@playwright/test'
import { ASSIGNMENT_TITLE, COURSE_NAME, signIn } from './support'

test('a student opens a course and hands in an assignment', async ({ page }) => {
  await signIn(page, 'lms', 'student1')

  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await expect(page.getByRole('heading', { level: 1, name: new RegExp(COURSE_NAME) })).toBeVisible()

  await page.getByRole('link', { name: ASSIGNMENT_TITLE }).first().click()
  await expect(page.getByRole('heading', { level: 1, name: ASSIGNMENT_TITLE })).toBeVisible()

  // A student who has handed in before opens the form for a new attempt first.
  const another = page.getByRole('button', { name: 'Submit another attempt' })
  if (await another.isVisible()) {
    await another.click()
  }
  await page.getByLabel('Text response (optional)').fill('My linked list: insert, delete, and search, with tests.')
  await page.getByRole('button', { name: /^Submit (assignment|new attempt)$/ }).click()

  await expect(page.getByRole('status').filter({ hasText: /Attempt \d+ submitted\./ })).toBeAttached()
  await expect(page.getByText('On time').first()).toBeVisible()
})
