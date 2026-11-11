import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a mentor records a meeting and the student sees only the shared part and their action item', async ({ browser }) => {
  const stamp = Date.now()
  const title = `Check-in ${stamp}`
  const task = `Read chapter ${stamp}`

  const admin = await browser.newPage()
  await signIn(admin, 'erp', 'admin1')
  await admin.goto('/admin/mentoring')
  await admin.getByLabel(/employee ID/).fill('faculty2')
  await admin.getByLabel(/college ID/).fill('student2')
  await admin.getByRole('button', { name: 'Pair', exact: true }).click()
  await expect(admin.getByRole('status').filter({ hasText: 'Paired.' })).toHaveCount(1)

  const mentor = await browser.newPage()
  await signIn(mentor, 'erp', 'faculty2')
  await mentor.goto('/mentoring')
  const pair = mentor.getByRole('region', { name: 'Mentoring student2' })
  await pair.getByLabel('Meeting title').fill(title)
  await pair.getByLabel('Held at').fill('2026-11-05T09:00')
  await pair.getByLabel('Shared notes').fill('Agreed to plan revision weekly.')
  await pair.getByLabel('Private notes').fill('Keep an eye on attendance.')
  await pair.getByRole('button', { name: 'Record meeting' }).click()
  await expect(mentor.getByRole('status').filter({ hasText: 'Meeting recorded.' })).toHaveCount(1)
  const meeting = pair.getByRole('region', { name: title })
  await meeting.getByLabel(`Action item for ${title}`).fill(task)
  await meeting.getByRole('button', { name: 'Add action item' }).click()
  await expect(mentor.getByRole('status').filter({ hasText: 'Action item added.' })).toHaveCount(1)

  const student = await browser.newPage()
  await signIn(student, 'erp', 'student2')
  await student.goto('/todo')
  await expect(student.getByText(`Mentoring: ${task}`)).toBeVisible()
  await student.goto('/mentoring')
  const mine = student.getByRole('region', { name: 'Your mentor faculty2' })
  await expect(mine.getByText('Agreed to plan revision weekly.')).toBeVisible()
  await expect(mine.getByText('Keep an eye on attendance.')).toHaveCount(0)
  await mine.getByRole('button', { name: new RegExp(`^Mark done:?\\s*${task}$`) }).click()
  await expect(student.getByRole('status').filter({ hasText: 'Marked done.' })).toHaveCount(1)
})
