import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a student starts a project, another joins, and a faculty mentor accepts', async ({ browser }) => {
  const title = `Campus app ${Date.now()}`

  const lead = await browser.newPage()
  await signIn(lead, 'erp', 'student1')
  await lead.goto('/projects')
  await lead.getByLabel('Project title').fill(title)
  await lead.getByLabel('Members, you included').fill('3')
  await lead.getByLabel('What it is about').fill('A helper app for new students.')
  await lead.getByRole('button', { name: 'Start project' }).click()
  await expect(lead.getByRole('status').filter({ hasText: 'Project started. You lead it.' })).toHaveCount(1)

  const member = await browser.newPage()
  await signIn(member, 'erp', 'student2')
  await member.goto('/projects')
  const card = member.getByRole('region', { name: title })
  await card.getByLabel(`Message to the lead of ${title} (optional)`).fill('I can build the backend.')
  await card.getByRole('button', { name: 'Ask to join' }).click()
  await expect(member.getByRole('status').filter({ hasText: 'Asked to join. The lead will decide.' })).toHaveCount(1)

  await lead.reload()
  const mine = lead.getByRole('region', { name: title })
  await mine.getByRole('button', { name: /^Accept\s*student2$/ }).click()
  await expect(lead.getByRole('status').filter({ hasText: 'student2 joined the project.' })).toHaveCount(1)
  await mine.getByLabel(`Mentor’s employee ID for ${title}`).fill('faculty1')
  await mine.getByRole('button', { name: 'Invite mentor' }).click()
  await expect(lead.getByRole('status').filter({ hasText: 'faculty1 invited to mentor.' })).toHaveCount(1)

  const mentor = await browser.newPage()
  await signIn(mentor, 'erp', 'faculty1')
  await mentor.goto('/projects')
  await mentor.getByRole('region', { name: title }).getByRole('button', { name: new RegExp(`^Accept mentoring\\s*${title}$`) }).click()
  await expect(mentor.getByRole('status').filter({ hasText: 'You are now mentoring this project.' })).toHaveCount(1)
  await expect(mentor.getByRole('region', { name: title }).getByText('Mentors: faculty1')).toBeVisible()
})
