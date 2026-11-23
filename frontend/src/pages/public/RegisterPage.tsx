import { ArrowLeft } from 'lucide-react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { EventMeta } from '../../components/EventMeta'
import { RegistrationForm } from '../../components/RegistrationForm'
import { ButtonLink } from '../../components/ui/Button'
import { Alert, LoadingBlock } from '../../components/ui/Feedback'
import { api } from '../../lib/api'
import { spotsLabel } from '../../lib/format'
import type { PublicEvent, Ticket } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

export function RegisterPage() {
  const { eventId } = useParams()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { data: event, error, loading } = useAsync(() => api.get<PublicEvent>(`/api/public/events/${eventId}`), [eventId])
  useDocumentTitle(event ? `Register · ${event.title}` : 'Register')

  function handleRegistered(ticket: Ticket) {
    navigate(`/tickets/${ticket.accessToken}`, { state: { justRegistered: true, emailQueued: ticket.emailQueued } })
  }

  const canRegister = event && (event.registrationOpen || event.waitlistOpen)
  const waitlist = Boolean(event && (event.availability === 'FULL' || params.get('waitlist') === '1'))

  return (
    <div className="mx-auto max-w-xl px-4 py-6 sm:py-10">
      <Link to={`/events/${eventId}`} className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900">
        <ArrowLeft className="size-4" aria-hidden /> Event details
      </Link>

      {loading && !event && <LoadingBlock />}
      {error && (
        <div className="mt-6">
          <Alert tone="error" title="Event not available">
            {error.status === 404 ? 'This event does not exist or is not open to the public.' : error.message}
          </Alert>
        </div>
      )}

      {event && (
        <div className="mt-5 rounded-xl border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-200 p-5">
            <h1 className="text-xl font-semibold text-slate-900">
              {waitlist ? 'Join the waitlist' : 'Register'} <span className="sr-only">for</span>
            </h1>
            <p className="mt-0.5 font-medium text-brand-800">{event.title}</p>
            <div className="mt-3">
              <EventMeta startsAt={event.startsAt} endsAt={event.endsAt} venue={event.venue} compact />
            </div>
            {event.registrationOpen && (
              <p className="mt-3 text-sm font-medium text-slate-700">{spotsLabel(event.spotsLeft, event.capacity)}</p>
            )}
          </div>
          <div className="p-5">
            {canRegister ? (
              <RegistrationForm eventId={event.id} waitlist={waitlist} onRegistered={handleRegistered} />
            ) : (
              <div className="space-y-4">
                <Alert tone="info" title={event.availability === 'ENDED' ? 'This event has ended' : 'Registration is closed'}>
                  This event is not accepting registrations.
                </Alert>
                <ButtonLink to="/" variant="secondary">
                  Browse other events
                </ButtonLink>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
