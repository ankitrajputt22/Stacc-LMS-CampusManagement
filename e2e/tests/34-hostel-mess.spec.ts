import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a warden sets the weekly mess menu and changes one meal for a day', async ({ page }) => {
  const dish = `Paneer curry ${Date.now()}`

  await signIn(page, 'erp', 'warden1')
  await page.goto('/warden')
  const card = page.getByRole('region', { name: 'Mess' })
  for (const day of ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']) {
    await card.getByLabel(`${day} lunch`).fill('Rice, dal, salad')
  }
  await card.getByRole('button', { name: 'Save weekly menu' }).click()
  await expect(card.getByRole('status')).toHaveText('Weekly menu saved.')
  await expect(card.getByText('Rice, dal, salad').first()).toBeVisible()

  await card.getByLabel('Meal', { exact: true }).selectOption('DINNER')
  await card.getByLabel('Served instead').fill(dish)
  await card.getByLabel('Note (optional)').fill('Festival')
  await card.getByRole('button', { name: 'Save change' }).click()
  await expect(card.getByRole('status')).toHaveText('Change for the day saved.')
  await expect(card.getByText(dish)).toBeVisible()
})
