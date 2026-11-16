import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('an admin checks the system health page', async ({ page }) => {
  await signIn(page, 'erp', 'admin1')
  await page.goto('/admin/system')
  const parts = page.getByRole('region', { name: 'Parts' })
  await expect(parts.getByRole('listitem').filter({ hasText: 'Database' }).getByText('UP')).toBeVisible()
  await expect(page.getByRole('region', { name: 'Application' }).getByText('Database schema')).toBeVisible()
  // The AI part says plainly when AI is off, as in the end-to-end stack (v0.13.7).
  await expect(page.getByRole('region', { name: 'AI' }).getByText('AI features are off.')).toBeVisible()
  await page.getByRole('button', { name: 'Check again' }).click()
  await expect(parts.getByRole('listitem').filter({ hasText: 'Ready for requests' }).getByText('UP')).toBeVisible()
})
