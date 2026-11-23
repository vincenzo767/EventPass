import { Camera, CameraOff, Keyboard, ScanLine } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { CheckInResultPanel } from '../../components/checkin/CheckInResultPanel'
import { QrScanner } from '../../components/checkin/QrScanner'
import { ScanGate } from '../../components/checkin/scanGate'
import { PageHeader } from '../../components/StaffLayout'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Alert, EmptyState, LoadingBlock } from '../../components/ui/Feedback'
import { api, ApiError } from '../../lib/api'
import { formatDateLong, formatTime, formatTimeRange } from '../../lib/format'
import type { CheckInResponse, StaffEvent } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

/** How long a camera scan result stays on screen before the scanner is ready again. */
export const RESULT_DISPLAY_MS = 2000
const STATS_REFRESH_MS = 10_000

export function CheckInPage() {
  const { eventId } = useParams()
  return eventId ? <CheckInConsole eventId={eventId} /> : <EventPicker />
}

function EventPicker() {
  useDocumentTitle('Check-in')
  const { data, error, loading } = useAsync(() => api.get<StaffEvent[]>('/api/staff/events'), [])
  const events = (data ?? [])
    .filter((e) => !e.ended && e.status !== 'DRAFT')
    .sort((a, b) => Number(b.eventDay) - Number(a.eventDay) || a.startsAt.localeCompare(b.startsAt))

  return (
    <>
      <PageHeader title="Check-in" description="Choose the event you are checking attendees in for." />
      {loading && !data && <LoadingBlock />}
      {error && <Alert tone="error">{error.message}</Alert>}
      {data && events.length === 0 && (
        <EmptyState icon={<ScanLine className="size-6" />} title="No events to check in">
          Only published events that have not ended are listed here.
        </EmptyState>
      )}
      <ul className="grid gap-3 sm:grid-cols-2">
        {events.map((event) => (
          <li key={event.id}>
            <Link
              to={`/staff/check-in/${event.id}`}
              className="block h-full rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:border-brand-300 hover:shadow-md"
            >
              <div className="flex items-center justify-between gap-2">
                <p className="font-semibold text-slate-900">{event.title}</p>
                {event.eventDay ? <Badge tone="success">Today</Badge> : <Badge tone="muted">Not today</Badge>}
              </div>
              <p className="mt-1 text-sm text-slate-600">
                {formatDateLong(event.startsAt)} · {formatTimeRange(event.startsAt, event.endsAt)}
              </p>
              <p className="mt-2 text-sm tabular-nums text-slate-700">
                {event.stats.checkedIn} / {event.stats.registered} checked in
              </p>
            </Link>
          </li>
        ))}
      </ul>
    </>
  )
}

interface RecentScan {
  id: number
  response: CheckInResponse
  at: number
}

