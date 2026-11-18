import { type ReactNode, useId } from 'react'
import { Link } from 'react-router'
import { NavIcon, type NavIconName } from '../components/navigation/navIcons'
import { textLinkClasses } from './styles'

type CardProps = {
  title?: ReactNode
  icon?: NavIconName
  level?: 2 | 3 | 4
  action?: { to: string; label: string } | ReactNode
  description?: ReactNode
  padding?: 'normal' | 'none'
  className?: string
  children: ReactNode
}

function isLink(action: CardProps['action']): action is { to: string; label: string } {
  return typeof action === 'object' && action !== null && 'to' in action && 'label' in action
}

export function Card({ title, icon, level = 2, action, description, padding = 'normal', className = '', children }: CardProps) {
  const id = useId()
  const Heading = `h${level}` as const
  const body = padding === 'none' ? '' : 'px-4 py-4 sm:px-5'
  return (
    <section aria-labelledby={title ? id : undefined} className={`rounded-panel border border-line bg-surface shadow-[0_1px_2px_rgba(15,23,42,0.05)] ${className}`}>
      {title && (
        <div className="relative mx-4 flex flex-wrap items-center justify-between gap-x-3 gap-y-1 border-b border-line pt-4 pb-3 sm:mx-5">
          <span aria-hidden="true" className="absolute -bottom-px left-0 h-0.5 w-12 bg-portal-cta" />
          <div className="min-w-0">
            <Heading id={id} className="flex items-center gap-2.5 text-lg font-semibold text-ink">
              {icon && <NavIcon name={icon} className="size-6 text-portal-navy" />}
              {title}
            </Heading>
            {description && <p className="mt-0.5 text-sm leading-6 text-ink-muted">{description}</p>}
          </div>
          {action && (isLink(action) ? <Link to={action.to} className={`text-sm ${textLinkClasses}`}>{action.label}</Link> : action)}
        </div>
      )}
      <div className={title && padding === 'normal' ? 'px-4 pt-4 pb-5 sm:px-5' : body}>{children}</div>
    </section>
  )
}
