import { expect, test } from '@playwright/test'
import { signIn, signOut } from './support'

test.describe('Signing in', () => {
  test('the front page offers the ERP and LMS logins', async ({ page }) => {
    await page.goto('/')

    await expect(page).toHaveURL(/\/login/)
    await expect(page.getByRole('link', { name: 'ERP Login' }).first()).toBeVisible()
    await expect(page.getByRole('link', { name: 'LMS Login' }).first()).toBeVisible()
  })

  test('a student signing in through the LMS lands on Courses', async ({ page }) => {
    await signIn(page, 'lms', 'student1')

    await expect(page).toHaveURL(/\/courses$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Courses' })).toBeVisible()
  })

  test('the Account tray shows the person by name, with the login ID beneath', async ({ page }) => {
    await signIn(page, 'lms', 'student1')

    await page.getByRole('navigation', { name: 'Main' }).getByRole('button', { name: 'Account' }).click()

    const tray = page.getByRole('dialog', { name: 'Account' })
    await expect(tray.getByText('Aarav Sharma')).toBeVisible()
    await expect(tray.getByText('student1', { exact: true })).toBeVisible()
  })

  test('a faculty member signing in through the LMS lands on Teaching', async ({ page }) => {
    await signIn(page, 'lms', 'faculty1')

    await expect(page).toHaveURL(/\/teaching$/)
  })

  test('an account that both studies and teaches lands on the dashboard, with no main role picked', async ({ page }) => {
    await signIn(page, 'lms', 'studentfaculty1')

    await expect(page).toHaveURL(/\/$/)
  })

  test('signing in through the ERP opens the dashboard', async ({ page }) => {
    await signIn(page, 'erp', 'admin1')

    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  })

  test('a wrong password gets one generic message and stays on the form', async ({ page }) => {
    await page.goto('/login?portal=erp')
    await page.getByLabel('College ID / Employee ID').fill('student1')
    await page.getByLabel('Password', { exact: true }).fill('not-the-password')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page.getByRole('alert').filter({ hasText: 'Invalid login ID or password.' })).toBeVisible()
    await expect(page).toHaveURL(/\/login/)
  })

  test('after signing out, pages need signing in again', async ({ page }) => {
    await signIn(page, 'lms', 'student1')
    await signOut(page)

    await page.goto('/courses')

    await expect(page).toHaveURL(/\/login/)
  })
})
