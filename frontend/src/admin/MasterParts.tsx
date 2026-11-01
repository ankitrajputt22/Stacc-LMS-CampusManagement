import { Badge } from '../ui'
import { statusTone, statusWord } from './masterStatus'

/** A record's status as a word in a badge; the word carries the meaning, never the colour alone. */
export function MasterStatus({ status }: { status: string }) {
  return <Badge tone={statusTone(status)}>{statusWord(status)}</Badge>
}

/** A code and a name, such as "CS301 · Data Structures", read as "CS301, Data Structures". */
export function CodeName({ code, name }: { code: string; name: string }) {
  return (
    <>
      <span className="font-medium">{code}</span>
      <span aria-hidden="true"> · </span>
      <span className="sr-only">, </span>
      {name}
    </>
  )
}
