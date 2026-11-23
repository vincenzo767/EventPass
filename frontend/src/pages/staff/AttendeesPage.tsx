import { ArrowUpCircle, Download, Pencil, ScanLine, Search, UserX, Undo2, Users } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useLocation, useParams } from 'react-router'
import { useStaff } from '../../auth/AuthContext'
import { EventMeta } from '../../components/EventMeta'
import { StatTile } from '../../components/StatTile'
import { PageHeader } from '../../components/StaffLayout'
import { AvailabilityBadge, EventStatusBadge, RegistrationStatusBadge } from '../../components/ui/Badge'
import { Button, ButtonLink } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { Alert, EmptyState, LoadingBlock } from '../../components/ui/Feedback'
import { api, ApiError } from '../../lib/api'
import { formatDateTime, formatDateTimeCompact, formatMobile } from '../../lib/format'
import type { Attendee, AttendeeList, EventStatus, RegistrationStatus, StaffEvent } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

type PendingAction =
  | { kind: 'cancel'; attendee: Attendee }
  | { kind: 'undo'; attendee: Attendee }
  | { kind: 'promote' }
  | { kind: 'status'; status: EventStatus }

type StatusFilter = 'ALL' | RegistrationStatus

const filters: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'CHECKED_IN', label: 'Checked in' },
  { value: 'WAITLISTED', label: 'Waitlisted' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

export function AttendeesPage() {
  const { eventId } = useParams()
  const staff = useStaff()
  const canManage = staff.role === 'ORGANIZER'
  const location = useLocation()
  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')
  const [filter, setFilter] = useState<StatusFilter>('ALL')
  const [pending, setPending] = useState<PendingAction>()
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string } | undefined>(() => {
    const saved = (location.state as { saved?: string } | null)?.saved
    return saved ? { tone: 'success', text: `Event ${saved}.` } : undefined
  })

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(query.trim()), 250)
    return () => window.clearTimeout(timer)
  }, [query])

  const event = useAsync(() => api.get<StaffEvent>(`/api/staff/events/${eventId}`), [eventId])
  const list = useAsync(
    () => api.get<AttendeeList>(`/api/staff/events/${eventId}/registrations${debounced ? `?q=${encodeURIComponent(debounced)}` : ''}`),
    [eventId, debounced],
  )
  useDocumentTitle(event.data ? `Attendees · ${event.data.title}` : 'Attendees')

  const totals = list.data?.totals ?? event.data?.stats
  const rows = useMemo(
    () => (list.data?.attendees ?? []).filter((a) => filter === 'ALL' || a.status === filter),
    [list.data, filter],
  )

  async function runPending() {
    if (!pending) return
    setBusy(true)
    try {
      if (pending.kind === 'cancel') {
        await api.post(`/api/staff/registrations/${pending.attendee.id}/cancel`)
        setNotice({ tone: 'success', text: `Registration for ${pending.attendee.fullName} was cancelled. One spot was freed.` })
      } else if (pending.kind === 'undo') {
        await api.post(`/api/staff/registrations/${pending.attendee.id}/undo-check-in`)
        setNotice({ tone: 'success', text: `Check-in for ${pending.attendee.fullName} was undone. The ticket can be checked in again.` })
      } else if (pending.kind === 'promote') {
        const result = await api.post<{ promoted: Attendee }>(`/api/staff/events/${eventId}/waitlist/promote`)
        setNotice({ tone: 'success', text: `${result.promoted.fullName} was promoted from the waitlist and now has a confirmed ticket.` })
      } else {
        await api.post(`/api/staff/events/${eventId}/status`, { status: pending.status })
        setNotice({ tone: 'success', text: pending.status === 'CLOSED' ? 'Event closed to new registrations.' : 'Event published.' })
      }
      await Promise.all([list.reload(), event.reload()])
    } catch (e) {
      setNotice({ tone: 'error', text: e instanceof ApiError ? e.message : 'The action could not be completed.' })
    } finally {
      setBusy(false)
      setPending(undefined)
    }
  }

  if (event.loading && !event.data) return <LoadingBlock />
  if (event.error) return <Alert tone="error">{event.error.status === 404 ? 'Event not found.' : event.error.message}</Alert>
  const ev = event.data!
  const canPromote = canManage && totals !== undefined && totals.waitlisted > 0 && totals.spotsLeft > 0

  return (
    <>
      <PageHeader
        title={ev.title}
        description={
          <div className="mt-2 space-y-2">
            <div className="flex flex-wrap gap-2">
              <EventStatusBadge status={ev.status} />
              {ev.status !== 'DRAFT' && <AvailabilityBadge availability={ev.availability} />}
            </div>
            <EventMeta startsAt={ev.startsAt} endsAt={ev.endsAt} venue={ev.venue} compact />
          </div>
        }
        actions={
          <>
            {!ev.ended && ev.status !== 'DRAFT' && (
              <ButtonLink to={`/staff/check-in/${ev.id}`} icon={<ScanLine className="size-4" aria-hidden />}>
                Check-in
              </ButtonLink>
            )}
            {canManage && (
              <>
                <a
                  href={`/api/staff/events/${ev.id}/registrations/export`}
                  download
                  className="inline-flex h-10 items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-800 shadow-sm hover:bg-slate-50"
                >
                  <Download className="size-4" aria-hidden /> Export CSV
                </a>
                <ButtonLink to={`/staff/events/${ev.id}/edit`} variant="secondary" icon={<Pencil className="size-4" aria-hidden />}>
                  Edit
                </ButtonLink>
                {ev.status === 'PUBLISHED' && (
                  <Button variant="secondary" onClick={() => setPending({ kind: 'status', status: 'CLOSED' })}>
                    Close registration
                  </Button>
                )}
                {ev.status !== 'PUBLISHED' && !ev.ended && (
                  <Button variant="secondary" onClick={() => setPending({ kind: 'status', status: 'PUBLISHED' })}>
                    Publish
                  </Button>
                )}
              </>
            )}
          </>
        }
      />

      {notice && (
        <div className="mb-5">
          <Alert tone={notice.tone}>{notice.text}</Alert>
        </div>
      )}

      {totals && (
        <dl className="mb-5 grid grid-cols-2 gap-3 lg:grid-cols-4" aria-label="Totals">
          <StatTile label="Registered / capacity" value={<span data-testid="registered-total">{totals.registered} / {totals.capacity}</span>} hint={`${totals.spotsLeft} spots left`} />
          <StatTile label="Checked in / registered" value={<span data-testid="checked-in-total">{totals.checkedIn} / {totals.registered}</span>} tone="success" />
          <StatTile label="Waitlisted" value={totals.waitlisted} tone={totals.waitlisted > 0 ? 'warning' : 'default'} />
          <StatTile label="Cancelled" value={totals.cancelled} tone="muted" />
        </dl>
      )}

      {canManage && totals && totals.waitlisted > 0 && (
        <div className="mb-5 flex flex-col gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="text-sm text-amber-900">
            <p className="font-semibold">{totals.waitlisted} {totals.waitlisted === 1 ? 'person is' : 'people are'} on the waitlist</p>
            <p>
              {totals.spotsLeft > 0
                ? `${totals.spotsLeft} ${totals.spotsLeft === 1 ? 'spot is' : 'spots are'} available. Promote the first person in line.`
                : 'No spot is available. Cancel a registration or increase capacity to promote someone.'}
            </p>
          </div>
          <Button onClick={() => setPending({ kind: 'promote' })} disabled={!canPromote} icon={<ArrowUpCircle className="size-4" aria-hidden />}>
            Promote next in line
          </Button>
        </div>
      )}

      <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="relative w-full md:max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
          <label htmlFor="attendee-search" className="sr-only">
            Search by name or ticket code
          </label>
          <input
            id="attendee-search"
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by name or ticket code"
            className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 text-sm shadow-sm focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-600/20"
          />
        </div>
        <div className="flex gap-1 overflow-x-auto" role="group" aria-label="Filter by status">
          {filters.map((f) => (
            <button
              key={f.value}
              type="button"
              aria-pressed={filter === f.value}
              onClick={() => setFilter(f.value)}
              className={`shrink-0 rounded-full px-3 py-1.5 text-xs font-medium ${
                filter === f.value ? 'bg-slate-900 text-white' : 'bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {list.error && <Alert tone="error">{list.error.message}</Alert>}
      {list.loading && !list.data && <LoadingBlock />}
      {list.data && rows.length === 0 && (
        <EmptyState icon={<Users className="size-6" />} title={debounced || filter !== 'ALL' ? 'No matching attendees' : 'No registrations yet'}>
          {debounced || filter !== 'ALL' ? 'Try a different name, ticket code or filter.' : 'Registrations will appear here as attendees sign up.'}
        </EmptyState>
      )}

      {rows.length > 0 && (
        <>
          {/* Desktop / tablet: table */}
          <div className="hidden overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm lg:block">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <caption className="sr-only">Registrations for {ev.title}</caption>
              <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="px-4 py-3">Attendee</th>
                  <th scope="col" className="px-4 py-3">Ticket code</th>
                  <th scope="col" className="px-4 py-3">Registered</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  <th scope="col" className="px-4 py-3">Check-in</th>
                  <th scope="col" className="px-4 py-3 text-right">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((a) => (
                  <tr key={a.id} className="align-top">
                    <td className="px-4 py-3">
                      <p className="font-medium text-slate-900">{a.fullName}</p>
                      <p className="text-slate-500">{a.email}</p>
                      <p className="tabular-nums text-slate-500">{formatMobile(a.mobile)}</p>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-slate-800">{a.ticketCode}</td>
                    <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTimeCompact(a.registeredAt)}</td>
                    <td className="px-4 py-3">
                      <RegistrationStatusBadge status={a.status} />
                      {a.waitlistPosition != null && <p className="mt-1 text-xs text-amber-800">#{a.waitlistPosition} in line</p>}
                    </td>
                    <td className="px-4 py-3 text-slate-600">
                      <CheckInDetail attendee={a} />
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right">
                      <RowActions attendee={a} canManage={canManage} onAction={setPending} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Phone and tablet: cards */}
          <ul className="grid gap-3 md:grid-cols-2 lg:hidden">
            {rows.map((a) => (
              <li key={a.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-semibold text-slate-900">{a.fullName}</p>
                    <p className="truncate text-sm text-slate-500">{a.email}</p>
                  </div>
                  <RegistrationStatusBadge status={a.status} />
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-x-3 gap-y-2 text-sm">
                  <div>
                    <dt className="text-xs text-slate-500">Ticket code</dt>
                    <dd className="font-mono text-xs text-slate-800">{a.ticketCode}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-500">Mobile</dt>
                    <dd className="tabular-nums">{formatMobile(a.mobile)}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-500">Registered</dt>
                    <dd>{formatDateTime(a.registeredAt)}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-500">Check-in</dt>
                    <dd>
                      <CheckInDetail attendee={a} />
                    </dd>
                  </div>
                </dl>
                <div className="mt-3 flex justify-end gap-2 border-t border-slate-100 pt-3">
                  <RowActions attendee={a} canManage={canManage} onAction={setPending} />
                </div>
              </li>
            ))}
          </ul>
        </>
      )}

      <ConfirmDialog
        open={pending !== undefined}
        title={dialogTitle(pending)}
        confirmLabel={dialogConfirm(pending)}
        tone={pending?.kind === 'cancel' ? 'danger' : 'primary'}
        busy={busy}
        onConfirm={() => void runPending()}
        onCancel={() => setPending(undefined)}
      >
        {dialogBody(pending)}
      </ConfirmDialog>
    </>
  )
}

function CheckInDetail({ attendee }: { attendee: Attendee }) {
  if (attendee.status === 'CHECKED_IN') {
    return (
      <span className="whitespace-nowrap">
        <span className="font-medium text-emerald-700">{formatDateTimeCompact(attendee.checkedInAt)}</span>
        {attendee.checkedInBy && <span className="block text-xs text-slate-500">by {attendee.checkedInBy}</span>}
      </span>
    )
  }
  if (attendee.status === 'CANCELLED') {
    return <span className="text-xs text-slate-500">Cancelled {attendee.cancelledBy ? `by ${attendee.cancelledBy}` : ''}</span>
  }
  return (
    <span>
      <span className="whitespace-nowrap">Not checked in</span>
      {attendee.checkInUndoneAt && (
        <span className="block text-xs text-slate-500">
          Check-in undone {formatDateTimeCompact(attendee.checkInUndoneAt)} by {attendee.checkInUndoneBy}
        </span>
      )}
    </span>
  )
}

function RowActions({ attendee, canManage, onAction }: { attendee: Attendee; canManage: boolean; onAction: (a: PendingAction) => void }) {
  return (
    <div className="inline-flex gap-1">
      {attendee.status === 'CHECKED_IN' && (
        <Button size="sm" variant="ghost" onClick={() => onAction({ kind: 'undo', attendee })} icon={<Undo2 className="size-4" aria-hidden />} aria-label={`Undo check-in for ${attendee.fullName}`}>
          Undo check-in
        </Button>
      )}
      {canManage && (attendee.status === 'CONFIRMED' || attendee.status === 'WAITLISTED') && (
        <Button
          size="sm"
          variant="ghost"
          className="text-red-700! hover:bg-red-50!"
          onClick={() => onAction({ kind: 'cancel', attendee })}
          icon={<UserX className="size-4" aria-hidden />}
          aria-label={`Cancel registration for ${attendee.fullName}`}
        >
          Cancel
        </Button>
      )}
    </div>
  )
}

function dialogTitle(action?: PendingAction): string {
  switch (action?.kind) {
    case 'cancel':
      return 'Cancel this registration?'
    case 'undo':
      return 'Undo this check-in?'
    case 'promote':
      return 'Promote the next person on the waitlist?'
    case 'status':
      return action.status === 'CLOSED' ? 'Close registration?' : 'Publish this event?'
    default:
      return ''
  }
}

function dialogConfirm(action?: PendingAction): string {
  switch (action?.kind) {
    case 'cancel':
      return 'Cancel registration'
    case 'undo':
      return 'Undo check-in'
    case 'promote':
      return 'Promote'
    case 'status':
      return action.status === 'CLOSED' ? 'Close registration' : 'Publish'
    default:
      return 'Confirm'
  }
}

function dialogBody(action?: PendingAction) {
  switch (action?.kind) {
    case 'cancel':
      return (
        <>
          <strong>{action.attendee.fullName}</strong> ({action.attendee.ticketCode}) will lose their{' '}
          {action.attendee.status === 'WAITLISTED' ? 'place on the waitlist' : 'seat, and the ticket will show "Cancelled" if scanned'}.
          This is recorded in the audit log.
        </>
      )
    case 'undo':
      return (
        <>
          <strong>{action.attendee.fullName}</strong> will be marked as not checked in, and their ticket can be checked in again.
          The undo is recorded with your name and the time.
        </>
      )
    case 'promote':
      return 'The earliest person on the waitlist gets the open spot and a confirmed ticket.'
    case 'status':
      return action.status === 'CLOSED'
        ? 'Attendees will no longer be able to register. Existing tickets stay valid for check-in.'
        : 'The event becomes visible to attendees and opens for registration.'
    default:
      return null
  }
}
