import { useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import {
  createHostel,
  createHostelBed,
  createHostelRoom,
  getHostelBeds,
  getHostelRooms,
  getHostels,
  updateHostel,
  updateHostelBed,
  updateHostelRoom,
} from '../../api/hostelApi'
import { useApiRequest } from '../../api/useApiRequest'
import { ActionButton } from '../../components/ActionButton'
import { Button } from '../../components/Button'
import { AccessDeniedState } from '../../components/states/AccessDeniedState'
import { EmptyState } from '../../components/states/EmptyState'
import { ErrorState } from '../../components/states/ErrorState'
import { LoadingState } from '../../components/states/LoadingState'
import { StatePanel } from '../../components/states/StatePanel'
import { usePendingFocus } from '../../components/usePendingFocus'
import type { Hostel, HostelRoom } from '../../types/hostel'
import {
  HOSTEL_CODE_MAX,
  HOSTEL_DESCRIPTION_MAX,
  HOSTEL_NAME_MAX,
  NUMBER_MAX,
  occupancyLabel,
  plainTagClasses,
  SIGN_IN_AGAIN,
} from '../hostelText'
import { SimpleForm } from './SimpleForm'

type Editing = { kind: 'hostel' | 'room' | 'bed'; id: number | 'new' } | null

const activeTag = (active: boolean) => (
  <span className={plainTagClasses(!active)}>{active ? 'Active' : 'Inactive'}</span>
)

function idParam(value: string | null): number | undefined {
  return value !== null && /^[1-9]\d{0,17}$/.test(value) ? Number(value) : undefined
}

/**
 * Hostels, the rooms of the chosen hostel, and the beds of the chosen room, each with Add and Edit. There is no
 * Delete: anything can be made inactive, but the backend refuses while it holds an active allocation. Occupancy comes
 * from the backend, never from the page.
 */
export function InventorySection() {
  const [searchParams, setSearchParams] = useSearchParams()
  const hostelId = idParam(searchParams.get('hostel'))
  const roomId = idParam(searchParams.get('room'))
  const [editing, setEditing] = useState<Editing>(null)
  const [announcement, setAnnouncement] = useState('')
  const sectionRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(sectionRef)

  const hostels = useApiRequest(getHostels)
  const loadRooms = useMemo(() => (hostelId === undefined ? null : (signal: AbortSignal) => getHostelRooms(hostelId, signal)), [hostelId])
  const rooms = useApiRequest(loadRooms)
  const loadBeds = useMemo(() => (roomId === undefined ? null : (signal: AbortSignal) => getHostelBeds(roomId, signal)), [roomId])
  const beds = useApiRequest(loadBeds)

  const hostel = hostels.status === 'loaded' ? hostels.data.find((row) => row.hostelId === hostelId) : undefined
  const room = rooms.status === 'loaded' ? rooms.data.find((row) => row.roomId === roomId) : undefined

  function done(message: string, focusKey: string) {
    setEditing(null)
    setAnnouncement(message)
    hostels.refresh()
    rooms.refresh()
    beds.refresh()
    focusLater(focusKey)
  }

  function choose(next: { hostel?: number; room?: number }) {
    setEditing(null)
    const params = new URLSearchParams()
    if (next.hostel) params.set('hostel', String(next.hostel))
    if (next.room) params.set('room', String(next.room))
    setSearchParams(params)
  }

  const isEditing = (kind: 'hostel' | 'room' | 'bed', id: number | 'new') => editing?.kind === kind && editing.id === id

  function hostelForm(row?: Hostel) {
    return (
      <SimpleForm
        heading={row ? `Edit ${row.name}` : 'Add hostel'}
        fields={[
          { name: 'code', label: 'Code', kind: 'text', maxLength: HOSTEL_CODE_MAX, required: true, readOnly: row !== undefined,
            hint: row ? 'The code can’t be changed.' : 'Stored in uppercase, such as MAIN. It can’t be changed later.' },
          { name: 'name', label: 'Name', kind: 'text', maxLength: HOSTEL_NAME_MAX, required: true },
          { name: 'description', label: 'Description', kind: 'textarea', maxLength: HOSTEL_DESCRIPTION_MAX },
          ...(row ? [{ name: 'active', label: 'Active: takes new allocations', kind: 'checkbox' as const,
            offNote: 'Existing allocation history is kept. A hostel with active allocations cannot be made inactive.' }] : []),
        ]}
        initial={{ code: row?.code ?? '', name: row?.name ?? '', description: row?.description ?? '', active: row?.active ?? true }}
        submitLabel={row ? 'Save changes' : 'Add hostel'}
        onCancel={() => { setEditing(null); focusLater(row ? `edit-hostel-${row.hostelId}` : 'add-hostel') }}
        onSubmit={async (values) => {
          const description = String(values.description).trim() || null
          if (row) {
            await updateHostel(row.hostelId, { name: String(values.name).trim(), description, active: values.active === true })
            done(`${String(values.name).trim()} saved.`, `edit-hostel-${row.hostelId}`)
          } else {
            const created = await createHostel({ code: String(values.code).trim(), name: String(values.name).trim(), description })
            done(`${created.name} added.`, `edit-hostel-${created.hostelId}`)
          }
        }}
      />
    )
  }

  function roomForm(parent: Hostel, row?: HostelRoom) {
    return (
      <SimpleForm
        heading={row ? `Edit room ${row.roomNumber}` : `Add room to ${parent.name}`}
        fields={[
          { name: 'roomNumber', label: 'Room number', kind: 'text', maxLength: NUMBER_MAX, required: true, readOnly: row?.numberLocked,
            hint: row?.numberLocked ? 'Fixed: a bed of this room has allocation history.' : undefined },
          ...(row ? [{ name: 'active', label: 'Active: takes new allocations', kind: 'checkbox' as const,
            offNote: 'A room with an occupied bed cannot be made inactive.' }] : []),
        ]}
        initial={{ roomNumber: row?.roomNumber ?? '', active: row?.active ?? true }}
        submitLabel={row ? 'Save changes' : 'Add room'}
        onCancel={() => { setEditing(null); focusLater(row ? `edit-room-${row.roomId}` : 'add-room') }}
        onSubmit={async (values) => {
          const roomNumber = String(values.roomNumber).trim()
          if (row) {
            await updateHostelRoom(row.roomId, { roomNumber, active: values.active === true })
            done(`Room ${roomNumber} saved.`, `edit-room-${row.roomId}`)
          } else {
            const created = await createHostelRoom(parent.hostelId, { roomNumber })
            done(`Room ${created.roomNumber} added.`, `edit-room-${created.roomId}`)
          }
        }}
      />
    )
  }

  const state = (request: { status: string; retry: () => void }, loading: string, empty: string, failed: string) => {
    if (request.status === 'loading') return <StatePanel><LoadingState label={loading} /></StatePanel>
    if (request.status === 'denied') return <StatePanel><AccessDeniedState title="You don’t have access to hostel management." message={SIGN_IN_AGAIN} /></StatePanel>
    if (request.status === 'notFound') return <StatePanel><EmptyState title={empty} /></StatePanel>
    if (request.status !== 'loaded') return <StatePanel><ErrorState title={failed} message="Check your connection and try again." onRetry={request.retry} /></StatePanel>
    return null
  }

  return (
    <div ref={sectionRef}>
      <p role="status" className="sr-only">{announcement}</p>

      <section aria-labelledby="hostels-heading">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="hostels-heading" className="text-base font-semibold text-ink">Hostels</h2>
          {editing === null && hostels.status === 'loaded' && (
            <Button data-focus-key="add-hostel" onClick={() => setEditing({ kind: 'hostel', id: 'new' })}>Add hostel</Button>
          )}
        </div>
        {isEditing('hostel', 'new') && <div className="mt-3">{hostelForm()}</div>}
        <div className="mt-3">
          {state(hostels, 'Loading hostels…', 'No hostels configured.', 'Couldn’t load hostels.') ??
            (hostels.status === 'loaded' && hostels.data.length === 0 ? (
              <StatePanel><EmptyState title="No hostels configured." /></StatePanel>
            ) : (
              hostels.status === 'loaded' && (
                <ul aria-label="Hostels" className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
                  {hostels.data.map((row) => (
                    <li key={row.hostelId} className={`px-4 py-3 sm:px-5 ${row.hostelId === hostelId ? 'bg-surface-muted' : ''}`}>
                      {isEditing('hostel', row.hostelId) ? hostelForm(row) : (
                        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                          <div className="min-w-0">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="font-mono text-xs text-ink-muted">{row.code}</span>
                              {activeTag(row.active)}
                            </div>
                            <p className="mt-1 text-sm font-semibold break-words text-ink">{row.name}</p>
                            {row.description && <p className="text-sm break-words text-ink-muted">{row.description}</p>}
                            <p className="mt-0.5 text-xs text-ink-muted">
                              {row.roomCount} rooms · {row.activeBedCount} active beds · {row.occupiedBedCount} occupied
                            </p>
                          </div>
                          <div className="flex flex-wrap gap-2">
                            <ActionButton focusKey={`edit-hostel-${row.hostelId}`} aria-disabled={editing !== null || undefined}
                              onClick={() => editing === null && setEditing({ kind: 'hostel', id: row.hostelId })}>
                              Edit<span className="sr-only"> {row.name}</span>
                            </ActionButton>
                            <ActionButton aria-pressed={row.hostelId === hostelId} onClick={() => choose({ hostel: row.hostelId })}>
                              Rooms<span className="sr-only"> of {row.name}</span>
                            </ActionButton>
                          </div>
                        </div>
                      )}
                    </li>
                  ))}
                </ul>
              )
            ))}
        </div>
      </section>

      {hostel && (
        <section aria-labelledby="rooms-heading" className="mt-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 id="rooms-heading" className="text-base font-semibold break-words text-ink">Rooms of {hostel.name}</h2>
            {editing === null && rooms.status === 'loaded' && (
              <Button data-focus-key="add-room" onClick={() => setEditing({ kind: 'room', id: 'new' })}>Add room</Button>
            )}
          </div>
          {isEditing('room', 'new') && <div className="mt-3">{roomForm(hostel)}</div>}
          <div className="mt-3">
            {state(rooms, 'Loading hostels…', 'No rooms configured.', 'Couldn’t load rooms.') ??
              (rooms.status === 'loaded' && rooms.data.length === 0 ? (
                <StatePanel><EmptyState title="No rooms configured." /></StatePanel>
              ) : (
                rooms.status === 'loaded' && (
                  <ul aria-label={`Rooms of ${hostel.name}`} className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
                    {rooms.data.map((row) => (
                      <li key={row.roomId} className={`px-4 py-3 sm:px-5 ${row.roomId === roomId ? 'bg-surface-muted' : ''}`}>
                        {isEditing('room', row.roomId) ? roomForm(hostel, row) : (
                          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                            <div className="min-w-0">
                              <div className="flex flex-wrap items-center gap-2">
                                <span className="text-sm font-semibold break-words text-ink">Room {row.roomNumber}</span>
                                {activeTag(row.active)}
                              </div>
                              <p className="text-xs text-ink-muted">{row.activeBedCount} active beds · {row.occupiedBedCount} occupied</p>
                            </div>
                            <div className="flex flex-wrap gap-2">
                              <ActionButton focusKey={`edit-room-${row.roomId}`} aria-disabled={editing !== null || undefined}
                                onClick={() => editing === null && setEditing({ kind: 'room', id: row.roomId })}>
                                Edit<span className="sr-only"> room {row.roomNumber}</span>
                              </ActionButton>
                              <ActionButton aria-pressed={row.roomId === roomId} onClick={() => choose({ hostel: hostel.hostelId, room: row.roomId })}>
                                Beds<span className="sr-only"> of room {row.roomNumber}</span>
                              </ActionButton>
                            </div>
                          </div>
                        )}
                      </li>
                    ))}
                  </ul>
                )
              ))}
          </div>
        </section>
      )}

      {hostel && room && (
        <section aria-labelledby="beds-heading" className="mt-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 id="beds-heading" className="text-base font-semibold text-ink">Beds of room {room.roomNumber}</h2>
            {editing === null && beds.status === 'loaded' && (
              <Button data-focus-key="add-bed" onClick={() => setEditing({ kind: 'bed', id: 'new' })}>Add bed</Button>
            )}
          </div>
          {isEditing('bed', 'new') && (
            <div className="mt-3">
              <SimpleForm
                heading={`Add bed to room ${room.roomNumber}`}
                fields={[{ name: 'bedNumber', label: 'Bed number', kind: 'text', maxLength: NUMBER_MAX, required: true }]}
                initial={{ bedNumber: '' }}
                submitLabel="Add bed"
                onCancel={() => { setEditing(null); focusLater('add-bed') }}
                onSubmit={async (values) => {
                  const created = await createHostelBed(room.roomId, { bedNumber: String(values.bedNumber).trim() })
                  done(`Bed ${created.bedNumber} added.`, `edit-bed-${created.bedId}`)
                }}
              />
            </div>
          )}
          <div className="mt-3">
            {state(beds, 'Loading hostels…', 'No beds configured.', 'Couldn’t load beds.') ??
              (beds.status === 'loaded' && beds.data.length === 0 ? (
                <StatePanel><EmptyState title="No beds configured." /></StatePanel>
              ) : (
                beds.status === 'loaded' && (
                  <ul aria-label={`Beds of room ${room.roomNumber}`} className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
                    {beds.data.map((row) => (
                      <li key={row.bedId} className="px-4 py-3 sm:px-5">
                        {isEditing('bed', row.bedId) ? (
                          <SimpleForm
                            heading={`Edit bed ${row.bedNumber}`}
                            fields={[
                              { name: 'bedNumber', label: 'Bed number', kind: 'text', maxLength: NUMBER_MAX, required: true, readOnly: row.numberLocked,
                                hint: row.numberLocked ? 'Fixed: this bed has allocation history.' : undefined },
                              { name: 'active', label: 'Active: takes new allocations', kind: 'checkbox', offNote: 'An occupied bed cannot be made inactive.' },
                            ]}
                            initial={{ bedNumber: row.bedNumber, active: row.active }}
                            submitLabel="Save changes"
                            onCancel={() => { setEditing(null); focusLater(`edit-bed-${row.bedId}`) }}
                            onSubmit={async (values) => {
                              await updateHostelBed(row.bedId, { bedNumber: String(values.bedNumber).trim(), active: values.active === true })
                              done(`Bed ${String(values.bedNumber).trim()} saved.`, `edit-bed-${row.bedId}`)
                            }}
                          />
                        ) : (
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-sm font-semibold text-ink">Bed {row.bedNumber}</span>
                              <span className={plainTagClasses(row.occupancy !== 'AVAILABLE')}>
                                <span className="sr-only">Occupancy: </span>
                                {occupancyLabel(row.occupancy)}
                              </span>
                            </div>
                            <ActionButton focusKey={`edit-bed-${row.bedId}`} aria-disabled={editing !== null || undefined}
                              onClick={() => editing === null && setEditing({ kind: 'bed', id: row.bedId })}>
                              Edit<span className="sr-only"> bed {row.bedNumber}</span>
                            </ActionButton>
                          </div>
                        )}
                      </li>
                    ))}
                  </ul>
                )
              ))}
          </div>
        </section>
      )}
    </div>
  )
}
