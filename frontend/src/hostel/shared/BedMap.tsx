import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { getApiError, getHttpStatus } from '../../api/apiError'
import { checkInAllocation, describeHostelRoom, getHostelBedMap, setBedMaintenance } from '../../api/hostelApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { EmptyState } from '../../components/states/EmptyState'
import { SectionState } from '../../courses/SectionState'
import type { BedMapCell, BedMapRoom, BedOccupancy } from '../../types/hostel'
import { Alert, Badge, type BadgeTone, Button, Card, TextField } from '../../ui'

const STATE: Record<BedOccupancy, { label: string; tone: BadgeTone }> = {
  AVAILABLE: { label: 'Free', tone: 'success' },
  OCCUPIED: { label: 'Occupied', tone: 'brand' },
  MAINTENANCE: { label: 'Maintenance', tone: 'warning' },
  INACTIVE: { label: 'Not in use', tone: 'neutral' },
}

function failure(error: unknown): string | null {
  const status = getHttpStatus(error)
  if (status === 401) {
    return null
  }
  const message = getApiError(error)?.message
  return status === 403 ? 'You don’t have access to do this.' : message && status !== null && status < 500 ? message : 'Couldn’t save. Try again.'
}

function RoomDetailsForm({ room, onSaved }: { room: BedMapRoom; onSaved: () => Promise<void> }) {
  const [block, setBlock] = useState(room.block ?? '')
  const [floor, setFloor] = useState(room.floorNumber === null ? '' : String(room.floorNumber))
  const [type, setType] = useState(room.roomType ?? '')
  const [error, setError] = useState<string | null>(null)
  return (
    <form
      className="grid gap-2 sm:grid-cols-[1fr_6rem_1fr_auto] sm:items-end"
      onSubmit={(event) => {
        event.preventDefault()
        if (floor !== '' && !/^-?\d{1,3}$/.test(floor)) {
          setError('Enter the floor as a whole number.')
          return
        }
        setError(null)
        void describeHostelRoom(room.roomId, {
          block: block.trim() || null,
          floorNumber: floor === '' ? null : Number(floor),
          roomType: type.trim() || null,
          notes: null,
        })
          .then(onSaved)
          .catch((caught: unknown) => setError(failure(caught)))
      }}
    >
      <TextField label={`Block of room ${room.roomNumber}`} value={block} maxLength={50} onChange={(event) => setBlock(event.target.value)} />
      <TextField label="Floor" value={floor} inputMode="numeric" onChange={(event) => setFloor(event.target.value)} />
      <TextField label="Type" value={type} maxLength={50} hint="Such as Double" onChange={(event) => setType(event.target.value)} />
      <Button type="submit" variant="secondary" size="sm">
        Save details
      </Button>
      {error && <p className="text-sm text-danger sm:col-span-4">{error}</p>}
    </form>
  )
}

function BedButton({ side, bed, onChanged }: { side: 'admin' | 'warden'; bed: BedMapCell; onChanged: (message: string) => Promise<void> }) {
  const [error, setError] = useState<string | null>(null)
  const act = (action: () => Promise<void>, done: string) =>
    void action()
      .then(() => onChanged(done))
      .catch((caught: unknown) => setError(failure(caught)))
  return (
    <li className="rounded-control border border-line p-2 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium text-ink">Bed {bed.bedNumber}</span>
        <Badge tone={STATE[bed.state].tone}>{STATE[bed.state].label}</Badge>
      </div>
      {bed.studentLoginId && (
        <p className="mt-1 text-ink">
          {bed.studentLoginId}
          <span className="text-ink-muted">{bed.checkedIn ? ' · checked in' : ' · not checked in yet'}</span>
        </p>
      )}
      <div className="mt-2 flex flex-wrap gap-2">
        {bed.allocationId !== null && !bed.checkedIn && (
          <Button size="sm" variant="secondary" onClick={() => act(() => checkInAllocation(side, bed.allocationId!, null), `Checked in ${bed.studentLoginId}.`)}>
            Check in<span className="sr-only">{` ${bed.studentLoginId}`}</span>
          </Button>
        )}
        {side === 'admin' && (bed.state === 'AVAILABLE' || bed.state === 'MAINTENANCE') && (
          <Button
            size="sm"
            variant="secondary"
            onClick={() =>
              act(
                () => setBedMaintenance(bed.bedId, bed.state !== 'MAINTENANCE'),
                bed.state === 'MAINTENANCE' ? `Bed ${bed.bedNumber} is back in use.` : `Bed ${bed.bedNumber} is under maintenance.`,
              )
            }
          >
            {bed.state === 'MAINTENANCE' ? 'Back in use' : 'Maintenance'}
            <span className="sr-only">{`: bed ${bed.bedNumber}`}</span>
          </Button>
        )}
      </div>
      {error && <p className="mt-1 text-danger">{error}</p>}
    </li>
  )
}

/**
 * A hostel's rooms and beds, by block and floor, with each bed's state in words. Wardens and admins check students in;
 * admins also set room details and put free beds under maintenance. Only what the API sends is shown.
 */
export function BedMap({ side, hostelId }: { side: 'admin' | 'warden'; hostelId: number }) {
  const queryClient = useQueryClient()
  const map = useApiQuery(queryKeys.hostel.bedMap(side, hostelId), (signal) => getHostelBedMap(side, hostelId, signal))
  const [notice, setNotice] = useState<string | null>(null)

  async function changed(message: string) {
    setNotice(message)
    await queryClient.invalidateQueries({ queryKey: ['hostel'] })
  }

  if (map.status !== 'loaded') {
    return (
      <Card title="Bed map">
        <SectionState request={map} what="the bed map" />
      </Card>
    )
  }
  return (
    <div className="space-y-4">
      {notice && <Alert tone="success">{notice}</Alert>}
      {map.data.rooms.length === 0 ? (
        <Card title={`${map.data.name} bed map`}>
          <EmptyState title="No rooms yet." description="Add rooms and beds under Hostels & Rooms." />
        </Card>
      ) : (
        map.data.rooms.map((room) => (
          <Card
            key={room.roomId}
            title={`Room ${room.roomNumber}`}
            description={[room.block && `Block ${room.block}`, room.floorNumber !== null && `Floor ${room.floorNumber}`, room.roomType, !room.active && 'Not in use']
              .filter(Boolean)
              .join(' · ')}
          >
            <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {room.beds.map((bed) => (
                <BedButton key={bed.bedId} side={side} bed={bed} onChanged={changed} />
              ))}
            </ul>
            {side === 'admin' && (
              <div className="mt-3 border-t border-line pt-3">
                <RoomDetailsForm room={room} onSaved={() => changed(`Saved the details of room ${room.roomNumber}.`)} />
              </div>
            )}
          </Card>
        ))
      )}
    </div>
  )
}
