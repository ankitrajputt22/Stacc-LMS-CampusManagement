import { lazy, Suspense } from 'react'
import type { TextFormat } from './textFormat'

const FormattedMarkdown = lazy(() => import('./FormattedMarkdown'))

const plainClasses = 'break-words whitespace-pre-line'

type FormattedTextProps = {
  text: string
  format: TextFormat
  className?: string
  headingLevel?: 2 | 3 | 4 | 5 | 6
}

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
