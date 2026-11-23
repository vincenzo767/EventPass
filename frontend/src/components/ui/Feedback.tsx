import { AlertCircle, CheckCircle2, Info, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'

export function Spinner({ className = 'size-5', label }: { className?: string; label?: string }) {
  return (
    <span role={label ? 'status' : undefined} className="inline-flex items-center gap-2">
      <svg className={`${className} animate-spin text-current`} viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" className="opacity-25" />
        <path d="M22 12a10 10 0 0 0-10-10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
      </svg>
      {label && <span className="text-sm text-slate-600">{label}</span>}
    </span>
  )
}

export function LoadingBlock({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex justify-center py-16 text-brand-700">
      <Spinner className="size-6" label={label} />
    </div>
  )
}

type AlertTone = 'info' | 'success' | 'warning' | 'error'

const alertStyles: Record<AlertTone, { box: string; icon: ReactNode }> = {
  info: { box: 'bg-brand-50 border-brand-200 text-brand-900', icon: <Info className="size-5 shrink-0" aria-hidden /> },
  success: {
    box: 'bg-emerald-50 border-emerald-200 text-emerald-900',
    icon: <CheckCircle2 className="size-5 shrink-0" aria-hidden />,
  },
  warning: {
    box: 'bg-amber-50 border-amber-200 text-amber-900',
    icon: <TriangleAlert className="size-5 shrink-0" aria-hidden />,
  },
  error: { box: 'bg-red-50 border-red-200 text-red-900', icon: <AlertCircle className="size-5 shrink-0" aria-hidden /> },
}

export function Alert({
  tone = 'info',
  title,
  children,
  action,
}: {
  tone?: AlertTone
  title?: string
  children?: ReactNode
  action?: ReactNode
}) {
  const style = alertStyles[tone]
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`flex gap-3 rounded-lg border px-4 py-3 text-sm ${style.box}`}
    >
      {style.icon}
      <div className="min-w-0 flex-1">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className={title ? 'mt-0.5' : ''}>{children}</div>}
        {action && <div className="mt-2">{action}</div>}
      </div>
    </div>
  )
}

export function EmptyState({ icon, title, children }: { icon: ReactNode; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center rounded-xl border border-dashed border-slate-300 bg-white px-6 py-12 text-center">
      <div className="mb-3 rounded-full bg-slate-100 p-3 text-slate-500">{icon}</div>
      <p className="font-semibold text-slate-900">{title}</p>
      {children && <div className="mt-1 max-w-sm text-sm text-slate-600">{children}</div>}
    </div>
  )
}
