import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a coach builds a team roster and the captain sees it', async ({ browser }) => {
  const name = `First XI ${Date.now()}`

  const student = await browser.newPage()
  await signIn(student, 'erp', 'cse24002')
  await student.goto('/campus/sports')
  await expect(student.getByRole('button', { name: /^(Join|Leave)\s*Cricket Team$/ })).toBeVisible()
  const join = student.getByRole('button', { name: /^Join\s*Cricket Team$/ })
  if (await join.isVisible()) {
    await join.click()
    await expect(student.getByRole('button', { name: /^Leave\s*Cricket Team$/ })).toBeVisible()
  }

  const coach = await browser.newPage()
  await signIn(coach, 'erp', 'emp1022')
  await coach.goto('/campus/sports')
  await coach.getByRole('button', { name: /^Teams\s*of Cricket Team$/ }).click()
  const teams = coach.getByRole('region', { name: 'Teams of Cricket Team' })
  await teams.getByLabel('Team name').fill(name)
  await teams.getByLabel('Season').fill('2026-27')
  await teams.getByRole('button', { name: 'Add team' }).click()
  await expect(teams.getByRole('status').filter({ hasText: 'Team added.' })).toHaveCount(1)
  const team = teams.getByRole('region', { name: `${name} 2026-27` })
  await team.getByLabel(/college ID for/).fill('cse24002')
  await team.getByLabel('Role').selectOption('CAPTAIN')
  await team.getByRole('button', { name: 'Add player' }).click()
  await expect(teams.getByRole('status').filter({ hasText: `cse24002 added to ${name}.` })).toHaveCount(1)

  await student.reload()
  await student.getByRole('button', { name: /^Teams\s*of Cricket Team$/ }).click()
  const roster = student.getByRole('region', { name: `${name} 2026-27` })
  await expect(roster.getByText('cse24002')).toBeVisible()
  await expect(roster.getByText('Captain', { exact: true })).toBeVisible()
  await expect(roster.getByRole('button', { name: 'Add player' })).toHaveCount(0)
})
