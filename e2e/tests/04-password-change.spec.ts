import { expect, type Page, test } from '@playwright/test'
import { password, signIn, signOut } from './support'

// Only this journey uses student2, so changing its password cannot disturb the others. It ends by putting the
// password back.
const LOGIN_ID = 'student2'
const NEW_PASSWORD = 'e2e river lamp cocoa 42'

async function changePassword(page: Page, current: string, next: string) {
  await page.getByRole('navigation', { name: 'Main' }).getByRole('button', { name: 'Account' }).click()
  await page.getByRole('link', { name: 'Change password' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Change password' })).toBeVisible()
  await page.getByLabel('Current password').fill(current)
  await page.locator('#change-password-newPassword').fill(next)
  await page.getByLabel('New password again').fill(next)
  await page.getByRole('button', { name: 'Change password' }).click()
  await expect(page.getByText('Password changed.')).toBeVisible()
}

test('changing the password keeps this device signed in and signs every other device out', async ({ page, browser }) => {
  const otherDevice = await browser.newContext()
  const other = await otherDevice.newPage()
  try {
    await signIn(other, 'erp', LOGIN_ID)
    await signIn(page, 'erp', LOGIN_ID)

    await changePassword(page, password(), NEW_PASSWORD)

    // This device carries on; the other one is sent to sign in again on its next request.
    await page.goto('/inbox')
    await expect(page).toHaveURL(/\/inbox$/)
    await other.goto('/inbox')
    await expect(other).toHaveURL(/\/login/)

    // The old password no longer works, the new one does.
    await signOut(page)
    await page.goto('/login?portal=erp')
    await page.getByLabel('College ID / Employee ID').fill(LOGIN_ID)
    await page.getByLabel('Password', { exact: true }).fill(password())
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('alert').filter({ hasText: 'Invalid login ID or password.' })).toBeVisible()
    await signIn(page, 'erp', LOGIN_ID, NEW_PASSWORD)

    await changePassword(page, NEW_PASSWORD, password())
  } finally {
    await otherDevice.close()
  }
})
