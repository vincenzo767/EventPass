import { CheckCircle2, Clock3, Download, XCircle } from 'lucide-react'
import { formatDateLong, formatDateTime, formatTimeRange } from '../lib/format'
import type { Ticket } from '../lib/types'
import { LogoMark } from './Logo'
import { RegistrationStatusBadge } from './ui/Badge'

/** The attendee's digital ticket, designed to be shown on a phone at the entrance. */
export function TicketCard({ ticket }: { ticket: Ticket }) {
  const hasQr = ticket.status === 'CONFIRMED' || ticket.status === 'CHECKED_IN'
  const qrUrl = `/api/public/tickets/${encodeURIComponent(ticket.accessToken)}/qr.png`

  return (
    <article aria-label="Your ticket" className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-lg">
      <header className="bg-brand-800 px-5 py-4 text-white">
        <div className="flex items-center justify-between gap-3">
          <span className="flex items-center gap-2 text-sm font-medium text-brand-100">
            <LogoMark className="size-6" />
            EventPass {hasQr ? 'ticket' : ticket.status === 'WAITLISTED' ? 'waitlist' : 'registration'}
          </span>
          <span className="rounded-full bg-white px-0.5 py-0.5">
            <RegistrationStatusBadge status={ticket.status} />
          </span>
        </div>
        <h2 className="mt-2 text-xl font-semibold leading-snug">{ticket.event.title}</h2>
      </header>

      <div className="px-5 py-4">
        <dl className="grid grid-cols-1 gap-x-4 gap-y-3 text-sm sm:grid-cols-2">
          <Item label="Attendee" value={ticket.attendeeName} />
          <Item label="Date" value={formatDateLong(ticket.event.startsAt)} />
          <Item label="Time (Cebu)" value={formatTimeRange(ticket.event.startsAt, ticket.event.endsAt)} />
          <Item label="Venue" value={ticket.event.venue} />
        </dl>
      </div>

      {/* Perforation */}
      <div className="relative flex items-center" aria-hidden>
        <span className="absolute -left-3 size-6 rounded-full border border-slate-200 bg-slate-50" />
        <span className="mx-5 w-full border-t-2 border-dashed border-slate-200" />
        <span className="absolute -right-3 size-6 rounded-full border border-slate-200 bg-slate-50" />
      </div>

      <div className="flex flex-col items-center px-5 pb-6 pt-5 text-center">
        {hasQr && (
          <>
            <img
              src={qrUrl}
              alt={`QR code for ticket ${ticket.ticketCode}`}
              width={240}
              height={240}
              className="size-60 rounded-lg border border-slate-200 bg-white p-1 [image-rendering:pixelated]"
            />
            <p className="mt-4 text-xs font-medium uppercase tracking-wider text-slate-500">Ticket code</p>
            <p className="mt-1 font-mono text-2xl font-semibold tracking-wider text-slate-900" data-testid="ticket-code">
              {ticket.ticketCode}
            </p>
            {ticket.status === 'CHECKED_IN' ? (
              <p className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-emerald-700">
                <CheckCircle2 className="size-4" aria-hidden /> Checked in {formatDateTime(ticket.checkedInAt)}
              </p>
            ) : (
              <p className="mt-3 text-sm text-slate-600">Show this QR code at the entrance.</p>
            )}
            <a
              href={qrUrl}
              download={`${ticket.ticketCode}.png`}
              className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-brand-700 hover:text-brand-900"
            >
              <Download className="size-4" aria-hidden /> Save QR image
            </a>
          </>
        )}

        {ticket.status === 'WAITLISTED' && (
          <div className="w-full rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-900">
            <Clock3 className="mx-auto size-7" aria-hidden />
            <p className="mt-2 text-lg font-semibold">You are #{ticket.waitlistPosition} on the waitlist</p>
            <p className="mt-1 text-sm">
              Your registration is <strong>not confirmed</strong> and this is not a valid entry ticket. If a spot opens and
              an organizer moves you up, this page will show your QR ticket.
            </p>
            <p className="mt-3 text-xs font-medium uppercase tracking-wider text-amber-800">Waitlist reference</p>
            <p className="font-mono text-lg font-semibold tracking-wider" data-testid="ticket-code">
              {ticket.ticketCode}
            </p>
          </div>
        )}

        {ticket.status === 'CANCELLED' && (
          <div className="w-full rounded-xl border border-slate-200 bg-slate-50 p-4 text-slate-700">
            <XCircle className="mx-auto size-7 text-slate-500" aria-hidden />
            <p className="mt-2 text-lg font-semibold">Registration cancelled</p>
            <p className="mt-1 text-sm">
              Cancelled {formatDateTime(ticket.cancelledAt)}. This ticket can no longer be used.
            </p>
            <p className="mt-2 font-mono text-sm text-slate-500" data-testid="ticket-code">
              {ticket.ticketCode}
            </p>
          </div>
        )}
      </div>
    </article>
  )
}

function Item({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wider text-slate-500">{label}</dt>
      <dd className="mt-0.5 font-medium text-slate-900">{value}</dd>
    </div>
  )
}
