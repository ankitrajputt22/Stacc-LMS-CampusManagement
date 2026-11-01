import type { ButtonHTMLAttributes, ComponentProps, ReactNode, Ref } from 'react'
import { Link } from 'react-router'
import { type ButtonSize, type ButtonVariant, buttonClasses } from './styles'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode
  variant?: ButtonVariant
  size?: ButtonSize
  /** As wide as its container on every screen size, for forms. */
  fullWidth?: boolean
  ref?: Ref<HTMLButtonElement>
}

/** A button. Primary for the main action of a view, secondary for the rest, danger for what cannot be undone. */
export function Button({ children, className = '', type = 'button', variant = 'primary', size = 'md', fullWidth = false, ...props }: ButtonProps) {
  return (
    <button type={type} className={`${buttonClasses(variant, size, fullWidth)} ${className}`} {...props}>
      {children}
    </button>
  )
}

type ButtonLinkProps = ComponentProps<typeof Link> & {
  variant?: ButtonVariant
  size?: ButtonSize
}

/** A link to another page that looks like a button, such as "Create assignment" opening its form page. */
export function ButtonLink({ className = '', variant = 'primary', size = 'md', ...props }: ButtonLinkProps) {
  return <Link className={`${buttonClasses(variant, size)} ${className}`} {...props} />
}
