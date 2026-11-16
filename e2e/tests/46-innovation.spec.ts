import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a student submits an idea, a faculty member reviews it, and the campus office accepts it', async ({ browser }) => {
  const title = `Solar benches ${Date.now()}`

  const student = await browser.newPage()
  await signIn(student, 'erp', 'student1')
  await student.goto('/innovation')
  await student.getByLabel('Idea title').fill(title)
  await student.getByLabel('The problem').fill('There is nowhere to charge phones outdoors.')
  await student.getByLabel('Your idea').fill('Benches with solar panels and USB ports.')
  await student.getByRole('button', { name: 'Submit idea' }).click()
  await expect(student.getByRole('status').filter({ hasText: 'Idea submitted for review.' })).toHaveCount(1)

  const reviewer = await browser.newPage()
  await signIn(reviewer, 'erp', 'faculty1')
  await reviewer.goto('/innovation')
  await reviewer.getByLabel(`Score for ${title}`).selectOption('4')
  await reviewer.getByLabel(`Comment on ${title}`).fill('Cheap to pilot near the library.')
  await reviewer.getByRole('listitem').filter({ hasText: title }).getByRole('button', { name: 'Save review' }).click()
  await expect(reviewer.getByRole('status').filter({ hasText: 'Review saved.' })).toHaveCount(1)

  const office = await browser.newPage()
  await signIn(office, 'erp', 'admin1')
  await office.goto('/innovation')
  await expect(office.getByText('faculty1: 4 of 5 — Cheap to pilot near the library.')).toBeVisible()
  await office.getByLabel(`Decision note for ${title} (optional)`).fill('Pilot approved.')
  await office.getByRole('button', { name: new RegExp(`^Accept\\s*${title}$`) }).click()
  await expect(office.getByRole('status').filter({ hasText: 'Idea accepted.' })).toHaveCount(1)

  await student.reload()
  await expect(student.getByText('Cheap to pilot near the library.')).toBeVisible()
  await expect(student.getByText('faculty1: 4 of 5', { exact: false })).toHaveCount(0)
})
