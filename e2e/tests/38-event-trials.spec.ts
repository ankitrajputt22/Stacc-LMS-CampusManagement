import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('the campus office turns a coming event into a trial', async ({ page }) => {
  const title = `Cricket trial ${Date.now()}`

  await signIn(page, 'erp', 'admin1')
  await page.goto('/admin/campus/events')
  const organizer = page.getByRole('region', { name: 'Organize events' })
  await organizer.getByLabel('Title').fill(title)
  await organizer.getByLabel('Starts').fill('2030-03-10T16:00')
  await organizer.getByLabel('Ends').fill('2030-03-10T18:00')
  await organizer.getByRole('button', { name: 'Save draft' }).click()
  await expect(organizer.getByText('Draft saved. Publish it when it is ready.')).toBeVisible()

  await organizer.getByRole('button', { name: `Make a trial of ${title}` }).click()
  await expect(organizer.getByText('Marked as a trial. Record Selected or Not selected once it starts.')).toBeVisible()
  const row = organizer.getByRole('listitem').filter({ hasText: title })
  await expect(row.getByText('Trial', { exact: true })).toBeVisible()
  await expect(organizer.getByRole('button', { name: `Make a trial of ${title}` })).toHaveCount(0)
})
