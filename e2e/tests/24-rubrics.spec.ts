import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, openCourseSection, signIn, signOut } from './support'

const ASSIGNMENT = 'Array warm-up exercises'

test('a faculty member grades with the assignment rubric and the student sees the scores per criterion', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()

  // The rubric is listed with its criteria.
  await openCourseSection(page, 'Rubrics')
  await expect(page.getByRole('heading', { name: 'Array exercises rubric' })).toBeVisible()

  await openCourseSection(page, 'Assignments')
  await page.getByRole('link', { name: new RegExp(`Review submissions.*${ASSIGNMENT}`) }).click()
  await page.getByRole('list', { name: 'Submissions' }).getByRole('link', { name: 'student1' }).click()

  await page.getByRole('button', { name: 'Mostly correct (8)' }).click()
  await page.getByRole('button', { name: 'Clear (8)' }).click()
  await page.getByLabel('Comment on Correct answers (optional)').fill('Problem 4 is off by one.')
  await expect(page.getByText('Total: 16 / 20')).toBeVisible()
  await page.getByRole('button', { name: /^(Save|Update) grade$/ }).click()
  await expect(page.getByRole('status').filter({ hasText: /Grade saved for attempt \d+: 16 out of 20\./ })).toBeAttached()

  await signOut(page)
  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await page.getByRole('link', { name: ASSIGNMENT }).first().click()
  await expect(page.getByRole('region', { name: 'Rubric' })).toBeVisible()
  const scores = page.getByRole('table', { name: 'Rubric scores' }).first()
  await expect(scores.getByRole('row').filter({ hasText: 'Correct answers' }).getByText('Problem 4 is off by one.')).toBeVisible()
  await expect(scores.getByRole('row').filter({ hasText: 'Clear working' }).getByText('8 / 8')).toBeVisible()
})
