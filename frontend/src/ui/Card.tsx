import { type ReactNode, useId } from 'react'
import { Link } from 'react-router'
import { textLinkClasses } from './styles'

type CardProps = {
  /** The card's heading. Without one, the card is a plain frame. */
  title?: ReactNode
  /** 2 for a section of a page, 3 for a section inside another section. */
  level?: 2 | 3 | 4
  /** A link beside the heading, such as "View all", or any small actions. */
  action?: { to: string; label: string } | ReactNode
  /** A short line under the heading. */
  description?: ReactNode
  /** `none` lets a table or list reach the card's edges. */
  padding?: 'normal' | 'none'
  className?: string
  children: ReactNode
}

function isLink(action: CardProps['action']): action is { to: string; label: string } {
  return typeof action === 'object' && action !== null && 'to' in action && 'label' in action
}

/**
 * A white bordered panel, the building block of every page. With a title it is a labelled section, so screen readers
 * can jump between sections.
 */
export function Card({ title, level = 2, action, description, padding = 'normal', className = '', children }: CardProps) {
  const id = useId()
  const Heading = `h${level}` as const
  const body = padding === 'none' ? '' : 'px-4 py-4 sm:px-5'
  return (
    <section aria-labelledby={title ? id : undefined} className={`rounded-panel border border-line bg-surface ${className}`}>
      {title && (
        <div className={`flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1 ${padding === 'none' ? 'border-b border-line px-4 py-3 sm:px-5' : 'px-4 pt-4 sm:px-5'}`}>
          <div className="min-w-0">
            <Heading id={id} className="text-base font-semibold text-ink">
              {title}
            </Heading>
            {description && <p className="mt-0.5 text-sm leading-6 text-ink-muted">{description}</p>}
          </div>
          {action && (isLink(action) ? <Link to={action.to} className={`text-sm ${textLinkClasses}`}>{action.label}</Link> : action)}
        </div>
      )}
      <div className={title && padding === 'normal' ? 'px-4 pt-3 pb-4 sm:px-5' : body}>{children}</div>
    </section>
  )
}
