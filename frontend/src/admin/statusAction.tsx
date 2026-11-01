import type { ReactNode } from 'react'
import type { ActiveStatus } from '../types/admin'
import { Button } from '../ui'
import type { RowActions } from './MasterSection'

export type Toggle = {
  /** Such as "department CSE". */
  name: string
  active: boolean
  /** What deactivating stops, such as "takes no new programs or courses". */
  stops: string
  focusKey: string
  save: (status: ActiveStatus) => Promise<void>
}

/** Activate or Deactivate, confirmed first. Nothing is deleted, and history stays either way. */
export function statusAction(actions: RowActions, toggle: Toggle): ReactNode {
  const target: ActiveStatus = toggle.active ? 'INACTIVE' : 'ACTIVE'
  const verb = toggle.active ? 'Deactivate' : 'Activate'
  return (
    <Button
      variant="secondary"
      size="sm"
      data-focus-key={toggle.focusKey}
      onClick={() =>
        actions.confirm(
          {
            question: `${verb} ${toggle.name}?`,
            detail: toggle.active
              ? `It stays in every list and keeps its history, but ${toggle.stops} until it is activated again.`
              : `It can be used for new records again.`,
            confirmLabel: verb,
            busyLabel: toggle.active ? 'Deactivating…' : 'Activating…',
            run: async () => {
              await toggle.save(target)
              return `${toggle.name.charAt(0).toUpperCase()}${toggle.name.slice(1)} ${toggle.active ? 'deactivated' : 'activated'}.`
            },
          },
          toggle.focusKey,
        )
      }
    >
      {verb}
      <span className="sr-only"> {toggle.name}</span>
    </Button>
  )
}
