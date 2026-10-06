import { Link } from 'react-router'

type BrandProps = {
  onNavigate?: () => void
}

export function Brand({ onNavigate }: BrandProps) {
  return (
    <Link
      to="/"
      onClick={onNavigate}
      className="flex items-center gap-2.5 rounded-control focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-stacc-primary"
    >
      <span
        aria-hidden="true"
        className="flex size-8 items-center justify-center rounded-control bg-stacc-primary text-xs font-bold tracking-wide text-white"
      >
        ST
      </span>
      <span className="text-lg font-bold tracking-tight text-ink">Stacc</span>
    </Link>
  )
}
