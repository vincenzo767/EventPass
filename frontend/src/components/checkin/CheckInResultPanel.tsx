import { CheckCircle2, CircleAlert, Clock, Undo2, XCircle } from 'lucide-react'
import { formatDateTime, formatTime } from '../../lib/format'
import type { CheckInResponse } from '../../lib/types'
import { Button } from '../ui/Button'

const toneStyles = {
  SUCCESS: { panel: 'bg-emerald-600 text-white', icon: <CheckCircle2 className="size-12" aria-hidden /> },
  WARNING: { panel: 'bg-amber-400 text-amber-950', icon: <CircleAlert className="size-12" aria-hidden /> },
  ERROR: { panel: 'bg-red-600 text-white', icon: <XCircle className="size-12" aria-hidden /> },
} as const

interface Props {
  result: CheckInResponse
  onUndo?: () => void
  undoing?: boolean
}

/**
 * The single, unmistakable result shown to door staff. Status is conveyed by text and icon as well
 * as colour, so it is readable for colour-blind staff and in bright light.
 */
export function CheckInResultPanel({ result, onUndo, undoing }: Props) {
  const tone = toneStyles[result.tone ?? 'ERROR']
  return (
    <div role="status" aria-live="assertive" data-testid="check-in-result" data-result={result.result} className={`rounded-xl p-5 ${tone.panel}`}>
      <div className="flex items-start gap-4">
        {tone.icon}
        <div className="min-w-0 flex-1">
          <p className="text-2xl font-bold leading-tight sm:text-3xl">{result.label}</p>
          {result.attendeeName && <p className="mt-1 truncate text-lg font-semibold">{result.attendeeName}</p>}
          {result.result === 'CHECKED_IN' && result.checkedInAt && (
            <p className="mt-1 inline-flex items-center gap-1.5 text-sm opacity-90">
              <Clock className="size-4" aria-hidden /> Checked in at {formatTime(result.checkedInAt)}
            </p>
          )}
          {result.result === 'ALREADY_CHECKED_IN' && result.checkedInAt && (
            <p className="mt-1 inline-flex items-center gap-1.5 text-sm font-semibold">
              <Clock className="size-4" aria-hidden /> First checked in {formatDateTime(result.checkedInAt)}
            </p>
          )}
          {result.result !== 'CHECKED_IN' && result.result !== 'ALREADY_CHECKED_IN' && (
            <p className="mt-1 text-sm opacity-90">{result.message}</p>
          )}
          {result.ticketCode && <p className="mt-2 font-mono text-xs opacity-80">{result.ticketCode}</p>}
        </div>
      </div>
      {onUndo && result.result === 'CHECKED_IN' && (
        <div className="mt-4 flex justify-end">
          <Button size="sm" variant="secondary" onClick={onUndo} loading={undoing} icon={<Undo2 className="size-4" aria-hidden />}>
            Undo check-in
          </Button>
        </div>
      )}
    </div>
  )
}
