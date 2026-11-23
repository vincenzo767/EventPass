import { CalendarDays, Clock, MapPin } from 'lucide-react'
import { dateBadge, formatDateLong, formatTimeRange } from '../lib/format'

interface EventMetaProps {
  startsAt: string
  endsAt: string
  venue: string
  compact?: boolean
}

/** Date, Cebu-time range and venue with icons. */
export function EventMeta({ startsAt, endsAt, venue, compact }: EventMetaProps) {
  const row = `flex items-start gap-2 ${compact ? 'text-sm' : 'text-[0.95rem]'} text-slate-700`
  return (
    <dl className={compact ? 'space-y-1.5' : 'space-y-2.5'}>
      <div className={row}>
        <dt className="sr-only">Date</dt>
        <CalendarDays className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden />
        <dd>{formatDateLong(startsAt)}</dd>
      </div>
      <div className={row}>
        <dt className="sr-only">Time (Cebu time)</dt>
        <Clock className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden />
        <dd>
          {formatTimeRange(startsAt, endsAt)} <span className="text-slate-500">(Cebu time)</span>
        </dd>
      </div>
      <div className={row}>
        <dt className="sr-only">Venue</dt>
        <MapPin className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden />
        <dd>{venue}</dd>
      </div>
    </dl>
  )
}

export function DateBadge({ iso }: { iso: string }) {
  const { month, day } = dateBadge(iso)
  return (
    <div className="flex w-14 shrink-0 flex-col items-center overflow-hidden rounded-lg border border-slate-200 bg-white text-center shadow-sm" aria-hidden>
      <span className="w-full bg-brand-700 py-0.5 text-[0.65rem] font-semibold tracking-wider text-white">{month}</span>
      <span className="py-1 text-xl font-semibold text-slate-900">{day}</span>
    </div>
  )
}

/** Seats bar: how full the event is. */
export function CapacityBar({ spotsLeft, capacity }: { spotsLeft: number; capacity: number }) {
  const taken = capacity - spotsLeft
  const percent = capacity === 0 ? 100 : Math.round((taken / capacity) * 100)
  const color = spotsLeft === 0 ? 'bg-red-500' : spotsLeft <= Math.max(2, capacity * 0.15) ? 'bg-amber-500' : 'bg-brand-600'
  return (
    <div
      className="h-1.5 w-full overflow-hidden rounded-full bg-slate-200"
      role="progressbar"
      aria-label="Seats taken"
      aria-valuemin={0}
      aria-valuemax={capacity}
      aria-valuenow={taken}
    >
      <div className={`h-full rounded-full ${color}`} style={{ width: `${percent}%` }} />
    </div>
  )
}
