import type { ReactNode } from 'react'

interface StatTileProps {
  label: string
  value: ReactNode
  hint?: ReactNode
  tone?: 'default' | 'success' | 'warning' | 'muted'
}

const valueTone = {
  default: 'text-slate-900',
  success: 'text-emerald-700',
  warning: 'text-amber-700',
  muted: 'text-slate-500',
}

export function StatTile({ label, value, hint, tone = 'default' }: StatTileProps) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3.5 py-3">
      <dt className="text-xs font-medium text-slate-500">{label}</dt>
      <dd className={`mt-1 text-2xl font-semibold tabular-nums ${valueTone[tone]}`}>{value}</dd>
      {hint && <dd className="mt-0.5 text-xs text-slate-500">{hint}</dd>}
    </div>
  )
}
