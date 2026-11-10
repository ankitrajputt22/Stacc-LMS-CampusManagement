import { describe, expect, it } from 'vitest'
import type { RoomChange } from '../../types/hostel'
import { placeText, roomChangeState } from './roomChangeText'

const side = { loginId: 'student2', hostel: 'Main Hostel', room: '101', bed: 'A' }

function change(overrides: Partial<RoomChange>): RoomChange {
  return {
    changeId: 1,
    kind: 'MOVE',
    requester: side,
    partner: null,
    partnerResponse: null,
    reason: 'Too noisy',
    preference: null,
    status: 'OPEN',
    approvalStatus: 'PENDING',
    createdAt: '2026-11-10T10:00:00Z',
    completedAt: null,
    mine: true,
    canCancel: true,
    canAnswer: false,
    canComplete: false,
    ...overrides,
  }
}

describe('room change text', () => {
  it('names the place and keeps the partner’s answer apart from the warden’s decision', () => {
    expect(placeText(side)).toBe('Main Hostel, room 101, bed A')
    expect(roomChangeState(change({}))).toBe('Waiting for the warden')
    expect(roomChangeState(change({ approvalStatus: 'APPROVED' }))).toBe('Approved by the warden, waiting for the hostel office')
    expect(roomChangeState(change({ kind: 'SWAP', partner: side, partnerResponse: 'PENDING', approvalStatus: null }))).toBe(
      'Waiting for the other student',
    )
    expect(roomChangeState(change({ kind: 'SWAP', status: 'DECLINED', partnerResponse: 'DECLINED' }))).toBe('Declined by the other student')
    expect(roomChangeState(change({ status: 'COMPLETED' }))).toBe('Moved')
  })
})
