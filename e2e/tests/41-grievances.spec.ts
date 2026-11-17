import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a grievance goes to its committee only and is resolved and accepted', async ({ browser }) => {
  const stamp = Date.now()
  const name = `Grievance area ${stamp}`

  const admin = await browser.newPage()
  await signIn(admin, 'erp', 'adm1001')
  await admin.goto('/admin/grievances')
  await admin.getByLabel('Code', { exact: true }).fill(`GA${stamp}`)
  await admin.getByLabel('Name', { exact: true }).fill(name)
  await admin.getByLabel('Answer within (days)').fill('7')
  await admin.getByRole('button', { name: 'Add category' }).click()
  await expect(admin.getByRole('status').filter({ hasText: 'Category added.' })).toHaveCount(1)
  const category = admin.getByRole('region', { name })
  await category.getByLabel(/login ID for/).fill('emp1021')
  await category.getByRole('button', { name: 'Add to committee' }).click()
  await expect(category.getByText('emp1021', { exact: true })).toBeVisible()

  const student = await browser.newPage()
  await signIn(student, 'erp', 'cse24001')
  await student.goto('/grievances/new')
  await student.getByLabel('Category').selectOption({ label: `${name} (answer within 7 days)` })
  await student.getByLabel('Subject', { exact: true }).fill('Noise after hours')
  await student.getByLabel('Details', { exact: true }).fill('Loud music in block C every night after midnight.')
  await student.getByRole('button', { name: 'Submit grievance' }).click()
  const submitted = student.getByRole('status').filter({ hasText: /^Grievance GRV-\d{6} submitted\.$/ })
  await expect(submitted).toHaveCount(1)
  const caseNumber = ((await submitted.textContent()) ?? '').match(/GRV-\d{6}/)?.[0] ?? ''

  const member = await browser.newPage()
  await signIn(member, 'erp', 'emp1021')
  await member.goto('/grievances/committee')
  await member.getByRole('table', { name: 'Committee cases' }).getByRole('link', { name: new RegExp(`^${caseNumber}\\b`) }).click()
  await expect(member.getByText('cse24001', { exact: true })).toBeVisible()
  await member.getByLabel('Resolution').fill('The warden spoke to the residents of block C.')
  await member.getByRole('button', { name: 'Resolve' }).click()
  await expect(member.getByRole('status').filter({ hasText: 'Resolved. The complainant can accept or appeal.' })).toHaveCount(1)

  await student.goto('/grievances')
  await student.getByRole('table', { name: 'My grievances' }).getByRole('link', { name: new RegExp(`^${caseNumber}\\b`) }).click()
  await expect(student.getByText('The warden spoke to the residents of block C.')).toBeVisible()
  await student.getByRole('button', { name: 'Accept resolution' }).click()
  await expect(student.getByRole('status').filter({ hasText: 'Resolution accepted. The case is closed.' })).toHaveCount(1)
})
