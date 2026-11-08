import { expect, test } from '@playwright/test'
import { ASSIGNMENT_TITLE, COURSE_CODE, COURSE_NAME, openCourseSection, signIn, signOut } from './support'

test('a faculty member adds a course outcome and aligns an assignment, and a student sees both', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Outcomes')
  await page.getByLabel('New outcome', { exact: true }).fill('Analyse the cost of common data structures.')
  await page.getByRole('button', { name: 'Add outcome' }).click()
  const outcomes = page.getByRole('region', { name: 'Course outcomes' })
  await expect(outcomes.getByText('Analyse the cost of common data structures.')).toBeVisible()
  await expect(outcomes.getByRole('button', { name: 'Edit CO1' })).toBeVisible()

  await openCourseSection(page, 'Assignments')
  await page.getByRole('button', { name: new RegExp(`Edit alignment\\s*of assignment “${ASSIGNMENT_TITLE}”`) }).click()
  await page.getByRole('checkbox', { name: /CO1 Analyse the cost/ }).check()
  await page.getByLabel('Bloom level', { exact: true }).selectOption('ANALYZE')
  await page.getByRole('button', { name: 'Save alignment' }).click()
  await expect(page.getByText('Outcomes CO1 · Bloom level Analyze')).toBeVisible()
  await signOut(page)

  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await openCourseSection(page, 'Syllabus')
  await expect(page.getByRole('region', { name: 'Course outcomes' }).getByText('Analyse the cost of common data structures.')).toBeVisible()

  await openCourseSection(page, 'Assignments')
  await page.getByRole('link', { name: ASSIGNMENT_TITLE }).click()
  await expect(page.getByText('Bloom level', { exact: true })).toBeVisible()
  await expect(page.getByText('Analyze', { exact: true })).toBeVisible()
})
