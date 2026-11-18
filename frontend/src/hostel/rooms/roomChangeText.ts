import type { RoomChange, RoomChangeSide } from '../../types/hostel'

export function placeText(side: RoomChangeSide): string {
  return `${side.hostel}, room ${side.room}, bed ${side.bed}`
}

export function roomChangeState(change: RoomChange): string {
  switch (change.status) {
    case 'COMPLETED':
      return change.kind === 'MOVE' ? 'Moved' : 'Swapped'
    case 'CANCELLED':
      return 'Cancelled'
    case 'DECLINED':
      return 'Declined by the other student'
  }
  if (change.kind === 'SWAP' && change.partnerResponse === 'PENDING') {
    return 'Waiting for the other student'
  }
  switch (change.approvalStatus) {
    case 'APPROVED':
      return 'Approved by the warden, waiting for the hostel office'
    case 'REJECTED':
      return 'Not approved by the warden'
    case 'CANCELLED':
      return 'Cancelled'
    default:
      return 'Waiting for the warden'
  }
}
