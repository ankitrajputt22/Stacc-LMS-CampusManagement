import { useState } from 'react'
import { vacateAllocationWithNote } from '../../api/hostelApi'
import { ActionButton } from '../../components/ActionButton'
import { StatusConfirmation } from '../../components/StatusConfirmation'
import { TextField } from '../../ui'
import { refusalText } from '../hostelText'

/** Vacate for one ACTIVE allocation, confirmed first. History is kept; nothing is deleted. */
export function VacateButton({ allocationId, label, onVacated }: { allocationId: number; label: string; onVacated: () => void }) {
  const [confirming, setConfirming] = useState(false)
  const [condition, setCondition] = useState('')
  if (!confirming) {
    return (
      <ActionButton onClick={() => setConfirming(true)}>
        Vacate<span className="sr-only">: {label}</span>
      </ActionButton>
    )
  }
  return (
    <div className="space-y-2">
      <TextField
        label="Room condition (optional)"
        hint="How the student left the room, for the record."
        value={condition}
        maxLength={500}
        onChange={(event) => setCondition(event.target.value)}
      />
      <StatusConfirmation
      question="Vacate this hostel allocation?"
      detail="The allocation history will be preserved and the bed will become available again."
      confirmLabel="Vacate allocation"
      busyLabel="Vacating…"
      onCancel={() => setConfirming(false)}
      onConfirm={async () => {
        await vacateAllocationWithNote(allocationId, condition.trim() === '' ? null : condition.trim())
        setConfirming(false)
        onVacated()
      }}
      describeRefusal={(status, message) => refusalText(status, message, 'vacate the allocation')}
      />
    </div>
  )
}
