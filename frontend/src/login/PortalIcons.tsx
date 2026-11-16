/** Small icons of the sign-in page. All decorative: each sits on a control whose name says the same in words. */

export function EyeIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false" className="size-6 shrink-0">
      <path d="M2 12s3.6-6.5 10-6.5S22 12 22 12s-3.6 6.5-10 6.5S2 12 2 12z" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
      <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  )
}

export function EyeOffIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false" className="size-6 shrink-0">
      <path d="M2 12s3.6-6.5 10-6.5S22 12 22 12s-3.6 6.5-10 6.5S2 12 2 12z" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
      <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M4 4l16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

/** Layered hills behind the sign-in form, in flat portal colours. Purely decorative. */
export function PortalLandscape({ className = '' }: { className?: string }) {
  return (
    <svg viewBox="0 0 1440 900" preserveAspectRatio="xMidYMax slice" aria-hidden="true" focusable="false" className={className}>
      <path d="M0 560 170 430l120 70 190-210 150 150 110-90 210 230 140-120 170 140 180-170v480H0z" fill="#3d4f7c" fillOpacity="0.45" />
      <path d="M0 660 220 520l170 110 160-90 200 160 170-120 190 140 150-80 180 110v170H0z" fill="#1d3461" fillOpacity="0.75" />
      <path d="M0 760 260 650l210 80 230-70 240 110 220-60 280 90v100H0z" fill="#0b2545" />
    </svg>
  )
}
