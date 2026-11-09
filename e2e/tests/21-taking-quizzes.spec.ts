import { expect, test } from '@playwright/test'
import { COURSE_NAME, openCourseSection, signIn } from './support'

const QUIZ = 'Stacks and queues check'

test('a student takes a timed quiz, with answers saved as they go, and submits it', async ({ page }) => {
  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await openCourseSection(page, 'Quizzes')
  await expect(page.getByRole('heading', { name: 'Quizzes', level: 1 })).toBeVisible()

  await page.getByRole('link', { name: QUIZ }).click()
  await expect(page.getByRole('heading', { name: QUIZ })).toBeVisible()
  await expect(page.getByText('15 minutes from the start')).toBeVisible()
  await page.getByRole('button', { name: 'Start quiz' }).click()
  await page.getByRole('button', { name: 'Start', exact: true }).click()

  await expect(page.getByRole('heading', { name: 'Quiz attempt', level: 1 })).toBeVisible()
  await expect(page.getByText(/Time left \d+:\d\d/)).toBeVisible()
  const questions = page.getByRole('region', { name: 'Questions' })

  await questions.getByRole('radio', { name: 'True', exact: true }).check()
  await questions.getByRole('radio', { name: 'Queue', exact: true }).check()
  await questions.getByRole('checkbox', { name: 'Reading an item by its index' }).check()
  await questions.getByRole('checkbox', { name: 'Writing an item at its index' }).check()
  await questions.getByLabel('Answer to question 4').fill('When items are often inserted or removed in the middle.')
  await questions.getByLabel('Answer to question 4').blur()
  await expect(questions.getByText('Saved', { exact: true })).toHaveCount(4)
  await expect(page.getByText('4 of 4 questions answered.')).toBeVisible()

  // The saved answers come back after a reload, from the server.
  await page.reload()
  await expect(questions.getByRole('radio', { name: 'Queue', exact: true })).toBeChecked()
  await expect(questions.getByLabel('Answer to question 4')).toHaveValue('When items are often inserted or removed in the middle.')

  await page.getByRole('button', { name: 'Submit quiz' }).click()
  await page.getByRole('button', { name: 'Submit', exact: true }).click()
  await expect(page.getByText('Quiz submitted successfully.')).toBeVisible()
  await expect(questions.getByRole('radio', { name: 'Queue', exact: true })).toBeDisabled()

  await page.getByRole('link', { name: 'Back to the quiz' }).click()
  const attempts = page.getByRole('region', { name: 'Your attempts' })
  await expect(attempts.getByText('Submitted').first()).toBeVisible()
  await expect(page.getByRole('button', { name: 'Start quiz' })).toBeVisible()
})
