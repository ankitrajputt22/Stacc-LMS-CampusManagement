const SHELVES = [
  [14, 30, 22, 36, 18, 26, 34, 20, 28, 16, 32, 24],
  [26, 18, 34, 22, 30, 14, 24, 36, 20, 28, 18, 30],
  [20, 32, 16, 28, 24, 34, 18, 26, 30, 22, 36, 16],
]
const COLORS = ['#5b7fb8', '#8ecae6', '#2f4f86', '#b8e2f2', '#46699f', '#6f93c4']

/**
 * A drawn, decorative study-room scene: shelves of books crossed by thin circuit lines, for learning plus technology.
 * It is drawn in place, so no photo is loaded, and it is hidden from screen readers.
 */
export function LibraryIllustration({ className = '' }: { className?: string }) {
  return (
    <svg viewBox="0 0 480 300" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false" className={className}>
      <rect width="480" height="300" fill="#1f3a68" />
      {SHELVES.map((heights, row) => {
        const base = 92 + row * 88
        let x = 24
        return (
          <g key={row}>
            {heights.map((height, index) => {
              const width = 18 + ((index * 7 + row * 5) % 14)
              const book = (
                <rect
                  key={index}
                  x={x}
                  y={base - height - 20}
                  width={width}
                  height={height + 20}
                  rx="2"
                  fill={COLORS[(index + row * 2) % COLORS.length]}
                  opacity="0.85"
                />
              )
              x += width + 6
              return book
            })}
            <rect x="12" y={base} width="456" height="8" rx="2" fill="#0b2545" />
          </g>
        )
      })}
      <g fill="none" stroke="#e0f2fe" strokeWidth="2" opacity="0.55">
        <path d="M40 300 L120 220 L120 140 L200 60 L200 0" />
        <path d="M300 300 L300 230 L380 150 L480 150" />
        <path d="M160 300 L160 250 L240 170 L320 170" />
      </g>
      <g fill="#0b2545" stroke="#e0f2fe" strokeWidth="2" opacity="0.8">
        <circle cx="120" cy="140" r="6" />
        <circle cx="200" cy="60" r="6" />
        <circle cx="380" cy="150" r="6" />
        <circle cx="240" cy="170" r="6" />
      </g>
    </svg>
  )
}
