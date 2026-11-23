import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Link, type LinkProps } from 'react-router'
import { Spinner } from './Feedback'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'sm' | 'md' | 'lg'

const base =
  'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors select-none ' +
  'disabled:cursor-not-allowed disabled:opacity-55 whitespace-nowrap'

const variants: Record<Variant, string> = {
  primary: 'bg-brand-700 text-white shadow-sm hover:bg-brand-800 disabled:hover:bg-brand-700',
  secondary:
    'bg-white text-slate-800 border border-slate-300 shadow-sm hover:bg-slate-50 disabled:hover:bg-white',
  ghost: 'text-slate-700 hover:bg-slate-100',
  danger: 'bg-red-600 text-white shadow-sm hover:bg-red-700 disabled:hover:bg-red-600',
}

const sizes: Record<Size, string> = {
  sm: 'h-8 px-3 text-sm',
  md: 'h-10 px-4 text-sm',
  lg: 'h-12 px-5 text-base',
}

export function buttonClass(variant: Variant = 'primary', size: Size = 'md', extra = ''): string {
  return `${base} ${variants[variant]} ${sizes[size]} ${extra}`
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
  icon?: ReactNode
}

export function Button({ variant = 'primary', size = 'md', loading, icon, children, className = '', disabled, ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      className={buttonClass(variant, size, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <Spinner className="size-4" /> : icon}
      {children}
    </button>
  )
}

interface ButtonLinkProps extends LinkProps {
  variant?: Variant
  size?: Size
  icon?: ReactNode
}

export function ButtonLink({ variant = 'primary', size = 'md', icon, children, className = '', ...rest }: ButtonLinkProps) {
  return (
    <Link className={buttonClass(variant, size, className)} {...rest}>
      {icon}
      {children}
    </Link>
  )
}
