import { ArrowLeft, Bookmark } from 'lucide-react'
import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { TicketCard } from '../../components/TicketCard'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { Alert, LoadingBlock } from '../../components/ui/Feedback'
import { api, ApiError } from '../../lib/api'
import type { Ticket } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

interface LocationState {
  justRegistered?: boolean
  emailQueued?: boolean
}

export function TicketPage() {
  const { token = '' } = useParams()
  const location = useLocation()
  const state = (location.state ?? {}) as LocationState
  const { data: ticket, error, loading, setData } = useAsync(
    () => api.get<Ticket>(`/api/public/tickets/${encodeURIComponent(token)}`),
    [token],
  )
  const [confirming, setConfirming] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState<string>()
  useDocumentTitle(ticket ? `Ticket · ${ticket.event.title}` : 'Your ticket')

  async function cancelRegistration() {
    setCancelling(true)
    setCancelError(undefined)
    try {
      setData(await api.post<Ticket>(`/api/public/tickets/${encodeURIComponent(token)}/cancel`))
      setConfirming(false)
    } catch (e) {
      setCancelError(e instanceof ApiError ? e.message : 'The registration could not be cancelled.')
      setConfirming(false)
    } finally {
      setCancelling(false)
    }
  }

  return (
    <div className="mx-auto max-w-md px-4 py-6 sm:py-10">
      <Link to="/" className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900">
        <ArrowLeft className="size-4" aria-hidden /> Events
      </Link>

      {loading && !ticket && <LoadingBlock label="Loading your ticket…" />}
      {error && (
        <div className="mt-6 space-y-4">
          <Alert tone="error" title="Ticket not found">
            This ticket link is not valid. You can look up your ticket with your ticket code and email.
          </Alert>
          <Link to="/tickets/find" className="font-semibold text-brand-700 underline">
            Find my ticket
          </Link>
        </div>
      )}

      {ticket && (
        <div className="mt-5 space-y-5">
          {state.justRegistered && ticket.status !== 'CANCELLED' && (
            <Alert tone={ticket.status === 'WAITLISTED' ? 'warning' : 'success'} title={ticket.status === 'WAITLISTED' ? "You're on the waitlist" : "You're registered!"}>
              {ticket.status === 'WAITLISTED'
                ? 'Your registration is not confirmed yet.'
                : 'Your ticket is ready. Keep this page handy for the event.'}
              {state.emailQueued && <> A confirmation email is being sent to {ticket.maskedEmail}.</>}
            </Alert>
          )}
          {cancelError && <Alert tone="error">{cancelError}</Alert>}

          <TicketCard ticket={ticket} />

          <div className="flex gap-3 rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-600">
            <Bookmark className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden />
            <p>
              Bookmark this page, or note your ticket code. You can always find this ticket again with your ticket code and
              registered email on <Link to="/tickets/find" className="font-medium text-brand-700 underline">Find my ticket</Link>.
            </p>
          </div>

          {ticket.cancellable && (
            <div className="text-center">
              <Button variant="ghost" className="text-red-700! hover:bg-red-50!" onClick={() => setConfirming(true)}>
                {ticket.status === 'WAITLISTED' ? 'Leave the waitlist' : 'Cancel my registration'}
              </Button>
            </div>
          )}
        </div>
      )}

      <ConfirmDialog
        open={confirming}
        title={ticket?.status === 'WAITLISTED' ? 'Leave the waitlist?' : 'Cancel your registration?'}
        confirmLabel={ticket?.status === 'WAITLISTED' ? 'Leave waitlist' : 'Cancel registration'}
        busy={cancelling}
        onConfirm={() => void cancelRegistration()}
        onCancel={() => setConfirming(false)}
      >
        Your spot will be released and this ticket will no longer be valid. You can register again later if spots are
        still available.
      </ConfirmDialog>
    </div>
  )
}
