import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import type { AiJob, AiKnowledgeStatus } from '../types/ai'
import { KnowledgePanel } from './KnowledgePanel'

vi.mock('../api/aiApi')

const ready: AiKnowledgeStatus = {
  enabled: true, status: 'READY', stale: true, indexedAt: '2026-11-10T05:00:00Z', publishedSourceCount: 4, failureMessage: null, job: null,
}
const queued: AiJob = { jobId: 1, status: 'QUEUED', createdAt: '2026-11-15T05:00:00Z', startedAt: null, finishedAt: null, resultSummary: null, errorMessage: null }

describe('KnowledgePanel', () => {
  it('queues a refresh and checks again only while the job is queued or running', { timeout: 15000 }, async () => {
    vi.mocked(api.getFacultyAiStatus)
      .mockResolvedValueOnce(ready)
      .mockResolvedValueOnce({ ...ready, job: queued })
      .mockResolvedValue({ ...ready, stale: false, job: { ...queued, status: 'SUCCEEDED', finishedAt: '2026-11-15T05:01:00Z', resultSummary: 'Indexed 4 sources.' } })
    vi.mocked(api.queueFacultyAiRefresh).mockResolvedValue(queued)
    render(<KnowledgePanel lmsCourseId={3} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Refresh AI knowledge' }))
    expect(api.queueFacultyAiRefresh).toHaveBeenCalledWith(3, expect.anything())
    expect(await screen.findByText('Refresh queued. It starts in a moment.')).toBeInTheDocument()

    // The panel asks again after its 3-second wait while the job is queued.
    expect(await screen.findByText(/Indexed 4 sources\./, undefined, { timeout: 5000 })).toBeInTheDocument()

    // Once the job has finished, nothing asks again.
    const calls = vi.mocked(api.getFacultyAiStatus).mock.calls.length
    await new Promise((resolve) => setTimeout(resolve, 3500))
    expect(vi.mocked(api.getFacultyAiStatus).mock.calls.length).toBe(calls)
  })
})
