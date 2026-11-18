import { roleLabel, type AccountRole } from '../../auth/roles'

type AccountRolesProps = {
  roles: readonly AccountRole[]
  className?: string
}

export function AccountRoles({ roles, className = '' }: AccountRolesProps) {
  if (roles.length === 0) {
    return null
  }

  const labels = roles.map(roleLabel)

  return (
    <span className={className} title={labels.join(', ')}>
      <span className="sr-only">Roles: {labels.join(', ')}</span>
      <span aria-hidden="true">{labels.join(' · ')}</span>
    </span>
  )
}