export function CheckInConsole({ eventId }: { eventId: string }) {
  const event = useAsync(() => api.get<StaffEvent>(`/api/staff/events/${eventId}`), [eventId])
  useDocumentTitle(event.data ? `Check-in · ${event.data.title}` : 'Check-in')

  const [stats, setStats] = useState<CheckInResponse['stats']>()
  const [result, setResult] = useState<CheckInResponse>()
  const [requestError, setRequestError] = useState<string>()
  const [processing, setProcessing] = useState(false)
  const [cameraOn, setCameraOn] = useState(false)
  const [cameraHold, setCameraHold] = useState(false)
  const [code, setCode] = useState('')
  const [recent, setRecent] = useState<RecentScan[]>([])
  const [undoingId, setUndoingId] = useState<number>()
  const gate = useRef(new ScanGate())
  const busy = useRef(false)
  const holdTimer = useRef<number | undefined>(undefined)
  const inputRef = useRef<HTMLInputElement>(null)
  const nextId = useRef(1)

  // Live counter: seeded from the event, updated by every check-in response and refreshed
  // periodically so check-ins from other devices show up too.
  useEffect(() => {
    if (event.data) setStats({ checkedIn: event.data.stats.checkedIn, registered: event.data.stats.registered, capacity: event.data.capacity })
  }, [event.data])
  useEffect(() => {
    const timer = window.setInterval(() => void event.reload(), STATS_REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [event.reload]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => () => window.clearTimeout(holdTimer.current), [])

  const submit = useCallback(
    async (rawCode: string, source: 'camera' | 'manual') => {
      if (busy.current) return
      busy.current = true
      setProcessing(true)
      setRequestError(undefined)
      if (source === 'camera') setCameraHold(true)
      try {
        const response = await api.post<CheckInResponse>(`/api/staff/events/${eventId}/check-ins`, { ticketCode: rawCode })
        setResult(response)
        setStats(response.stats)
        setRecent((list) => [{ id: nextId.current++, response, at: Date.now() }, ...list].slice(0, 8))
      } catch (e) {
        setResult(undefined)
        setRequestError(e instanceof ApiError ? e.message : 'Check-in failed. Please try again.')
      } finally {
        busy.current = false
        setProcessing(false)
        if (source === 'camera') {
          // Show the result for ~2 seconds, then get ready for the next scan automatically.
          window.clearTimeout(holdTimer.current)
          holdTimer.current = window.setTimeout(() => {
            setCameraHold(false)
            setResult(undefined)
          }, RESULT_DISPLAY_MS)
        }
      }
    },
    [eventId],
  )

  const onDecode = useCallback(
    (text: string) => {
      if (busy.current || !gate.current.accept(text, Date.now())) return
      void submit(text, 'camera')
    },
    [submit],
  )

  function onManualSubmit(e: FormEvent) {
    e.preventDefault()
    const value = code.trim()
    if (!value) {
      setRequestError('Enter a ticket code.')
      return
    }
    setCode('')
    void submit(value, 'manual').then(() => inputRef.current?.focus())
  }

  async function undo(registrationId: number) {
    setUndoingId(registrationId)
    try {
      const response = await api.post<CheckInResponse>(`/api/staff/registrations/${registrationId}/undo-check-in`)
      setStats(response.stats)
      setResult(undefined)
      setRecent((list) => list.map((item) => (item.response.registrationId === registrationId && item.response.result === 'CHECKED_IN'
        ? { ...item, response: { ...item.response, label: 'Check-in undone', tone: 'WARNING' as const, result: undefined } }
        : item)))
    } catch (e) {
      setRequestError(e instanceof ApiError ? e.message : 'Undo failed.')
    } finally {
      setUndoingId(undefined)
    }
  }

  if (event.loading && !event.data) return <LoadingBlock />
  if (event.error && !event.data) return <Alert tone="error">{event.error.status === 404 ? 'Event not found.' : event.error.message}</Alert>
  const ev = event.data!

  return (
    <>
      <PageHeader
        title="Check-in"
        description={
          <span>
            <span className="font-medium text-slate-800">{ev.title}</span> · {formatDateLong(ev.startsAt)} ·{' '}
            <Link to="/staff/check-in" className="text-brand-700 underline">Change event</Link>
          </span>
        }
      />

      {!ev.eventDay && (
        <div className="mb-4">
          <Alert tone="warning" title="Not the event day">
            Check-in is only allowed on the event day ({formatDateLong(ev.startsAt)}). Confirmed tickets scanned today will
            show "Not event day".
          </Alert>
        </div>
      )}

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-4">
          {stats && (
            <div className="flex items-center justify-between rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm" aria-live="polite">
              <span className="text-sm font-medium text-slate-600">Live counter</span>
              <span className="text-2xl font-semibold tabular-nums text-slate-900" data-testid="live-counter">
                {stats.checkedIn} / {stats.registered} <span className="text-base font-medium text-slate-500">checked in</span>
              </span>
            </div>
          )}

          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between gap-2">
              <h2 className="flex items-center gap-2 font-semibold text-slate-900">
                <Camera className="size-4" aria-hidden /> Camera scanner
              </h2>
              <Button
                size="sm"
                variant={cameraOn ? 'secondary' : 'primary'}
                onClick={() => setCameraOn((on) => !on)}
                icon={cameraOn ? <CameraOff className="size-4" aria-hidden /> : <Camera className="size-4" aria-hidden />}
              >
                {cameraOn ? 'Stop camera' : 'Start camera'}
              </Button>
            </div>
            {cameraOn ? (
              <QrScanner paused={processing || cameraHold} onDecode={onDecode} />
            ) : (
              <p className="rounded-lg bg-slate-50 px-4 py-6 text-center text-sm text-slate-600">
                Start the camera to scan QR tickets from a phone screen or printout. The result shows for about 2 seconds,
                then the scanner is ready for the next ticket.
              </p>
            )}
          </div>

          <form onSubmit={onManualSubmit} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm" noValidate>
            <label htmlFor="ticket-code-input" className="mb-2 flex items-center gap-2 font-semibold text-slate-900">
              <Keyboard className="size-4" aria-hidden /> Enter ticket code
            </label>
            <div className="flex gap-2">
              <input
                ref={inputRef}
                id="ticket-code-input"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="e.g. CDM-7K3P9-Q2XHM"
                autoComplete="off"
                autoCapitalize="characters"
                spellCheck={false}
                autoFocus
                className="h-12 min-w-0 flex-1 rounded-lg border border-slate-300 px-3 font-mono text-base uppercase shadow-sm placeholder:font-sans placeholder:normal-case focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-600/20"
              />
              <Button type="submit" size="lg" loading={processing}>
                Check in
              </Button>
            </div>
            <p className="mt-2 text-xs text-slate-500">USB and Bluetooth QR scanners that type the code also work here.</p>
          </form>

          {requestError && <Alert tone="error">{requestError}</Alert>}
          {result && (
            <CheckInResultPanel
              result={result}
              undoing={undoingId === result.registrationId}
              onUndo={result.registrationId ? () => void undo(result.registrationId!) : undefined}
            />
          )}
        </div>

        <aside aria-label="Recent scans" className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm lg:self-start">
          <h2 className="font-semibold text-slate-900">Recent scans</h2>
          {recent.length === 0 ? (
            <p className="mt-2 text-sm text-slate-500">Scans from this device appear here.</p>
          ) : (
            <ul className="mt-3 divide-y divide-slate-100">
              {recent.map((item) => (
                <li key={item.id} className="flex items-start justify-between gap-2 py-2.5">
                  <div className="min-w-0">
                    <p className={`text-sm font-semibold ${toneText(item.response.tone)}`}>{item.response.label}</p>
                    <p className="truncate text-sm text-slate-700">{item.response.attendeeName ?? 'Unknown code'}</p>
                    <p className="text-xs text-slate-500">{formatTime(new Date(item.at).toISOString())}</p>
                  </div>
                  {item.response.result === 'CHECKED_IN' && item.response.registrationId && (
                    <Button
                      size="sm"
                      variant="ghost"
                      loading={undoingId === item.response.registrationId}
                      onClick={() => void undo(item.response.registrationId!)}
                      aria-label={`Undo check-in for ${item.response.attendeeName}`}
                    >
                      Undo
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </aside>
      </div>
    </>
  )
}

function toneText(tone?: CheckInResponse['tone']): string {
  if (tone === 'SUCCESS') return 'text-emerald-700'
  if (tone === 'WARNING') return 'text-amber-700'
  return 'text-red-700'
}
