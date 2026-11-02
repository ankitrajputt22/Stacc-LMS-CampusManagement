import { lazy, Suspense } from 'react'
import type { TextFormat } from './textFormat'

// The formatting renderer and its maths fonts load only when a formatted text is on screen.
const FormattedMarkdown = lazy(() => import('./FormattedMarkdown'))

const plainClasses = 'break-words whitespace-pre-line'

type FormattedTextProps = {
  text: string
  format: TextFormat
  /** Classes for the text block, such as "mt-1 text-sm leading-6 text-ink". */
  className?: string
  /** The level formatted headings start at, so they fit under the page's own headings. */
  headingLevel?: 2 | 3 | 4 | 5 | 6
}

/**
 * A course text shown as its format says: plain text exactly as typed with its line breaks, or formatted text with
 * Stacc's small formatting set. Neither ever interprets HTML. While the formatter loads, the text shows as written.
 */
export function FormattedText({ text, format, className = '', headingLevel = 3 }: FormattedTextProps) {
  if (format !== 'FORMATTED') {
    return <p className={`${plainClasses} ${className}`}>{text}</p>
  }
  return (
    <div className={`break-words ${className}`}>
      <Suspense fallback={<p className={plainClasses}>{text}</p>}>
        <FormattedMarkdown text={text} headingLevel={headingLevel} />
      </Suspense>
    </div>
  )
}
