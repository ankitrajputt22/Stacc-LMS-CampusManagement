import { useState } from 'react'
import { vacateAllocation } from '../../api/hostelApi'
import { ActionButton } from '../../components/ActionButton'
import { StatusConfirmation } from '../../components/StatusConfirmation'
import { refusalText } from '../hostelText'

/** Vacate for one ACTIVE allocation, confirmed first. History is kept; nothing is deleted. */
export function VacateButton({ allocationId, label, onVacated }: { allocationId: number; label: string; onVacated: () => void }) {
  const [confirming, setConfirming] = useState(false)
  if (!confirming) {
    return (
      <ActionButton onClick={() => setConfirming(true)}>
        Vacate<span className="sr-only">: {label}</span>
      </ActionButton>
    )
  }
  return (
    <StatusConfirmation
      question="Vacate this hostel allocation?"
      detail="The allocation history will be preserved and the bed will become available again."
      confirmLabel="Vacate allocation"
      busyLabel="Vacating…"
      onCancel={() => setConfirming(false)}
      onConfirm={async () => {
        await vacateAllocation(allocationId)
        setConfirming(false)
        onVacated()
      }}
      describeRefusal={(status, message) => refusalText(status, message, 'vacate the allocation')}
    />
  )
}
