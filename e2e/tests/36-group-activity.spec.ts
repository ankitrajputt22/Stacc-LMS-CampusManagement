import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a club officer posts an announcement and schedules a meeting for members', async ({ page }) => {
  const title = `Kickoff ${Date.now()}`

  await signIn(page, 'erp', 'student1')
  await page.goto('/campus/clubs')
  await page.getByRole('button', { name: /^Sessions and announcements/ }).first().click()
  const panel = page.getByRole('region', { name: /^Sessions and announcements of / }).first()
  await panel.getByLabel('Announcement title').fill(title)
  await panel.getByLabel('Announcement message').fill('First meeting of the term in room 204.')
  await panel.getByRole('button', { name: 'Post announcement' }).click()
  await expect(panel.getByRole('status')).toHaveText('Announcement posted. Members are told in their Inbox.')
  await expect(panel.getByText(title)).toBeVisible()

  await panel.getByLabel('Session title').fill(title)
  await panel.getByLabel('Starts').fill('2030-01-15T17:00')
  await panel.getByLabel('Ends').fill('2030-01-15T18:00')
  await panel.getByRole('button', { name: 'Schedule session' }).click()
  await expect(panel.getByRole('status')).toHaveText('Session scheduled. It is on members’ calendars.')
})
