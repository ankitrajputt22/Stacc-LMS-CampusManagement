import { expect, test } from '@playwright/test'
import { signIn, signOut } from './support'

// Only this journey uses ece25010's requests.
test('a student asks for medical leave from ERP Attendance and the office approves it', async ({ page }) => {
  await signIn(page, 'erp', 'ece25010')
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Attendance', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Attendance' })).toBeVisible()

  await page.getByRole('button', { name: 'Request medical leave' }).click()
  const dialog = page.getByRole('dialog', { name: 'Request medical leave' })
  const day = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
  await dialog.getByLabel('From date').fill(day)
  await dialog.getByLabel('To date').fill(day)
  await dialog.getByLabel('Reason').fill('Fever; the doctor advised a day of rest.')
  await dialog.getByRole('button', { name: 'Submit request' }).click()
  await expect(dialog).toBeHidden()
  const reference = (await page.getByText(/^MED-\d{6}$/).first().textContent()) ?? ''
  expect(reference).toMatch(/^MED-/)
  await signOut(page)

  await signIn(page, 'erp', 'adm1001')
  await page.goto('/erp/requests')
  await page.getByRole('searchbox', { name: 'Search' }).fill('ece25010')
  await page.getByRole('button', { name: 'Search', exact: true }).click()
  await page.getByRole('button', { name: `Review ${reference}` }).click()
  await page.getByRole('button', { name: 'Approve' }).click()
  await expect(page.getByRole('row').filter({ hasText: reference })).toContainText('Approved')
})
