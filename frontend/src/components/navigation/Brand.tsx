import { Link } from 'react-router'

type BrandProps = {
  onNavigate?: () => void
  /** On the sign-in page there is nowhere to go yet, so the brand is plain text there. */
  asLink?: boolean
}

export function Brand({ onNavigate, asLink = true }: BrandProps) {
  const mark = (
    <>
      <span
        aria-hidden="true"
        className="flex size-8 items-center justify-center rounded-control bg-stacc-primary text-xs font-bold tracking-wide text-white"
      >
        ST
      </span>
      <span className="text-lg font-bold tracking-tight text-ink">Stacc</span>
    </>
  )

  if (!asLink) {
    return <div className="flex items-center gap-2.5">{mark}</div>
  }

  return (
    <Link
      to="/"
      onClick={onNavigate}
      className="flex items-center gap-2.5 rounded-control focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-stacc-primary"
    >
      {mark}
    </Link>
  )
}
