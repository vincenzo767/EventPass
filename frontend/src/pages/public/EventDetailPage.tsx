import { ArrowLeft, ListPlus, UserPlus } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { CapacityBar, EventMeta } from '../../components/EventMeta'
import { AvailabilityBadge, Badge } from '../../components/ui/Badge'
import { Button, ButtonLink } from '../../components/ui/Button'
import { Alert, LoadingBlock } from '../../components/ui/Feedback'
import { api } from '../../lib/api'
import { spotsLabel } from '../../lib/format'
import type { PublicEvent } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

export function EventDetailPage() {
  const { eventId } = useParams()
  const { data: event, error, loading } = useAsync(() => api.get<PublicEvent>(`/api/public/events/${eventId}`), [eventId])
  useDocumentTitle(event?.title)

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6 sm:py-10">
      <Link to="/" className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900">
        <ArrowLeft className="size-4" aria-hidden /> All events
      </Link>

      {loading && !event && <LoadingBlock />}
      {error && (
        <div className="mt-6">
          <Alert tone="error" title={error.status === 404 ? 'Event not found' : 'Event could not be loaded'}>
            {error.status === 404 ? 'This event does not exist or is not open to the public.' : error.message}
          </Alert>
        </div>
      )}

      {event && (
        <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_340px] lg:gap-10">
          <article>
            <div className="flex flex-wrap gap-2">
              <AvailabilityBadge availability={event.availability} />
              {event.happeningNow && <Badge tone="brand">Happening today</Badge>}
            </div>
            <h1 className="mt-3 text-3xl font-semibold tracking-tight text-slate-900">{event.title}</h1>
            <div className="mt-5">
              <EventMeta startsAt={event.startsAt} endsAt={event.endsAt} venue={event.venue} />
            </div>
            <h2 className="mt-8 text-sm font-semibold uppercase tracking-wide text-slate-500">About this event</h2>
            <p className="mt-2 whitespace-pre-line leading-relaxed text-slate-700">{event.description}</p>
          </article>

          <aside aria-label="Registration" className="lg:pt-2">
            <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm lg:sticky lg:top-24">
              <p className="text-sm text-slate-500">Free registration</p>
              <p className={`mt-1 text-xl font-semibold ${event.spotsLeft === 0 ? 'text-red-700' : 'text-slate-900'}`}>
                {availabilityHeadline(event)}
              </p>
              <div className="mt-3">
                <CapacityBar spotsLeft={event.spotsLeft} capacity={event.capacity} />
              </div>
              <p className="mt-2 text-xs text-slate-500">Capacity: {event.capacity} attendees</p>

              <div className="mt-5 space-y-3">
                {event.registrationOpen ? (
                  <ButtonLink to={`/events/${event.id}/register`} size="lg" className="w-full" icon={<UserPlus className="size-5" aria-hidden />}>
                    Register
                  </ButtonLink>
                ) : (
                  <Button size="lg" className="w-full" disabled icon={<UserPlus className="size-5" aria-hidden />}>
                    Register
                  </Button>
                )}

                {event.waitlistOpen && (
                  <>
                    <ButtonLink
                      to={`/events/${event.id}/register?waitlist=1`}
                      variant="secondary"
                      size="lg"
                      className="w-full"
                      icon={<ListPlus className="size-5" aria-hidden />}
                    >
                      Join the waitlist
                    </ButtonLink>
                    <p className="text-xs text-slate-500">
                      This event is full. Waitlisted registrations are not confirmed; you get a ticket only if a spot
                      opens and an organizer moves you up.
                    </p>
                  </>
                )}
                {event.availability === 'CLOSED' && <Alert tone="info">Registration for this event is closed.</Alert>}
                {event.availability === 'ENDED' && <Alert tone="info">This event has ended.</Alert>}
              </div>
            </div>
          </aside>
        </div>
      )}
    </div>
  )
}

function availabilityHeadline(event: PublicEvent): string {
  switch (event.availability) {
    case 'FULL':
      return 'Full'
    case 'CLOSED':
      return 'Registration closed'
    case 'ENDED':
      return 'Ended'
    default:
      return spotsLabel(event.spotsLeft, event.capacity)
  }
}
