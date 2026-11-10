import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('the campus office closes a classroom for class hours and students see them', async ({ browser }) => {
  const stamp = Date.now()
  const room = `Room ${stamp}`
  const lecture = `Lecture ${stamp}`

  const office = await browser.newPage()
  await signIn(office, 'erp', 'admin1')
  await office.goto('/admin/campus/facilities')
  await office.getByLabel('Code', { exact: true }).fill(`CR${stamp}`)
  await office.getByLabel('Name', { exact: true }).fill(room)
  await office.getByLabel('Kind').selectOption('CLASSROOM')
  await office.getByRole('button', { name: 'Add facility' }).click()
  await expect(office.getByText('Facility added.')).toBeVisible()

  await office.getByLabel('Facility for class hours').selectOption({ label: room })
  await office.getByLabel('Day', { exact: true }).selectOption('TUESDAY')
  await office.getByLabel('Class starts').fill('09:00')
  await office.getByLabel('Class ends').fill('11:00')
  await office.getByLabel('Class', { exact: true }).fill(lecture)
  await office.getByRole('button', { name: 'Add class hours' }).click()
  await expect(office.getByText('Class hours added.')).toBeVisible()
  await expect(office.getByText(`Tuesday 09:00–11:00 ${lecture}`)).toBeVisible()

  const student = await browser.newPage()
  await signIn(student, 'erp', 'student1')
  await student.goto('/campus/facilities')
  await expect(student.getByText(`Class hours (no bookings): Tuesday 09:00–11:00 ${lecture}`)).toBeVisible()
})
