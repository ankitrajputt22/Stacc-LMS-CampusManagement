import type { ReactNode } from 'react'
import { ActionButton } from '../components/ActionButton'
import { statusWord } from './masterStatus'

export type Column<T> = {
  header: string
  cell: (row: T) => ReactNode
}

type MasterListProps<T> = {
  /** Names the list for screen readers, such as "Departments". */
  label: string
  rows: T[]
  columns: Column<T>[]
  rowKey: (row: T) => number
  /** How the row's Edit button is named for screen readers, such as "department CSE". */
  rowName: (row: T) => string
  onEdit: (row: T) => void
}

/**
 * The records of one master: a compact table on wider screens, and stacked records on phones, so no page ever scrolls
 * sideways. Each record has Edit and nothing else; academic masters are never deleted.
 */
export function MasterList<T>({ label, rows, columns, rowKey, rowName, onEdit }: MasterListProps<T>) {
  return (
    <>
      <div className="hidden overflow-hidden rounded-panel border border-line bg-surface md:block">
        <table className="w-full table-fixed text-left text-sm">
          <caption className="sr-only">{label}</caption>
          <thead className="border-b border-line bg-surface-muted text-xs font-semibold text-ink-muted">
            <tr>
              {columns.map((column) => (
                <th key={column.header} scope="col" className="px-4 py-2.5 font-semibold">
                  {column.header}
                </th>
              ))}
              <th scope="col" className="w-24 px-4 py-2.5 font-semibold">
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {rows.map((row) => (
              <tr key={rowKey(row)} className="align-top">
                {columns.map((column) => (
                  <td key={column.header} className="px-4 py-3 break-words text-ink">
                    {column.cell(row)}
                  </td>
                ))}
                <td className="px-4 py-2.5 text-right">
                  <ActionButton focusKey={`edit-${rowKey(row)}`} onClick={() => onEdit(row)}>
                    Edit<span className="sr-only"> {rowName(row)}</span>
                  </ActionButton>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ul aria-label={label} className="divide-y divide-line rounded-panel border border-line bg-surface md:hidden">
        {rows.map((row) => (
          <li key={rowKey(row)} className="px-4 py-3">
            <dl className="space-y-1.5">
              {columns.map((column) => (
                <div key={column.header}>
                  <dt className="text-xs text-ink-muted">{column.header}</dt>
                  <dd className="text-sm break-words text-ink">{column.cell(row)}</dd>
                </div>
              ))}
            </dl>
            <ActionButton className="mt-2" focusKey={`edit-${rowKey(row)}-mobile`} onClick={() => onEdit(row)}>
              Edit<span className="sr-only"> {rowName(row)}</span>
            </ActionButton>
          </li>
        ))}
      </ul>
    </>
  )
}

/** A record's status as a word in a quiet tag, never as colour alone. */
export function MasterStatus({ status }: { status: string }) {
  return (
    <span className="inline-block rounded-sm border border-line-strong px-1.5 py-0.5 text-xs font-medium text-ink">
      {statusWord(status)}
    </span>
  )
}
