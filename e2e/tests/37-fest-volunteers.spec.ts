import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('the campus office runs a fest and a student volunteers for a shift', async ({ browser }) => {
  const name = `Fest ${Date.now()}`

  const office = await browser.newPage()
  await signIn(office, 'erp', 'admin1')
  await office.goto('/admin/campus/fests')
  await office.getByLabel('Fest name').fill(name)
  await office.getByLabel('First day').fill('2030-02-01')
  await office.getByLabel('Last day').fill('2030-02-02')
  await office.getByRole('button', { name: 'Add fest' }).click()
  await expect(office.getByRole('status')).toHaveText('Fest added as a draft.')
  const card = office.getByRole('region', { name })
  await card.getByLabel('Shift role').fill('Help desk')
  await card.getByLabel('Places').fill('1')
  await card.getByLabel('Shift starts').fill('2030-02-01T09:00')
  await card.getByLabel('Shift ends').fill('2030-02-01T11:00')
  await card.getByRole('button', { name: 'Add shift' }).click()
  await expect(office.getByRole('status')).toHaveText('Shift added.')
  await card.getByRole('button', { name: `Publish ${name}` }).click()
  await expect(office.getByRole('status')).toHaveText('Fest published.')

  const student = await browser.newPage()
  await signIn(student, 'erp', 'student1')
  await student.goto('/campus/fests')
  const festCard = student.getByRole('region', { name })
  await festCard.getByRole('textbox', { name: /^Note for Help desk/ }).fill('Free in the morning')
  await festCard.getByRole('button', { name: 'Volunteer' }).click()
  await expect(student.getByText('Applied. The campus office will accept or decline.')).toBeVisible()
  await expect(festCard.getByText('Applied', { exact: true })).toBeVisible()

  await office.reload()
  await office.getByRole('region', { name }).getByRole('button', { name: 'Accept student1' }).click()
  await expect(office.getByRole('status')).toHaveText('student1 accepted.')
  await expect(office.getByRole('region', { name }).getByText('1 of 1 places taken')).toBeVisible()
})
