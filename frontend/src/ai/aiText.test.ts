import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it } from 'vitest'
import { AI_PAUSED_FOR_QUIZ, aiFailure, STUDENT_AI_TURNED_OFF } from './aiText'

function refused(status: number, message: string): AxiosError {
  const config = { headers: new AxiosHeaders() }
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, null, {
    status, statusText: '', headers: {}, config, data: { status, message, fieldErrors: [] },
  })
}

describe('aiFailure', () => {
  it('says when the assistant is turned off or busy, in the backend words', () => {
    expect(aiFailure(refused(409, STUDENT_AI_TURNED_OFF), 'student')).toEqual({ kind: 'turnedOff', message: STUDENT_AI_TURNED_OFF })
    expect(aiFailure(refused(429, 'AI is busy right now. Try again in a minute.'), 'student')).toEqual({
      kind: 'busy', message: 'AI is busy right now. Try again in a minute.',
    })
    expect(aiFailure(refused(409, 'Something else.'), 'student')).toEqual({ kind: 'notReady', message: 'Something else.' })
    expect(aiFailure(refused(409, AI_PAUSED_FOR_QUIZ), 'student')?.kind).toBe('paused')
  })
})
