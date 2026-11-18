import type { RubricDraft } from '../teaching/rubricDraft'
import type { AiRubricSuggestion } from '../types/ai'

export function rubricDraftOf(suggestion: AiRubricSuggestion): RubricDraft {
  return {
    title: suggestion.title,
    description: '',
    criteria: suggestion.criteria.map((criterion) => ({
      title: criterion.title,
      description: criterion.description ?? '',
      maxPoints: String(criterion.maxPoints),
      levels: criterion.levels.map((level) => ({ title: level.title, description: level.description ?? '', points: String(level.points) })),
    })),
  }
}
