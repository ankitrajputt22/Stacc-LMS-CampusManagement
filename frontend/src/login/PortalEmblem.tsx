/** Stacc's round emblem: an open book under the "ST" monogram. Decorative; the name is always written beside it. */
export function PortalEmblem({ className = '' }: { className?: string }) {
  return (
    <svg viewBox="0 0 120 120" aria-hidden="true" focusable="false" className={className}>
      <circle cx="60" cy="60" r="58" fill="#c9d6e8" />
      <circle cx="60" cy="60" r="51" fill="#ffffff" />
      <circle cx="60" cy="60" r="46" fill="#0b2545" />
      <path d="M33 74c9-4 18-4 27 1 9-5 18-5 27-1v8c-9-4-18-4-27 1-9-5-18-5-27-1z" fill="#8ecae6" />
      <text
        x="60"
        y="63"
        textAnchor="middle"
        fontFamily="Inter, ui-sans-serif, system-ui, sans-serif"
        fontSize="30"
        fontWeight="800"
        letterSpacing="1"
        fill="#ffffff"
      >
        ST
      </text>
    </svg>
  )
}
