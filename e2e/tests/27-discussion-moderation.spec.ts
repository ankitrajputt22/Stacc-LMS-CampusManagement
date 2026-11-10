import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, openCourseSection, signIn, signOut } from './support'

test('a student reports a discussion and the faculty hide it from students and lock it', async ({ page }) => {
  const title = `Moderation journey ${Date.now()}`

  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Discussions')
  await page.getByRole('button', { name: 'Start a discussion' }).click()
  await page.getByLabel('Title').fill(title)
  await page.getByLabel('Question or topic').fill('Share your favourite sorting algorithm.')
  await page.getByRole('button', { name: 'Post discussion' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  await signOut(page)

  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await openCourseSection(page, 'Discussions')
  await page.getByRole('link', { name: title }).click()
  await page.getByRole('button', { name: /^Report\s*this discussion$/ }).click()
  await page.getByLabel('Why are you reporting this discussion?').fill('Not about this course.')
  await page.getByRole('button', { name: 'Send report' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Report sent to the course’s faculty.' })).toBeAttached()
  await signOut(page)

  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Discussions')
  const reports = page.getByRole('region', { name: 'Reported posts' })
  const report = reports.getByRole('listitem').filter({ hasText: title })
  await expect(report.getByText('Not about this course.')).toBeVisible()
  await report.getByRole('button', { name: /^Hide post/ }).click()
  await expect(reports.getByRole('listitem').filter({ hasText: title })).toHaveCount(0)

  await page.getByRole('link', { name: title }).click()
  await expect(page.getByText('Hidden from students')).toBeVisible()
  await page.getByRole('button', { name: 'Lock', exact: true }).click()
  await expect(page.getByText('This discussion is locked. It takes no new replies.')).toBeVisible()
  await signOut(page)

  // Students no longer see the hidden discussion.
  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await openCourseSection(page, 'Discussions')
  await expect(page.getByRole('heading', { name: 'Discussions' }).first()).toBeVisible()
  await expect(page.getByRole('link', { name: title })).toHaveCount(0)
})
