import { expect, test } from '@playwright/test'
import { signIn, signOut } from './support'

// A new account of this run only, so the journey never changes a demo account.
const LOGIN_ID = `E2E${Date.now().toString(36).toUpperCase()}`
const TEMPORARY = 'Amber-Signal-4821'
const CHOSEN = 'e2e harbor violet kite 57'

test('an admin creates an account and the new person must replace the temporary password', async ({ page }) => {
  await signIn(page, 'erp', 'admin1')
  await page.goto('/admin/users')
  await expect(page.getByRole('heading', { level: 1, name: 'Users' })).toBeVisible()
  await page.getByRole('link', { name: 'Add user' }).click()

  await page.getByLabel('Login ID').fill(LOGIN_ID)
  await page.getByLabel('Full name').fill('Journey Admin')
  await page.getByLabel('Temporary password').fill(TEMPORARY)
  await page.getByRole('checkbox', { name: 'Admin' }).check()
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page.getByText(`Account ${LOGIN_ID} created.`)).toBeVisible()
  await expect(page.getByText('Must change password').first()).toBeVisible()
  await page.goto('/admin/users')
  await page.getByLabel('Search users').fill(LOGIN_ID)
  await page.getByRole('button', { name: 'Find' }).click()
  await expect(page.getByRole('table', { name: 'Users' }).getByRole('link', { name: 'Journey Admin' })).toBeVisible()

  // The creation is in the audit log, by admin1, without the password.
  await page.goto('/admin/audit')
  await page.getByLabel('Search login IDs').fill(LOGIN_ID)
  await page.getByRole('button', { name: 'Find' }).click()
  const created = page.getByRole('table', { name: 'Audit log' }).getByRole('row').filter({ hasText: 'Account created' })
  await expect(created).toContainText('admin1')
  await expect(page.getByText(TEMPORARY)).toHaveCount(0)
  await signOut(page)

  await signIn(page, 'erp', LOGIN_ID, TEMPORARY)
  await expect(page.getByRole('heading', { level: 1, name: 'Change password' })).toBeVisible()
  await page.getByLabel('Temporary password').fill(TEMPORARY)
  await page.locator('#change-password-newPassword').fill(CHOSEN)
  await page.getByLabel('New password again').fill(CHOSEN)
  await page.getByRole('button', { name: 'Change password' }).click()
  await expect(page.getByRole('navigation', { name: 'Main' })).toBeVisible()

  // Account settings: the official name as the college recorded it, a preferred name, and this browser's session.
  await page.goto('/account')
  await expect(page.getByRole('heading', { level: 1, name: 'Account settings' })).toBeVisible()
  await expect(page.getByRole('main').getByText('Journey Admin')).toBeVisible()
  await page.getByLabel(/Preferred name/).fill('Journey')
  await page.getByRole('button', { name: 'Save details' }).click()
  await expect(page.getByText('Your details are saved.')).toBeVisible()
  await expect(page.getByText('This browser')).toBeVisible()
})
