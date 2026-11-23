import { CalendarX2, ChevronRight, Ticket } from 'lucide-react'
import { Link } from 'react-router'
import { CapacityBar, DateBadge, EventMeta } from '../../components/EventMeta'
import { AvailabilityBadge, Badge } from '../../components/ui/Badge'
import { ButtonLink } from '../../components/ui/Button'
import { Alert, EmptyState, LoadingBlock } from '../../components/ui/Feedback'
import { api } from '../../lib/api'
import { spotsLabel } from '../../lib/format'
import type { PublicEvent } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

export function EventListPage() {
  useDocumentTitle('Upcoming events')
  const { data: events, error, loading, reload } = useAsync(() => api.get<PublicEvent[]>('/api/public/events'), [])

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 sm:py-12">
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-semibold text-brand-700">Free community events in Cebu</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight text-slate-900 sm:text-4xl">Upcoming events</h1>
          <p className="mt-2 max-w-xl text-slate-600">
            Register in under a minute and get a QR ticket on your phone. Show it at the door to check in.
          </p>
        </div>
        <ButtonLink to="/tickets/find" variant="secondary" icon={<Ticket className="size-4" aria-hidden />}>
          Find my ticket
        </ButtonLink>
      </div>

      {loading && !events && <LoadingBlock label="Loading events…" />}
      {error && (
        <Alert tone="error" title="Events could not be loaded" action={<button className="font-semibold underline" onClick={() => void reload()}>Try again</button>}>
          {error.message}
        </Alert>
      )}
      {events && events.length === 0 && (
        <EmptyState icon={<CalendarX2 className="size-6" />} title="No upcoming events">
          New events will appear here as soon as they are published.
        </EmptyState>
      )}

      {events && events.length > 0 && (
        <ul className="grid gap-4 md:grid-cols-2">
          {events.map((event) => (
            <li key={event.id}>
              <Link
                to={`/events/${event.id}`}
                className="group flex h-full flex-col rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition hover:border-brand-200 hover:shadow-md"
              >
                <div className="flex gap-4">
                  <DateBadge iso={event.startsAt} />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <AvailabilityBadge availability={event.availability} />
                      {event.happeningNow && <Badge tone="brand">Happening today</Badge>}
                    </div>
                    <h2 className="mt-2 text-lg font-semibold leading-snug text-slate-900 group-hover:text-brand-800">
                      {event.title}
                    </h2>
                  </div>
                </div>
                <div className="mt-4">
                  <EventMeta startsAt={event.startsAt} endsAt={event.endsAt} venue={event.venue} compact />
                </div>
                <p className="mt-3 line-clamp-2 text-sm text-slate-600">{event.description}</p>
                <div className="mt-auto pt-5">
                  <div className="mb-2 flex items-center justify-between text-sm">
                    <span className={event.spotsLeft === 0 ? 'font-semibold text-red-700' : 'font-medium text-slate-800'}>
                      {event.spotsLeft === 0 ? 'Full' : spotsLabel(event.spotsLeft, event.capacity)}
                    </span>
                    <span className="inline-flex items-center gap-0.5 font-medium text-brand-700">
                      View details <ChevronRight className="size-4" aria-hidden />
                    </span>
                  </div>
                  <CapacityBar spotsLeft={event.spotsLeft} capacity={event.capacity} />
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
