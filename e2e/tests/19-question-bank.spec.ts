import { expect, test } from '@playwright/test'
import { COURSE_CODE, openCourseSection, signIn } from './support'

test('a faculty member adds a question to the bank, reviews it, offers it for practice, and archives it', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Question bank')
  await expect(page.getByRole('heading', { name: 'Question bank', level: 1 })).toBeVisible()

  const form = page.getByRole('region', { name: 'Add a question' })
  await form.getByLabel('Question type').selectOption('TRUE_FALSE')
  await form.getByRole('textbox', { name: 'Question', exact: true }).fill('A primary key may contain null values.')
  await form.getByRole('radio', { name: 'False', exact: true }).check()
  await form.getByLabel('Topic (optional)').fill('Keys')
  await form.getByLabel('Difficulty', { exact: true }).selectOption('EASY')
  await form.getByRole('button', { name: 'Add question' }).click()

  const bank = page.getByRole('region', { name: 'Questions' })
  await expect(bank.getByText('A primary key may contain null values.')).toBeVisible()
  await expect(bank.getByText('Answer: False')).toBeVisible()
  await expect(bank.getByText('Needs review')).toBeVisible()

  await bank.getByRole('button', { name: /Mark reviewed/ }).click()
  await expect(bank.getByText('Reviewed', { exact: true })).toBeVisible()
  await bank.getByRole('button', { name: /OK for practice/ }).click()
  await expect(bank.getByText('OK for practice', { exact: true })).toBeVisible()

  await bank.getByRole('button', { name: /^Archive\s*question/ }).click()
  await page.getByRole('button', { name: 'Archive question', exact: true }).click()
  await expect(bank.getByText('No questions match these filters.')).toBeVisible()
  await bank.getByLabel('Status').selectOption('ARCHIVED')
  await expect(bank.getByText('A primary key may contain null values.')).toBeVisible()
  await expect(bank.getByText('Archived', { exact: true })).toBeVisible()
})
