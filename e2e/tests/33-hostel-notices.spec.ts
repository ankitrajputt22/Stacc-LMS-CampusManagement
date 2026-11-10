import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a warden publishes an emergency notice for their hostel', async ({ page }) => {
  const title = `Fire drill ${Date.now()}`

  await signIn(page, 'erp', 'warden1')
  await page.goto('/warden')
  const card = page.getByRole('region', { name: 'Hostel notices' })
  await card.getByLabel('Notice title').fill(title)
  await card.getByLabel('Notice message').fill('Leave by the nearest stairs at 6 pm.')
  await card.getByRole('checkbox', { name: 'Emergency' }).check()
  await card.getByRole('button', { name: 'Add notice' }).click()
  const notice = card.getByRole('listitem').filter({ hasText: title })
  await expect(notice.getByText('Draft', { exact: true })).toBeVisible()
  await notice.getByRole('button', { name: /^Publish/ }).click()
  await notice.getByRole('button', { name: 'Publish', exact: true }).click()
  await expect(notice.getByText('Published', { exact: true })).toBeVisible()
  await expect(notice.getByText('Emergency')).toBeVisible()
})
