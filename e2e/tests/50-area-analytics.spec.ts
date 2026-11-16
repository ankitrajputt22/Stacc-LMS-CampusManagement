import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('each person sees the areas they run', async ({ browser }) => {
  const admin = await browser.newPage()
  await signIn(admin, 'erp', 'admin1')
  await admin.goto('/areas')
  await expect(admin.getByRole('region', { name: 'Hostel' })).toBeVisible()
  await expect(admin.getByRole('region', { name: 'Helpdesk' })).toBeVisible()
  await expect(admin.getByRole('region', { name: 'Library' })).toHaveCount(0)

  const librarian = await browser.newPage()
  await signIn(librarian, 'erp', 'librarian1')
  await librarian.goto('/areas')
  await expect(librarian.getByRole('region', { name: 'Library' }).getByText('Copies in service')).toBeVisible()
  await expect(librarian.getByRole('region', { name: 'Hostel' })).toHaveCount(0)
})
