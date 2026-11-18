import { Badge } from '../ui'
import { statusTone, statusWord } from './masterStatus'

export function MasterStatus({ status }: { status: string }) {
  return <Badge tone={statusTone(status)}>{statusWord(status)}</Badge>
}

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
