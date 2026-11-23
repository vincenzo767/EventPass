import { useEffect, useRef, type ReactNode } from 'react'
import { Button } from './Button'

interface ConfirmDialogProps {
  open: boolean
  title: string
  children: ReactNode
  confirmLabel: string
  tone?: 'primary' | 'danger'
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/**
 * Confirmation for destructive actions, built on the native <dialog> element: focus is trapped,
 * Escape closes it, and the page behind it is inert.
 */
export function ConfirmDialog({ open, title, children, confirmLabel, tone = 'danger', busy, onConfirm, onCancel }: ConfirmDialogProps) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) {
      if (typeof dialog.showModal === 'function') dialog.showModal()
      else dialog.setAttribute('open', '')
    }
    if (!open && dialog.open) {
      if (typeof dialog.close === 'function') dialog.close()
      else dialog.removeAttribute('open')
    }
  }, [open])

  return (
    <dialog
      ref={ref}
      aria-labelledby="confirm-dialog-title"
      onCancel={(event) => {
        event.preventDefault()
        if (!busy) onCancel()
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-xl border border-slate-200 bg-white p-0 shadow-xl backdrop:bg-slate-900/40"
    >
      {open && (
        <div className="p-6">
          <h2 id="confirm-dialog-title" className="text-lg font-semibold text-slate-900">
            {title}
          </h2>
          <div className="mt-2 text-sm text-slate-600">{children}</div>
          <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variant="secondary" onClick={onCancel} disabled={busy}>
              Keep it
            </Button>
            <Button variant={tone} onClick={onConfirm} loading={busy}>
              {confirmLabel}
            </Button>
          </div>
        </div>
      )}
    </dialog>
  )
}
