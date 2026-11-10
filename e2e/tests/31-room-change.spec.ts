import { expect, test } from '@playwright/test'
import { signIn, signOut } from './support'

test('a resident asks to move, and the hostel office sees the request waiting for the warden', async ({ page }) => {
  await signIn(page, 'erp', 'student2')
  await page.goto('/hostel')
  const card = page.getByRole('region', { name: 'Room change' })
  await card.getByRole('button', { name: 'Ask to move' }).click()
  await card.getByLabel('Reason').fill('The room is too noisy at night.')
  await card.getByLabel('Wish (optional)').fill('A quiet floor')
  await card.getByRole('button', { name: 'Ask to move' }).click()
  await expect(card.getByText('Your request went to the warden.')).toBeVisible()
  const request = card.getByRole('listitem').filter({ hasText: 'The room is too noisy at night.' }).first()
  await expect(request.getByText('Waiting for the warden')).toBeVisible()
  await signOut(page)

  await signIn(page, 'erp', 'admin1')
  await page.goto('/admin/hostel/room-changes')
  await expect(page.getByRole('listitem').filter({ hasText: 'The room is too noisy at night.' }).first().getByText('Waiting for the warden')).toBeVisible()
  await signOut(page)

  // The resident cancels it, so the demo bed stays free of open changes.
  await signIn(page, 'erp', 'student2')
  await page.goto('/hostel')
  const mine = page.getByRole('region', { name: 'Room change' }).getByRole('listitem').filter({ hasText: 'Waiting for the warden' }).first()
  await mine.getByRole('button', { name: 'Cancel request' }).click()
  await mine.getByRole('button', { name: 'Cancel room change' }).click()
  await expect(page.getByRole('region', { name: 'Room change' }).getByText('Room change cancelled.').first()).toBeVisible()
})
