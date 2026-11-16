import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, openCourseSection, signIn, signOut } from './support'

test('a faculty member posts an announcement with a file and a student downloads it from the course', async ({ page }) => {
  const title = `Lab moved ${Date.now()}`

  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Announcements')
  await page.getByRole('button', { name: 'Add announcement' }).click()
  await page.getByLabel('Title', { exact: true }).fill(title)
  await page.getByLabel('Message', { exact: true }).fill('Friday’s lab moves to Room 204. The plan is attached.')
  await page.getByRole('button', { name: 'Create announcement' }).click()

  const item = page.getByRole('article', { name: title })
  await item.getByLabel(/^Add files to/).setInputFiles({ name: 'lab-plan.txt', mimeType: 'text/plain', buffer: Buffer.from('Room 204, 2 pm.\n') })
  await item.getByRole('button', { name: /^Upload/ }).click()
  await expect(item.getByRole('list', { name: `Files of ${title}` })).toContainText('lab-plan.txt')
  await item.getByRole('button', { name: /^Publish/ }).click()
  await expect(item.getByText(/^Published /)).toBeVisible()
  await signOut(page)

  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await openCourseSection(page, 'Announcements')
  const posted = page.getByRole('article', { name: title })
  await expect(posted.getByRole('list', { name: `Files of ${title}` })).toContainText('lab-plan.txt')
  const download = page.waitForEvent('download')
  await posted.getByRole('button', { name: /^Download/ }).click()
  expect((await download).suggestedFilename()).toBe('lab-plan.txt')

  // The first publication reached the student's Inbox.
  await page.goto('/inbox')
  await expect(page.getByText(`${title} was posted in ${COURSE_CODE} ${COURSE_NAME}.`)).toBeVisible()
})
