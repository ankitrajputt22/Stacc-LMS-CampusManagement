import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a student opens their academic profile and sees where they study and their published results', async ({ page }) => {
  await signIn(page, 'erp', 'student1')

  await page.getByRole('navigation', { name: 'Main' }).getByRole('button', { name: 'Account' }).click()
  await page.getByRole('link', { name: 'Academic profile' }).click()

  await expect(page.getByRole('heading', { level: 1, name: 'Academic profile' })).toBeVisible()
  await expect(page.getByText('25CSE001')).toBeVisible()
  await expect(page.getByText('2026-27 · Semester 3 · Section A')).toBeVisible()

  // The earlier semester's course has a published result; nothing is calculated from it.
  const earlier = page.getByRole('table', { name: /Courses of 2025-26 semester 2/ })
  await expect(earlier.getByRole('row').filter({ hasText: 'CS201' })).toContainText('78 / 100 · B+')
  await expect(page.getByText(/GPA|percentage/i)).toHaveCount(0)
})
