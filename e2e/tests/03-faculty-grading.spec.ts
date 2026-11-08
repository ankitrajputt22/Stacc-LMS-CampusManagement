import { expect, test } from '@playwright/test'
import { ASSIGNMENT_TITLE, COURSE_CODE, COURSE_NAME, openCourseSection, signIn, signOut } from './support'

test('a faculty member grades the latest attempt and the student sees the grade', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')

  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Assignments')
  await page.getByRole('link', { name: new RegExp(`Review submissions.*${ASSIGNMENT_TITLE}`) }).click()
  await page.getByRole('list', { name: 'Submissions' }).getByRole('link', { name: 'student1' }).click()

  await page.getByLabel('Marks awarded').first().fill('42')
  await page.getByLabel('Feedback').first().fill('Clear and well tested.')
  await page.getByRole('button', { name: /^(Save|Update) grade$/ }).first().click()

  await expect(page.getByRole('status').filter({ hasText: /Grade saved for attempt \d+: 42 out of 50\./ })).toBeAttached()

  await signOut(page)
  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await page.getByRole('link', { name: ASSIGNMENT_TITLE }).first().click()

  await expect(page.getByText('Clear and well tested.').first()).toBeVisible()
})
