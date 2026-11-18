import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a student finds their fees, exam seats, and CGPA in the ERP', async ({ page }) => {
  await signIn(page, 'erp', 'cse24001')
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'ERP', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'ERP' })).toBeVisible()
  await expect(page.getByRole('link', { name: /Fees.*₹32,500 due/ })).toBeVisible()

  await page.getByRole('link', { name: /Exams & Results/ }).first().click()
  const exams = page.getByRole('table', { name: 'Your exams' })
  await expect(exams.getByRole('row').filter({ hasText: 'CS301' })).toContainText('B-12')

  await page.getByRole('navigation', { name: 'Exams and results sections' }).getByRole('link', { name: 'Results' }).click()
  await expect(page.getByRole('heading', { name: 'CGPA history' })).toBeVisible()

  await page.goto('/erp/fees/payments')
  await expect(page.getByRole('table', { name: 'Payments' }).getByRole('row')).toHaveCount(4)
})
