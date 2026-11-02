import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a student registers for an open elective after reviewing it, then drops it', async ({ page }) => {
  // student2 is in section B: CS305 is open to the whole semester, CS306 only to section A.
  await signIn(page, 'erp', 'student2')
  await page.goto('/academic/registration')
  await expect(page.getByRole('heading', { level: 1, name: 'Course Registration' })).toBeVisible()
  await expect(page.getByRole('checkbox', { name: /CS306/ })).toHaveCount(0)

  await page.getByRole('checkbox', { name: 'CS305 Software Engineering' }).check()
  await page.getByRole('button', { name: 'Review registration' }).click()
  await expect(page.getByText(/You are registering for 1 course with a total of 3 credits: CS305/)).toBeVisible()
  await page.getByRole('button', { name: 'Confirm registration' }).click()
  await expect(page.getByText(/Registered for 1 course \(3 credits\): CS305/)).toBeVisible()

  const mine = page.getByRole('region', { name: 'My registered courses' })
  const row = mine.getByRole('listitem').filter({ hasText: 'CS305 Software Engineering' })
  await expect(row).toContainText('Enrolled')

  await row.getByRole('button', { name: /^Drop/ }).click()
  await row.getByRole('button', { name: 'Drop course' }).click()
  await expect(page.getByText('CS305 Software Engineering dropped.')).toBeVisible()
  await expect(row).toContainText('Dropped')
  await expect(page.getByText('You dropped or ended this course earlier, so it cannot be taken again.')).toBeVisible()
})
