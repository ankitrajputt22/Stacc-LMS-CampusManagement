import { expect, test } from '@playwright/test'
import { COURSE_CODE, openCourseSection, signIn } from './support'

const QUESTION = 'A primary key may contain null values.'

test('a faculty member adds a question to the bank, reviews it, offers it for practice, and archives it', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Question bank')
  await expect(page.getByRole('heading', { name: 'Question bank', level: 1 })).toBeVisible()

  const form = page.getByRole('region', { name: 'Add a question' })
  await form.getByLabel('Question type').selectOption('TRUE_FALSE')
  await form.getByRole('textbox', { name: 'Question', exact: true }).fill(QUESTION)
  await form.getByRole('radio', { name: 'False', exact: true }).check()
  await form.getByLabel('Topic (optional)').fill('Keys')
  await form.getByLabel('Difficulty', { exact: true }).selectOption('EASY')
  await form.getByRole('button', { name: 'Add question' }).click()

  // The demo bank already holds reviewed questions, so everything below looks inside this question's own entry.
  const bank = page.getByRole('region', { name: 'Questions' })
  const item = bank.getByRole('listitem').filter({ hasText: QUESTION })
  await expect(item.getByText(QUESTION, { exact: true })).toBeVisible()
  await expect(item.getByText('Answer: False')).toBeVisible()
  await expect(item.getByText('Needs review')).toBeVisible()

  await item.getByRole('button', { name: /Mark reviewed/ }).click()
  await expect(item.getByText('Reviewed', { exact: true })).toBeVisible()
  await item.getByRole('button', { name: /OK for practice/ }).click()
  await expect(item.getByText('OK for practice', { exact: true })).toBeVisible()

  await item.getByRole('button', { name: /^Archive\s*question/ }).click()
  await page.getByRole('button', { name: 'Archive question', exact: true }).click()
  await expect(bank.getByText(QUESTION, { exact: true })).toHaveCount(0)
  await bank.getByLabel('Status').selectOption('ARCHIVED')
  await expect(item.getByText(QUESTION, { exact: true })).toBeVisible()
  await expect(item.getByText('Archived', { exact: true })).toBeVisible()
})
