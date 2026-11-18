import type { ReactNode } from 'react'

export type Column<Row> = {
  key: string
  header: string
  cell: (row: Row) => ReactNode
  align?: 'left' | 'right'
  primary?: boolean
}

type DataTableProps<Row> = {
  caption: string
  columns: readonly Column<Row>[]
  rows: readonly Row[]
  rowKey: (row: Row) => string | number
  empty?: ReactNode
}

export function DataTable<Row>({ caption, columns, rows, rowKey, empty }: DataTableProps<Row>) {
  if (rows.length === 0 && empty) {
    return <>{empty}</>
  }
  const primary = columns.find((column) => column.primary) ?? columns[0]
  const others = columns.filter((column) => column !== primary)
  return (
    <>
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full border-collapse text-left text-sm">
          <caption className="sr-only">{caption}</caption>
          <thead>
            <tr className="border-b border-line bg-sky-50/70">
              {columns.map((column) => (
                <th
                  key={column.key}
                  scope="col"
                  className={`px-4 py-3 text-sm font-bold text-ink ${column.align === 'right' ? 'text-right' : ''}`}
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {rows.map((row) => (
              <tr key={rowKey(row)} className="align-top hover:bg-surface-muted/60">
                {columns.map((column) =>
                  column === primary ? (
                    <th key={column.key} scope="row" className="px-4 py-3 font-medium break-words text-ink">
                      {column.cell(row)}
                    </th>
                  ) : (
                    <td key={column.key} className={`px-4 py-3 break-words text-ink ${column.align === 'right' ? 'text-right' : ''}`}>
                      {column.cell(row)}
                    </td>
                  ),
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ul aria-label={caption} className="divide-y divide-line md:hidden">
        {rows.map((row) => (
          <li key={rowKey(row)} className="px-4 py-3">
            <p className="text-sm font-medium break-words text-ink">{primary.cell(row)}</p>
            <dl className="mt-1.5 grid grid-cols-[minmax(0,8rem)_minmax(0,1fr)] gap-x-3 gap-y-1 text-sm">
              {others.map((column) => (
                <div key={column.key} className="contents">
                  <dt className="text-ink-muted">{column.header}</dt>
                  <dd className="break-words text-ink">{column.cell(row)}</dd>
                </div>
              ))}
            </dl>
          </li>
        ))}
      </ul>
    </>
  )
}
