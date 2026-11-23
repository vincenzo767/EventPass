import { CalendarPlus, CalendarX2, ScanLine, Users } from 'lucide-react'
import { useEffect } from 'react'
import { useStaff } from '../../auth/AuthContext'
import { EventMeta } from '../../components/EventMeta'
import { StatTile } from '../../components/StatTile'
import { PageHeader } from '../../components/StaffLayout'
import { AvailabilityBadge, Badge, EventStatusBadge } from '../../components/ui/Badge'
import { ButtonLink } from '../../components/ui/Button'
import { Alert, EmptyState, LoadingBlock } from '../../components/ui/Feedback'
import { api } from '../../lib/api'
import type { StaffEvent } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

const REFRESH_MS = 15_000

export function DashboardPage() {
  useDocumentTitle('Dashboard')
  const staff = useStaff()
  const { data, error, loading, reload } = useAsync(() => api.get<StaffEvent[]>('/api/staff/events'), [])

  // Keep counts current while the dashboard is open (e.g. during check-in at the door).
  useEffect(() => {
    const timer = window.setInterval(() => void reload(), REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [reload])

  const upcoming = (data ?? [])
    .filter((event) => !event.ended && event.status !== 'DRAFT')
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt))

  return (
    <>
      <PageHeader
        title="Event operations"
        description={`Upcoming and ongoing events · counts refresh every ${REFRESH_MS / 1000} seconds`}
        actions={
          staff.role === 'ORGANIZER' && (
            <ButtonLink to="/staff/events/new" icon={<CalendarPlus className="size-4" aria-hidden />}>
              New event
            </ButtonLink>
          )
        }
      />

      {loading && !data && <LoadingBlock />}
      {error && <Alert tone="error">{error.message}</Alert>}
      {data && upcoming.length === 0 && (
        <EmptyState icon={<CalendarX2 className="size-6" />} title="No upcoming events">
          Published events that have not ended will appear here.
        </EmptyState>
      )}

      <div className="space-y-5">
        {upcoming.map((event) => (
          <DashboardEventCard key={event.id} event={event} canManage={staff.role === 'ORGANIZER'} />
        ))}
      </div>
    </>
  )
}

function DashboardEventCard({ event, canManage }: { event: StaffEvent; canManage: boolean }) {
  const s = event.stats
  const percent = s.registered === 0 ? 0 : Math.round((s.checkedIn / s.registered) * 100)

  return (
    <section aria-labelledby={`event-${event.id}-title`} className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <EventStatusBadge status={event.status} />
            <AvailabilityBadge availability={event.availability} />
            {event.eventDay && <Badge tone="success">Event day · check-in open</Badge>}
          </div>
          <h2 id={`event-${event.id}-title`} className="mt-2 text-lg font-semibold text-slate-900">
            {event.title}
          </h2>
          <div className="mt-2">
            <EventMeta startsAt={event.startsAt} endsAt={event.endsAt} venue={event.venue} compact />
          </div>
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <ButtonLink to={`/staff/check-in/${event.id}`} icon={<ScanLine className="size-4" aria-hidden />}>
            Check-in
          </ButtonLink>
          <ButtonLink to={`/staff/events/${event.id}`} variant="secondary" icon={<Users className="size-4" aria-hidden />}>
            Attendees{canManage ? ' & waitlist' : ''}
          </ButtonLink>
        </div>
      </div>

      <div className="mt-5">
        <div className="flex items-baseline justify-between text-sm">
          <span className="font-medium text-slate-700">Check-in progress</span>
          <span className="tabular-nums text-slate-900">
            <strong>{s.checkedIn}</strong> / {s.registered} attendees checked in
          </span>
        </div>
        <div
          className="mt-2 h-2 overflow-hidden rounded-full bg-slate-200"
          role="progressbar"
          aria-label="Attendees checked in"
          aria-valuemin={0}
          aria-valuemax={s.registered}
          aria-valuenow={s.checkedIn}
        >
          <div className="h-full rounded-full bg-emerald-500 transition-all" style={{ width: `${percent}%` }} />
        </div>
      </div>

      <dl className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6">
        <StatTile label="Capacity" value={s.capacity} hint={`${s.spotsLeft} spots left`} />
        <StatTile label="Confirmed" value={s.registered} hint="Seats taken, incl. checked in" />
        <StatTile label="Checked in" value={s.checkedIn} tone="success" />
        <StatTile
          label="No-shows"
          value={s.noShows ?? '—'}
          tone={s.noShows === undefined ? 'muted' : 'default'}
          hint={s.noShows === undefined ? 'Shown after the event starts' : 'Confirmed, not checked in'}
        />
        <StatTile label="Waitlisted" value={s.waitlisted} tone={s.waitlisted > 0 ? 'warning' : 'default'} />
        <StatTile label="Cancelled" value={s.cancelled} tone="muted" />
      </dl>
    </section>
  )
}
