import type { LmsRubric } from '../types/lms'

/** A rubric's criteria with their maximum points and levels, read-only. Plain text throughout. */
export function RubricView({ rubric }: { rubric: Pick<LmsRubric, 'title' | 'description' | 'totalPoints' | 'criteria'> }) {
  return (
    <div className="space-y-3">
      {rubric.description && <p className="text-sm break-words whitespace-pre-line text-ink-muted">{rubric.description}</p>}
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-left text-sm">
          <caption className="sr-only">{`Rubric: ${rubric.title}`}</caption>
          <thead>
            <tr className="border-b border-line text-ink-muted">
              <th scope="col" className="py-2 pr-3 font-medium">Criterion</th>
              <th scope="col" className="py-2 pr-3 font-medium">Levels</th>
              <th scope="col" className="py-2 text-right font-medium">Points</th>
            </tr>
          </thead>
          <tbody>
            {rubric.criteria.map((criterion) => (
              <tr key={criterion.id} className="border-b border-line align-top">
                <th scope="row" className="py-2 pr-3 font-medium text-ink">
                  <span className="break-words">{criterion.title}</span>
                  {criterion.description && <span className="mt-0.5 block font-normal break-words text-ink-muted">{criterion.description}</span>}
                </th>
                <td className="py-2 pr-3 text-ink">
                  {criterion.levels.length === 0 ? (
                    <span className="text-ink-muted">Any points up to the maximum</span>
                  ) : (
                    <ul className="space-y-0.5">
                      {criterion.levels.map((level) => (
                        <li key={`${level.title}-${level.points}`} className="break-words">
                          <span className="font-medium">{level.title}</span> ({level.points})
                          {level.description && <span className="text-ink-muted">: {level.description}</span>}
                        </li>
                      ))}
                    </ul>
                  )}
                </td>
                <td className="py-2 text-right text-ink tabular-nums">{criterion.maxPoints}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <th scope="row" colSpan={2} className="py-2 pr-3 text-right font-medium text-ink">Total</th>
              <td className="py-2 text-right font-semibold text-ink tabular-nums">{rubric.totalPoints}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  )
}
