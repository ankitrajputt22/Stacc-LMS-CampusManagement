import { describe, expect, it } from 'vitest'
import { checkRubric, draftTotal, emptyRubric } from './rubricDraft'

describe('checkRubric', () => {
  it('sends a fitting rubric with trimmed text and numbers', () => {
    const { errors, input } = checkRubric({
      title: ' Design ',
      description: '',
      criteria: [
        { title: 'ER diagram', description: '', maxPoints: '12', levels: [{ title: 'Excellent', description: '', points: '12' }] },
        { title: 'Explanation', description: 'Why', maxPoints: '8', levels: [] },
      ],
    })
    expect(errors).toEqual([])
    expect(input).toEqual({
      title: 'Design',
      description: null,
      criteria: [
        { title: 'ER diagram', description: null, maxPoints: 12, levels: [{ title: 'Excellent', description: null, points: 12 }] },
        { title: 'Explanation', description: 'Why', maxPoints: 8, levels: [] },
      ],
    })
  })

  it('names each slip by its criterion and level', () => {
    const { errors, input } = checkRubric({
      title: '',
      description: '',
      criteria: [
        { title: 'Same', description: '', maxPoints: '5', levels: [{ title: '', description: '', points: '6' }] },
        { title: 'same', description: '', maxPoints: '0', levels: [] },
      ],
    })
    expect(input).toBeNull()
    expect(errors).toEqual([
      'Enter a title.',
      'Criterion 1, level 1: enter a title.',
      "Criterion 1, level 1: the points cannot be above the criterion's maximum.",
      'Criterion 2: use a title no other criterion has.',
      'Criterion 2: enter maximum points above 0, with at most 2 decimal places.',
    ])
  })

  it('adds up the points typed so far', () => {
    const draft = emptyRubric()
    draft.criteria = [
      { title: 'A', description: '', maxPoints: '12.5', levels: [] },
      { title: 'B', description: '', maxPoints: 'x', levels: [] },
    ]
    expect(draftTotal(draft)).toBe(12.5)
  })
})
