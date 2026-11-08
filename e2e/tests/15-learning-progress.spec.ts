import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, signIn, signOut } from './support'

const PAGE = 'Arrays and memory layout'

test('a faculty member makes a page required and a student continues to it and marks it complete', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await page.getByRole('button', { name: new RegExp(`^Make required\\s*page “${PAGE}”$`) }).click()
  await expect(page.getByRole('button', { name: new RegExp(`^Make optional\\s*page “${PAGE}”$`) })).toBeVisible()
  await signOut(page)

  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  const progress = page.getByRole('region', { name: 'Your progress' })
  await expect(progress).toContainText('0 of 1 required page completed')
  await progress.getByRole('link', { name: PAGE }).click()

  await expect(page.getByRole('heading', { level: 1, name: PAGE })).toBeVisible()
  await page.getByRole('button', { name: 'Mark as complete' }).click()
  await expect(page.getByText('Completed', { exact: true })).toBeVisible()

  await page.goBack()
  await expect(page.getByRole('region', { name: 'Your progress' })).toContainText('1 of 1 required page completed')
})
