import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, openCourseSection, signIn, signOut } from './support'

const TITLE = 'One-shot reflection'

/** The value of a datetime-local input for a moment some days from now, in the browser's own time zone. */
function inDays(days: number): string {
  const time = new Date(Date.now() + days * 86_400_000)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${time.getFullYear()}-${pad(time.getMonth() + 1)}-${pad(time.getDate())}T${pad(time.getHours())}:${pad(time.getMinutes())}`
}

test('a faculty member allows one attempt and no late work, and the student can hand in only once', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Assignments')

  await page.getByRole('button', { name: /^Add assignment/ }).click()
  await page.getByLabel('Title', { exact: true }).fill(TITLE)
  await page.getByRole('textbox', { name: 'Instructions', exact: true }).fill('Write a short reflection on this week.')
  await page.getByLabel('Due date and time').fill(inDays(7))
  await page.getByLabel('Maximum marks').fill('5')
  await page.getByRole('button', { name: 'Create assignment' }).click()

  const item = page.getByRole('list', { name: 'Assignments' }).getByRole('listitem').filter({ hasText: TITLE })
  await item.getByRole('button', { name: /^Submission rules/ }).click()
  await item.getByLabel('Attempts allowed (optional)').fill('1')
  await item.getByLabel('Accept late work (marked late)').uncheck()
  await item.getByRole('button', { name: 'Save rules' }).click()
  await expect(item.getByText(/no late work · 1 attempt/)).toBeVisible()
  await item.getByRole('button', { name: new RegExp(`^Publish\\s*assignment “${TITLE}”`) }).click()
  await expect(item.getByRole('button', { name: /^Publish/ })).toHaveCount(0)
  await signOut(page)

  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await page.getByRole('link', { name: TITLE }).first().click()
  await expect(page.getByText('Not accepted after the due date')).toBeVisible()
  await page.getByLabel('Text response (optional)').fill('This week I learned about stacks.')
  await page.getByRole('button', { name: 'Submit assignment' }).click()
  await expect(page.getByText('You have used every attempt (1).')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Submit another attempt' })).toHaveCount(0)
})
