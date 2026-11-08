import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { copyCourseContent, getCopySources, type LmsCopyResult, type LmsCopySource } from '../api/lmsCourseManagementApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { Alert, Button, Card, SelectField } from '../ui'

function sourceLabel(source: LmsCopySource): string {
  const section = source.sectionCode ? ` · Section ${source.sectionCode}` : ''
  const state = source.lmsCourseStatus === 'ARCHIVED' ? 'archived' : source.lmsCourseStatus === 'DRAFT' ? 'draft' : 'published'
  return `${source.academicSessionCode} · ${source.programCode} Semester ${source.semesterNumber}${section} (${state})`
}

function resultText(result: LmsCopyResult): string {
  const plural = (count: number, word: string) => `${count} ${word}${count === 1 ? '' : 's'}`
  const parts = [plural(result.modules, 'module'), plural(result.pages, 'page'), plural(result.files, 'file')]
  return `Copied ${parts.join(', ')}${result.syllabus ? ', and the syllabus text' : ''} as drafts. Publish them when they are ready.`
}

function refusal(status: number | null, apiMessage: string | null): string {
  if (status === 403) {
    return 'You don’t have access to change this course’s content. If your access changed recently, sign out and sign in again.'
  }
  return apiMessage && status !== null && status < 600 && status !== 500 ? apiMessage : 'Couldn’t copy. Check your connection and try again.'
}

/**
 * Copy content from an earlier offering of this course that the faculty member taught: modules, pages, study files,
 * and the syllabus text when this course has none, all as drafts after the current modules. Shown only when there is
 * something to copy from; the backend decides which courses qualify.
 */
export function CopyContentCard({ lmsCourseId, reload }: { lmsCourseId: number; reload: () => void }) {
  const queryClient = useQueryClient()
  const sources = useApiQuery(queryKeys.lms.copySources(lmsCourseId), (signal) => getCopySources(lmsCourseId, signal))
  const [chosen, setChosen] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [done, setDone] = useState<string | null>(null)

  if (sources.status !== 'loaded' || sources.data.length === 0) {
    return null
  }
  const source = sources.data.find((candidate) => String(candidate.lmsCourseId) === chosen) ?? null
  return (
    <Card
      title="Copy from an earlier offering"
      description="Reuse modules, pages, study files, and the syllabus text from a term you taught this course. Everything arrives as a draft; assignments and announcements are not copied."
      className="mb-6"
    >
      {done && (
        <Alert tone="success" className="mb-3">
          <span role="status">{done}</span>
        </Alert>
      )}
      {confirming && source ? (
        <StatusConfirmation
          question={`Copy content from ${source.academicSessionCode}, semester ${source.semesterNumber}?`}
          detail="The copies are added as drafts after your current modules; nothing you already have changes. Students see none of it until you publish."
          confirmLabel="Copy content"
          busyLabel="Copying…"
          describeRefusal={refusal}
          onCancel={() => setConfirming(false)}
          onConfirm={async () => {
            const result = await copyCourseContent(lmsCourseId, source.lmsCourseId)
            setConfirming(false)
            setDone(resultText(result))
            reload()
            await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
          }}
        />
      ) : (
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <SelectField
            label="Copy from"
            className="min-w-0 flex-1"
            value={chosen}
            onChange={(event) => {
              setChosen(event.target.value)
              setDone(null)
            }}
          >
            <option value="">Choose an offering</option>
            {sources.data.map((candidate) => (
              <option key={candidate.lmsCourseId} value={candidate.lmsCourseId}>
                {sourceLabel(candidate)}
              </option>
            ))}
          </SelectField>
          <Button variant="secondary" aria-disabled={source === null || undefined} onClick={() => source && setConfirming(true)}>
            Copy content
          </Button>
        </div>
      )}
    </Card>
  )
}
