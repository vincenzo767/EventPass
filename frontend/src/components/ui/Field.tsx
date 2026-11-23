import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'

const controlBase =
  'block w-full rounded-lg border bg-white px-3 text-slate-900 shadow-sm placeholder:text-slate-400 ' +
  'focus:outline-none focus:ring-2 disabled:bg-slate-100 disabled:text-slate-500'

function controlClass(invalid: boolean, extra = ''): string {
  return `${controlBase} ${
    invalid
      ? 'border-red-400 focus:border-red-500 focus:ring-red-500/25'
      : 'border-slate-300 focus:border-brand-600 focus:ring-brand-600/20'
  } ${extra}`
}

interface FieldShellProps {
  id: string
  label: string
  error?: string
  hint?: ReactNode
  required?: boolean
  children: ReactNode
}

/** Label, control, hint and an error message placed directly beneath the control. */
function FieldShell({ id, label, error, hint, required, children }: FieldShellProps) {
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-slate-800">
        {label}
        {required && (
          <span className="text-red-600" aria-hidden="true">
            {' '}*
          </span>
        )}
      </label>
      {children}
      {hint && !error && (
        <p id={`${id}-hint`} className="mt-1.5 text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="mt-1.5 text-sm font-medium text-red-700" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

function describedBy(id: string, error?: string, hint?: ReactNode): string | undefined {
  if (error) return `${id}-error`
  if (hint) return `${id}-hint`
  return undefined
}

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: ReactNode
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, error, hint, required, className = '', id: givenId, ...rest },
  ref,
) {
  const generated = useId()
  const id = givenId ?? generated
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} required={required}>
      <input
        ref={ref}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-required={required || undefined}
        aria-describedby={describedBy(id, error, hint)}
        className={controlClass(Boolean(error), `h-11 ${className}`)}
        {...rest}
      />
    </FieldShell>
  )
})

interface TextAreaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string
  error?: string
  hint?: ReactNode
}

export const TextAreaField = forwardRef<HTMLTextAreaElement, TextAreaFieldProps>(function TextAreaField(
  { label, error, hint, required, className = '', ...rest },
  ref,
) {
  const id = useId()
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} required={required}>
      <textarea
        ref={ref}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-required={required || undefined}
        aria-describedby={describedBy(id, error, hint)}
        className={controlClass(Boolean(error), `py-2.5 ${className}`)}
        {...rest}
      />
    </FieldShell>
  )
})

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  error?: string
  hint?: ReactNode
}

export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(function SelectField(
  { label, error, hint, required, className = '', children, ...rest },
  ref,
) {
  const id = useId()
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} required={required}>
      <select
        ref={ref}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
        className={controlClass(Boolean(error), `h-11 ${className}`)}
        {...rest}
      >
        {children}
      </select>
    </FieldShell>
  )
})
