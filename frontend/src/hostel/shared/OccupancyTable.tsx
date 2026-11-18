import type { HostelOccupancy } from '../../types/hostel'
import { Badge, Button, ButtonLink } from '../../ui'

export function OccupancyTable({
  rows,
  selected,
  onSelect,
  linkTo,
}: {
  rows: HostelOccupancy[]
  selected: number | null
  onSelect: (hostelId: number) => void
  linkTo?: string
}) {
  return (
    <div className="overflow-x-auto rounded-panel border border-line bg-surface">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">Occupancy by hostel</caption>
        <thead className="border-b border-line bg-surface-muted text-xs text-ink-muted">
          <tr>
            <th scope="col" className="px-4 py-2 font-medium">Hostel</th>
            <th scope="col" className="px-4 py-2 font-medium">Beds</th>
            <th scope="col" className="px-4 py-2 font-medium">Occupied</th>
            <th scope="col" className="px-4 py-2 font-medium">Maintenance</th>
            <th scope="col" className="px-4 py-2 font-medium">Free</th>
            <th scope="col" className="px-4 py-2 font-medium"><span className="sr-only">Bed map</span></th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map((row) => (
            <tr key={row.hostelId}>
              <th scope="row" className="px-4 py-2 font-medium text-ink">
                {row.name} <span className="font-mono text-xs text-ink-muted">{row.code}</span>
                {!row.active && <span className="ml-2"><Badge tone="neutral">Inactive</Badge></span>}
              </th>
              <td className="px-4 py-2">{row.beds}</td>
              <td className="px-4 py-2">{row.occupied}</td>
              <td className="px-4 py-2">{row.maintenance}</td>
              <td className="px-4 py-2">{row.available}</td>
              <td className="px-4 py-2">
                {linkTo ? (
                  <ButtonLink to={linkTo} size="sm" variant="secondary">
                    View bed map<span className="sr-only">{` of ${row.name}`}</span>
                  </ButtonLink>
                ) : (
                  <Button size="sm" variant={selected === row.hostelId ? 'primary' : 'secondary'} onClick={() => onSelect(row.hostelId)}>
                    Bed map<span className="sr-only">{` of ${row.name}`}</span>
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
