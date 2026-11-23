import { CalendarPlus, CalendarX2, Pencil, ScanLine, Users } from 'lucide-react'
import { Link } from 'react-router'
import { useStaff } from '../../auth/AuthContext'
import { PageHeader } from '../../components/StaffLayout'
import { AvailabilityBadge, EventStatusBadge } from '../../components/ui/Badge'
import { ButtonLink } from '../../components/ui/Button'
import { Alert, EmptyState, LoadingBlock } from '../../components/ui/Feedback'
import { api } from '../../lib/api'
import { formatDateShort, formatTimeRange } from '../../lib/format'
import type { StaffEvent } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

export function EventsPage() {
  useDocumentTitle('Events')
  const staff = useStaff()
  const canManage = staff.role === 'ORGANIZER'
  const { data: events, error, loading } = useAsync(() => api.get<StaffEvent[]>('/api/staff/events'), [])

  return (
    <>
      <PageHeader
        title="Events"
        description={canManage ? 'Create, edit, publish and close events.' : 'Events you can check attendees in for.'}
        actions={
          canManage && (
            <ButtonLink to="/staff/events/new" icon={<CalendarPlus className="size-4" aria-hidden />}>
              New event
            </ButtonLink>
          )
        }
      />
      {loading && !events && <LoadingBlock />}
      {error && <Alert tone="error">{error.message}</Alert>}
      {events && events.length === 0 && (
        <EmptyState icon={<CalendarX2 className="size-6" />} title="No events yet">
          {canManage ? 'Create your first event to start taking registrations.' : 'No events are available.'}
        </EmptyState>
      )}

      {events && events.length > 0 && (
        <ul className="divide-y divide-slate-200 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          {events.map((event) => (
            <li key={event.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <Link to={`/staff/events/${event.id}`} className="font-semibold text-slate-900 hover:text-brand-800">
                    {event.title}
                  </Link>
                  <EventStatusBadge status={event.status} />
                  {event.status !== 'DRAFT' && <AvailabilityBadge availability={event.availability} />}
                </div>
                <p className="mt-1 text-sm text-slate-600">
                  {formatDateShort(event.startsAt)} · {formatTimeRange(event.startsAt, event.endsAt)} · {event.venue}
                </p>
                <p className="mt-1 text-sm tabular-nums text-slate-500">
                  {event.stats.registered} / {event.capacity} registered · {event.stats.checkedIn} checked in
                  {event.stats.waitlisted > 0 && ` · ${event.stats.waitlisted} waitlisted`}
                </p>
              </div>
              <div className="flex shrink-0 flex-wrap gap-2">
                <ButtonLink to={`/staff/events/${event.id}`} variant="secondary" size="sm" icon={<Users className="size-4" aria-hidden />}>
                  Attendees
                </ButtonLink>
                {!event.ended && event.status !== 'DRAFT' && (
                  <ButtonLink to={`/staff/check-in/${event.id}`} variant="secondary" size="sm" icon={<ScanLine className="size-4" aria-hidden />}>
                    Check-in
                  </ButtonLink>
                )}
                {canManage && (
                  <ButtonLink to={`/staff/events/${event.id}/edit`} variant="secondary" size="sm" icon={<Pencil className="size-4" aria-hidden />}>
                    Edit
                  </ButtonLink>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
