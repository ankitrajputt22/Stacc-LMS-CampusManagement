import { expect, test } from '@playwright/test'
import { signIn, signOut } from './support'

test.describe('Signing in', () => {
  test('the front page is one sign-in for everything', async ({ page }) => {
    await page.goto('/')

    await expect(page).toHaveURL(/\/login/)
    await expect(page.getByRole('heading', { level: 1, name: 'Sign In to STACC' })).toBeVisible()
    await expect(page.getByLabel('College ID / Employee ID')).toBeVisible()
  })

  test('a student signing in lands on the Overview, with Learning and the ERP in the sidebar', async ({ page }) => {
    await signIn(page, 'erp', 'cse24001')

    await expect(page).toHaveURL(/\/$/)
    const nav = page.getByRole('navigation', { name: 'Main' })
    await expect(nav.getByRole('link', { name: 'Courses' })).toBeVisible()
    await expect(nav.getByRole('link', { name: 'ERP', exact: true })).toBeVisible()
  })

  test('the Account tray shows the person by name, with the login ID beneath', async ({ page }) => {
    await signIn(page, 'lms', 'cse24001')

    await page.getByRole('button', { name: 'Account', exact: true }).click()

    const tray = page.getByRole('dialog', { name: 'Account' })
    await expect(tray.getByText('Aarav Sharma')).toBeVisible()
    await expect(tray.getByText('cse24001', { exact: true })).toBeVisible()
  })

  test('an old LMS link still takes a faculty member to Teaching, with Teaching in the sidebar', async ({ page }) => {
    await signIn(page, 'lms', 'emp1021')

    await expect(page).toHaveURL(/\/teaching$/)
    await expect(page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'My Courses' })).toBeVisible()
  })

  test('an account that both studies and teaches lands on the dashboard, with no main role picked', async ({ page }) => {
    await signIn(page, 'lms', 'cse24003')

    await expect(page).toHaveURL(/\/$/)
  })

  test('signing in through the ERP opens the dashboard', async ({ page }) => {
    await signIn(page, 'erp', 'adm1001')

    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  })

  test('a wrong password gets one generic message and stays on the form', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('College ID / Employee ID').fill('cse24001')
    await page.getByLabel('Password', { exact: true }).fill('not-the-password')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page.getByRole('alert').filter({ hasText: 'Invalid login ID or password.' })).toBeVisible()
    await expect(page).toHaveURL(/\/login/)
  })

  test('the sign-in token is never readable by the page, and nothing is kept in browser storage', async ({ page }) => {
    await signIn(page, 'lms', 'cse24001')

    const seen = await page.evaluate(() => ({
      cookies: document.cookie,
      session: window.sessionStorage.length,
      local: window.localStorage.length,
    }))
    expect(seen.cookies).not.toContain('STACC_SESSION')
    expect(seen.session).toBe(0)
    expect(seen.local).toBe(0)
    // The HttpOnly cookie is there for the browser to send; the page simply cannot read it.
    const cookies = await page.context().cookies()
    expect(cookies.find((cookie) => cookie.name === 'STACC_SESSION')).toMatchObject({ httpOnly: true, sameSite: 'Lax' })
  })

  test('a reload keeps the person signed in, because the session lives on the server', async ({ page }) => {
    await signIn(page, 'lms', 'cse24001')

    await page.reload()

    await expect(page).toHaveURL(/\/courses$/)
  })

  test('after signing out, pages need signing in again', async ({ page }) => {
    await signIn(page, 'lms', 'cse24001')
    await signOut(page)

    await page.goto('/courses')

    await expect(page).toHaveURL(/\/login/)
  })
})
