import { useState } from 'react'
import { getExamPaper } from '../api/lmsExamPapersApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import { FormattedText } from '../formatting/FormattedText'
import type { ExamPaper } from '../types/lms'
import { Button, ButtonLink, Card } from '../ui'
import { answerKeyText, choiceLetter, marksText, sectionGroups } from './examPaperText'

/** The paper as it is printed: heading, instructions, sections, and questions, then the answer key when shown. */
function PrintedPaper({ paper, withAnswers }: { paper: ExamPaper; withAnswers: boolean }) {
  return (
    <article className="print-area space-y-6 bg-surface p-6 text-ink sm:p-10" aria-label={`${paper.title} as printed`}>
      <header className="space-y-1 border-b border-line pb-4 text-center">
        <h2 className="text-2xl font-bold">{paper.title}</h2>
        <p>{`${paper.courseCode} ${paper.courseName}`}</p>
        <p className="text-sm">
          {[paper.durationMinutes === null ? null : `Time: ${paper.durationMinutes} minutes`, `Maximum marks: ${Number(paper.totalMarks)}`]
            .filter(Boolean)
            .join('    ·    ')}
        </p>
      </header>
      {paper.instructions && (
        <section aria-label="Instructions">
          <h3 className="font-semibold">Instructions</h3>
          <p className="whitespace-pre-line text-sm leading-6">{paper.instructions}</p>
        </section>
      )}
      {sectionGroups(paper.questions).map((group, groupIndex) => (
        <section key={groupIndex} className="space-y-4" aria-label={group.section ?? 'Questions'}>
          {group.section && <h3 className="text-center text-lg font-semibold">{group.section}</h3>}
          <ol className="space-y-4">
            {group.questions.map((question) => (
              <li key={question.id} className="break-inside-avoid space-y-2">
                <div className="flex gap-2">
                  <span className="font-semibold">{question.position}.</span>
                  <div className="min-w-0 flex-1">
                    <FormattedText text={question.text} format={question.textFormat} className="text-sm leading-6" headingLevel={4} />
                  </div>
                  <span className="shrink-0 text-sm font-semibold">{`[${marksText(question.marks)}]`}</span>
                </div>
                {(question.type === 'SINGLE_CHOICE' || question.type === 'MULTIPLE_CHOICE') && (
                  <ul className="space-y-1 pl-6 text-sm">
                    {question.options.map((option, index) => (
                      <li key={index}>{`${choiceLetter(index)} ${option.text}`}</li>
                    ))}
                  </ul>
                )}
                {question.type === 'TRUE_FALSE' && <p className="pl-6 text-sm">(a) True    (b) False</p>}
              </li>
            ))}
          </ol>
        </section>
      ))}
      {withAnswers && (
        <section className="space-y-2 border-t border-line pt-4 print:break-before-page" aria-label="Answer key">
          <h3 className="text-center text-lg font-semibold">Answer key</h3>
          <ol className="space-y-2 text-sm">
            {paper.questions.map((question) => (
              <li key={question.id}>
                <span className="font-semibold">{question.position}. </span>
                {answerKeyText(question)}
                {question.explanation && <p className="whitespace-pre-line pl-5 text-ink-muted">{question.explanation}</p>}
              </li>
            ))}
          </ol>
        </section>
      )}
    </article>
  )
}

/** The print view of a paper: Show answer key, Print, and the paper itself. Only the paper is printed. */
export function ExamPaperPrint({ lmsCourseId, paperId }: { lmsCourseId: number; paperId: number }) {
  const paper = useApiQuery(queryKeys.lms.examPaper(paperId), (signal) => getExamPaper(paperId, signal))
  const [withAnswers, setWithAnswers] = useState(false)

  if (paper.status !== 'loaded') {
    return (
      <Card title="Print view">
        {paper.status === 'notFound' ? (
          <EmptyState title="Paper unavailable" description="This paper could not be found, or it is not in a course you teach." />
        ) : (
          <SectionState request={paper} what="the paper" />
        )}
      </Card>
    )
  }

  return (
    <div className="space-y-4">
      <Card title="Print view" description="Only the paper below is printed. Formatted text prints as shown.">
        <div className="flex flex-wrap items-center gap-3">
          <label className="flex items-center gap-2 text-sm text-ink">
            <input type="checkbox" checked={withAnswers} onChange={(event) => setWithAnswers(event.target.checked)} />
            Show answer key
          </label>
          <Button size="sm" onClick={() => window.print()}>
            Print
          </Button>
          <ButtonLink to={`/teaching/${lmsCourseId}/papers/${paperId}`} variant="secondary" size="sm">
            Back to the paper
          </ButtonLink>
        </div>
      </Card>
      <div className="rounded-md border border-line">
        <PrintedPaper paper={paper.data} withAnswers={withAnswers} />
      </div>
    </div>
  )
}
