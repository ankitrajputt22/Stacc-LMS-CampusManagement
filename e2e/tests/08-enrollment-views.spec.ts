import { expect, test } from '@playwright/test'
import { signIn, signOut } from './support'

test('an admin filters course enrollments and a teacher reads a class list', async ({ page }) => {
  await signIn(page, 'erp', 'adm1001')
  await page.goto('/admin/academic/course-enrollments')
  const table = page.getByRole('table', { name: 'Course enrollments' })
  await expect(table.getByRole('row').first()).toBeVisible()

  // cse24002 withdrew from CS304 in the demo data.
  await page.getByLabel('Status').selectOption('WITHDRAWN')
  await page.getByLabel('Search course enrollments').fill('cse24002')
  await page.getByRole('button', { name: 'Find' }).click()
  await expect(table.getByRole('row').filter({ hasText: 'CS304' })).toContainText('Withdrawn')
  await signOut(page)

  await signIn(page, 'erp', 'emp1021')
  await page.goto('/class-lists')
  await expect(page.getByRole('heading', { level: 1, name: 'Class Lists' })).toBeVisible()
  await page.getByRole('link', { name: /View students\s*of CS301/ }).click()
  const students = page.getByRole('table', { name: 'Students of CS301' })
  await expect(students.getByRole('rowheader', { name: /cse24001/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /Drop|Withdraw|Delete/ })).toHaveCount(0)
})
