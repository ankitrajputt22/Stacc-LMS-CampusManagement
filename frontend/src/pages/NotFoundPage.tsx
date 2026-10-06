import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <>
      <title>Page not found · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">
        Page not found
      </h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        The page you are looking for does not exist.
      </p>
      <Link
        to="/"
        className="mt-5 inline-flex min-h-11 items-center rounded-control text-sm font-medium text-stacc-primary underline underline-offset-4 hover:text-stacc-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
      >
        Back to Dashboard
      </Link>
    </>
  )
}
