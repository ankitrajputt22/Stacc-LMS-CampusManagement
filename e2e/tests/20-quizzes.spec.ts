import { expect, test } from '@playwright/test'
import { COURSE_CODE, openCourseSection, signIn } from './support'

const QUESTION = 'A queue serves the most recently added item first.'

test('a faculty member builds a quiz from reviewed bank questions and publishes it', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()

  // A reviewed question of its own, so the journey does not depend on the demo bank.
  await openCourseSection(page, 'Question bank')
  const form = page.getByRole('region', { name: 'Add a question' })
  await form.getByLabel('Question type').selectOption('TRUE_FALSE')
  await form.getByRole('textbox', { name: 'Question', exact: true }).fill(QUESTION)
  await form.getByRole('radio', { name: 'False', exact: true }).check()
  await form.getByRole('button', { name: 'Add question' }).click()
  const item = page.getByRole('region', { name: 'Questions' }).getByRole('listitem').filter({ hasText: QUESTION })
  await item.getByRole('button', { name: /Mark reviewed/ }).click()
  await expect(item.getByText('Reviewed', { exact: true })).toBeVisible()

  await openCourseSection(page, 'Quizzes')
  await expect(page.getByRole('heading', { name: 'Quizzes', level: 1 })).toBeVisible()
  await page.getByRole('button', { name: 'New quiz' }).click()
  const settings = page.getByRole('region', { name: 'New quiz' })
  await settings.getByLabel('Title').fill('Queue basics')
  await settings.getByLabel('Time limit in minutes (optional)').fill('10')
  await settings.getByLabel('Negative marking (% of a question’s marks)').fill('25')
  await settings.getByRole('button', { name: 'Create quiz' }).click()

  await expect(page.getByRole('heading', { name: 'Queue basics' })).toBeVisible()
  await expect(page.getByText('No questions yet.')).toBeVisible()
  const bank = page.getByRole('region', { name: 'Add from the question bank' })
  await bank.getByLabel('Search the bank').fill('most recently added item first')
  await bank.getByRole('listitem').filter({ hasText: QUESTION }).getByRole('button', { name: /^Add/ }).click()

  const questions = page.getByRole('region', { name: 'Questions' })
  await expect(questions.getByText(QUESTION, { exact: true })).toBeVisible()
  await expect(page.getByText('1 question · 1 mark · 10 minutes · 1 attempt')).toBeVisible()

  await page.getByRole('button', { name: 'Publish quiz' }).click()
  await page.getByRole('button', { name: 'Publish', exact: true }).click()
  await expect(page.getByText('Published', { exact: true })).toBeVisible()
  await expect(questions.getByRole('button', { name: /Remove/ })).toHaveCount(0)
  await expect(page.getByRole('region', { name: 'Add from the question bank' })).toHaveCount(0)

  // The bank now keeps the question as the published quiz asks it.
  await openCourseSection(page, 'Question bank')
  const used = page.getByRole('region', { name: 'Questions' }).getByRole('listitem').filter({ hasText: QUESTION })
  await expect(used.getByText('In a published quiz')).toBeVisible()
  await expect(used.getByRole('button', { name: /^Edit/ })).toHaveCount(0)
})
