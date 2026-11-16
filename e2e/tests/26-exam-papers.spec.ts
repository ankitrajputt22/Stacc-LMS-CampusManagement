import { expect, test } from '@playwright/test'
import { COURSE_CODE, openCourseSection, signIn } from './support'

test('a faculty member plans a paper with a blueprint, builds it from the bank, prints it, and makes it final', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Exam papers')
  await expect(page.getByRole('heading', { name: 'Exam papers', level: 1 })).toBeVisible()

  // A one-row blueprint: one reviewed true-or-false question worth 3 marks (the demo bank has one).
  const blueprints = page.getByRole('region', { name: 'Blueprints' })
  await blueprints.getByRole('button', { name: 'New blueprint' }).click()
  await blueprints.getByLabel('Blueprint title').fill('Journey blueprint')
  await blueprints.getByLabel('Section, row 1').fill('Section A')
  await blueprints.getByLabel('Question type, row 1').selectOption('TRUE_FALSE')
  await blueprints.getByLabel('Marks each, row 1').fill('3')
  await blueprints.getByRole('button', { name: 'Create blueprint' }).click()
  const blueprint = blueprints.getByRole('listitem').filter({ hasText: 'Journey blueprint' }).first()
  await expect(blueprint.getByText('1 question × 3 marks: True or false')).toBeVisible()

  await blueprint.getByRole('button', { name: 'Build paper from Journey blueprint' }).click()
  await blueprint.getByLabel('Paper title').fill('Journey paper')
  await blueprint.getByLabel('Duration in minutes').fill('30')
  await blueprint.getByRole('button', { name: 'Build paper' }).click()

  await expect(page.getByRole('heading', { name: 'Journey paper' })).toBeVisible()
  await expect(page.getByText('1 question · 3 marks · 30 minutes')).toBeVisible()

  // The Word document downloads through the API.
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Download Word', exact: true }).click()
  expect((await download).suggestedFilename()).toBe('Journey paper.docx')

  await page.getByRole('link', { name: 'Print view' }).click()
  const printed = page.getByRole('article', { name: 'Journey paper as printed' })
  await expect(printed.getByText('Maximum marks: 3', { exact: false })).toBeVisible()
  await expect(printed.getByRole('heading', { name: 'Section A' })).toBeVisible()
  await expect(printed.getByRole('region', { name: 'Answer key' })).toHaveCount(0)
  await page.getByRole('checkbox', { name: 'Show answer key' }).check()
  await expect(printed.getByRole('region', { name: 'Answer key' })).toContainText(/True|False/)

  await page.getByRole('link', { name: 'Back to the paper' }).click()
  await page.getByRole('button', { name: 'Make final' }).click()
  await page.getByRole('button', { name: 'Make final' }).last().click()
  await expect(page.getByText('Final', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Save questions' })).toHaveCount(0)
})
