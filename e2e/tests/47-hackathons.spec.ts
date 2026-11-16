import { expect, test } from '@playwright/test'
import { signIn } from './support'

/** A datetime-local value in the browser's own time zone. */
function local(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

test('the campus office runs a hackathon and a student team hands in its work', async ({ browser }) => {
  const title = `Code Sprint ${Date.now()}`
  const team = `Owls ${Date.now()}`

  const office = await browser.newPage()
  await signIn(office, 'erp', 'admin1')
  await office.goto('/hackathons')
  await office.getByLabel('Hackathon title').fill(title)
  await office.getByLabel('Team size').fill('3')
  await office.getByLabel('Starts', { exact: true }).fill(local(new Date(Date.now() - 3_600_000)))
  await office.getByLabel('Ends', { exact: true }).fill(local(new Date(Date.now() + 86_400_000)))
  await office.getByRole('button', { name: 'Add hackathon' }).click()
  await expect(office.getByRole('status').filter({ hasText: 'Hackathon added as a draft.' })).toHaveCount(1)
  const card = office.getByRole('region', { name: title })
  await card.getByLabel(`Judge’s employee ID for ${title}`).fill('faculty1')
  await card.getByRole('button', { name: 'Name judge' }).click()
  await expect(card.getByText('Judges: faculty1')).toBeVisible()
  await card.getByRole('button', { name: new RegExp(`^Publish\\s*${title}$`) }).click()
  await expect(office.getByRole('status').filter({ hasText: 'Hackathon published.' })).toHaveCount(1)

  const student = await browser.newPage()
  await signIn(student, 'erp', 'student1')
  await student.goto('/hackathons')
  const mine = student.getByRole('region', { name: title })
  await mine.getByLabel(`New team name for ${title}`).fill(team)
  await mine.getByRole('button', { name: 'Form team' }).click()
  await expect(student.getByRole('status').filter({ hasText: 'Team formed.' })).toHaveCount(1)
  await mine.getByLabel('Submission title').fill('Bus tracker')
  await mine.getByLabel('What you built').fill('Live campus bus positions.')
  await mine.getByRole('button', { name: 'Hand in' }).click()
  await expect(student.getByRole('status').filter({ hasText: 'Work handed in. You can replace it until the end.' })).toHaveCount(1)
  await expect(mine.getByRole('button', { name: 'Replace submission' })).toBeVisible()
})
