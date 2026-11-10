import { expect, test } from '@playwright/test'
import { signIn, signOut } from './support'

test('a resident registers a visitor, security sees them, and the hostel office sets visiting hours', async ({ page }) => {
  const visitor = `Asha ${Date.now()}`

  await signIn(page, 'erp', 'student2')
  await page.goto('/hostel')
  const card = page.getByRole('region', { name: 'Visitors' })
  await card.getByLabel('Visitor’s name').fill(visitor)
  await card.getByLabel('Relation').fill('Mother')
  await card.getByRole('button', { name: 'Register visitor' }).click()
  await expect(card.getByRole('status')).toHaveText('Visitor registered. Security lets them in during visiting hours.')
  await expect(card.getByRole('listitem').filter({ hasText: visitor }).getByText('Expected')).toBeVisible()
  await signOut(page)

  await signIn(page, 'erp', 'security1')
  await page.goto('/gate')
  await expect(page.getByRole('region', { name: 'Visitors today' }).getByRole('listitem').filter({ hasText: visitor })).toBeVisible()
  await signOut(page)

  await signIn(page, 'erp', 'admin1')
  await page.goto('/admin/hostel/visitors')
  await page.getByLabel('Visitors from').fill('16:00')
  await page.getByLabel('Visitors until').fill('19:00')
  await page.getByRole('button', { name: 'Save visiting hours' }).click()
  await expect(page.getByText('Visiting hours saved.')).toBeVisible()
})
