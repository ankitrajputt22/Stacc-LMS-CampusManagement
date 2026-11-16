import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('the campus office records a match and students see the standings', async ({ browser }) => {
  const name = `Cup ${Date.now()}`

  const office = await browser.newPage()
  await signIn(office, 'erp', 'admin1')
  await office.goto('/admin/campus/tournaments')
  await office.getByLabel('Tournament name').fill(name)
  await office.getByLabel('Sport').fill('Football')
  await office.getByLabel('Season').fill('2026-27')
  await office.getByRole('button', { name: 'Add tournament' }).click()
  await expect(office.getByRole('status').filter({ hasText: 'Tournament added as a draft.' })).toHaveCount(1)

  const card = office.getByRole('region', { name })
  await card.getByLabel('Home opponent name').fill('Hawks')
  await card.getByLabel('Away opponent name').fill('Owls')
  await card.getByLabel('Match starts').fill('2026-11-20T10:00')
  await card.getByRole('button', { name: 'Add match' }).click()
  await expect(office.getByRole('status').filter({ hasText: 'Match added.' })).toHaveCount(1)

  const result = card.getByRole('form', { name: 'Result of Hawks v Owls' })
  await result.getByLabel('Hawks score').fill('3')
  await result.getByLabel('Owls score').fill('1')
  await result.getByLabel('Result').selectOption('HOME_WIN')
  await result.getByRole('button', { name: 'Save result' }).click()
  await expect(office.getByRole('status').filter({ hasText: 'Result of Hawks v Owls saved.' })).toHaveCount(1)
  await card.getByRole('button', { name: `Publish ${name}` }).click()
  await expect(office.getByRole('status').filter({ hasText: 'Tournament published.' })).toHaveCount(1)

  const student = await browser.newPage()
  await signIn(student, 'erp', 'student1')
  await student.goto('/campus/sports')
  const published = student.getByRole('region', { name })
  await expect(published.getByRole('table', { name: `Standings of ${name}` })).toBeVisible()
  await expect(published.getByRole('table', { name: `Matches of ${name}` }).getByText('3 – 1 · Hawks won')).toBeVisible()
})
