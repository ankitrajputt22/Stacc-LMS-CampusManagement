import { expect, test } from '@playwright/test'
import { signIn } from './support'

// New accounts of this run only; the journey never changes a demo account.
const SUFFIX = Date.now().toString(36).toUpperCase()
const HEADER = 'login_id,full_name,email,roles,department,program,admission_session,roll_number'

test('an admin checks a CSV file, sees its problems, and then imports it', async ({ page }) => {
  await signIn(page, 'erp', 'admin1')
  await page.goto('/admin/users')
  await page.getByRole('link', { name: 'Import from CSV' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Import users' })).toBeVisible()
  await page.getByLabel('Temporary password for every account').fill('Teal-Harbor-6620')

  // student1 exists already, so the file is refused as a whole.
  await page.getByLabel('File to import').setInputFiles({
    name: 'people.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(`${HEADER}\nstudent1,Someone,,ADMIN,,,,\nIMP${SUFFIX},Import Person,,ADMIN,,,,\n`),
  })
  await page.getByRole('button', { name: 'Check file' }).click()
  await expect(page.getByText('An account with this login ID already exists.').first()).toBeVisible()
  await expect(page.getByRole('button', { name: /^Import \d+ accounts$/ })).toHaveCount(0)

  await page.getByLabel('File to import').setInputFiles({
    name: 'people.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(`${HEADER}\nIMP${SUFFIX},Import Person,,ADMIN,,,,\n`),
  })
  await page.getByRole('button', { name: 'Check file' }).click()
  await page.getByRole('button', { name: 'Import 1 accounts' }).click()
  await page.getByRole('button', { name: 'Create accounts' }).click()
  await expect(page.getByText('1 account was created.', { exact: false })).toBeVisible()

  await page.goto('/admin/users')
  await page.getByLabel('Search users').fill(`IMP${SUFFIX}`)
  await page.getByRole('button', { name: 'Find' }).click()
  await expect(page.getByRole('table', { name: 'Users' }).getByRole('link', { name: 'Import Person' })).toBeVisible()
})
