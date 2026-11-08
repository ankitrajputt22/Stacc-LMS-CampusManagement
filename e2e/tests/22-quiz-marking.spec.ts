import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, openCourseSection, signIn, signOut } from './support'

const QUIZ = 'Stacks and queues check'

test('a teacher marks a written quiz answer and the student then sees the score', async ({ page }) => {
  // student2 takes the quiz and answers only the written question.
  await signIn(page, 'lms', 'student2')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await openCourseSection(page, 'Quizzes')
  await page.getByRole('link', { name: QUIZ }).click()
  await page.getByRole('button', { name: 'Start quiz' }).click()
  await page.getByRole('button', { name: 'Start', exact: true }).click()
  const questions = page.getByRole('region', { name: 'Questions' })
  await questions.getByLabel('Answer to question 4').fill('A linked list grows without copying.')
  await questions.getByLabel('Answer to question 4').blur()
  await expect(questions.getByText('Saved', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Submit quiz' }).click()
  await page.getByRole('button', { name: 'Submit', exact: true }).click()
  await expect(page.getByText('Quiz submitted successfully.')).toBeVisible()
  await expect(page.getByText('Your result appears once your teacher has marked the written answers.')).toBeVisible()
  await signOut(page)

  // faculty1 marks it.
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Quizzes')
  await page.getByRole('link', { name: QUIZ }).click()
  await page.getByRole('link', { name: 'Results' }).click()
  await expect(page.getByRole('heading', { name: 'Quiz results', level: 1 })).toBeVisible()
  await page.getByRole('link', { name: /^student2, attempt 1/ }).click()
  await expect(page.getByText('A linked list grows without copying.')).toBeVisible()
  await page.getByLabel('Marks for question 4').fill('3')
  await page.getByLabel('Feedback for question 4 (optional)').fill('Mention insertion in the middle.')
  await page.getByRole('button', { name: /^Save marks\s*for question 4/ }).click()
  await expect(page.getByText(/Score 3\.00 \/ 10\.00|Score 3 \/ 10/)).toBeVisible()
  await signOut(page)

  // student2 sees the result.
  await signIn(page, 'lms', 'student2')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await openCourseSection(page, 'Quizzes')
  await page.getByRole('link', { name: QUIZ }).click()
  await expect(page.getByRole('region', { name: 'Your attempts' }).getByText(/^3(\.00)? \/ 10(\.00)?$/).first()).toBeVisible()
  await page.getByRole('region', { name: 'Your attempts' }).getByRole('link', { name: 'Attempt 1' }).first().click()
  await expect(page.getByText('Mention insertion in the middle.')).toBeVisible()
})
