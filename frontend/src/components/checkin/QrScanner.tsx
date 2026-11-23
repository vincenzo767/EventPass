import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser'
import { CameraOff, Loader2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

interface QrScannerProps {
  /** While paused, decoded codes are ignored (a result is being shown). */
  paused: boolean
  onDecode: (text: string) => void
}

type CameraState = { status: 'starting' } | { status: 'scanning' } | { status: 'error'; message: string }

/** Turns a getUserMedia / decoder failure into guidance door staff can act on. */
export function describeCameraError(error: unknown): string {
  const name = error instanceof Error || (error && typeof error === 'object' && 'name' in error) ? String((error as { name: string }).name) : ''
  switch (name) {
    case 'NotAllowedError':
    case 'SecurityError':
    case 'PermissionDeniedError':
      return 'Camera access was blocked. Allow camera permission for this site in your browser settings, then reload. You can keep using manual entry below.'
    case 'NotFoundError':
    case 'OverconstrainedError':
    case 'DevicesNotFoundError':
      return 'No camera was found on this device. Use manual ticket entry below.'
    case 'NotReadableError':
    case 'TrackStartError':
      return 'The camera is being used by another app. Close it and try again, or use manual entry below.'
    default:
      return 'The camera could not be started. Use manual ticket entry below.'
  }
}

/**
 * Live QR scanner using the device camera (rear camera preferred on phones). Requires a secure
 * context (HTTPS or localhost). Manual entry stays available when the camera cannot be used.
 */
export function QrScanner({ paused, onDecode }: QrScannerProps) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [state, setState] = useState<CameraState>({ status: 'starting' })
  const onDecodeRef = useRef(onDecode)
  const pausedRef = useRef(paused)
  onDecodeRef.current = onDecode
  pausedRef.current = paused

  useEffect(() => {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      setState({
        status: 'error',
        message: 'Camera scanning needs a secure (HTTPS) connection and a browser with camera support. Use manual entry below.',
      })
      return
    }
    let cancelled = false
    let controls: IScannerControls | undefined
    const reader = new BrowserQRCodeReader(undefined, { delayBetweenScanAttempts: 120, delayBetweenScanSuccess: 400 })

    reader
      .decodeFromConstraints(
        { audio: false, video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } } },
        videoRef.current ?? undefined,
        (result) => {
          if (result && !pausedRef.current) onDecodeRef.current(result.getText())
        },
      )
      .then((c) => {
        if (cancelled) c.stop()
        else {
          controls = c
          setState({ status: 'scanning' })
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) setState({ status: 'error', message: describeCameraError(error) })
      })

    return () => {
      cancelled = true
      controls?.stop()
    }
  }, [])

  if (state.status === 'error') {
    return (
      <div role="alert" className="flex aspect-[4/3] w-full flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-slate-300 bg-slate-100 p-6 text-center">
        <CameraOff className="size-8 text-slate-500" aria-hidden />
        <p className="max-w-sm text-sm text-slate-700">{state.message}</p>
      </div>
    )
  }

  return (
    <div className="relative aspect-[4/3] w-full overflow-hidden rounded-xl bg-slate-900">
      <video ref={videoRef} className="size-full object-cover" muted playsInline aria-label="Camera preview for scanning QR tickets" />
      {/* Aiming frame */}
      <div className="pointer-events-none absolute inset-0 flex items-center justify-center" aria-hidden>
        <div className={`size-[58%] max-h-64 max-w-64 rounded-2xl border-4 ${paused ? 'border-white/30' : 'border-white/80'} shadow-[0_0_0_9999px_rgba(15,23,42,0.35)]`} />
      </div>
      <p className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-slate-900/80 to-transparent px-4 pb-3 pt-8 text-center text-sm font-medium text-white" aria-live="polite">
        {state.status === 'starting' ? (
          <span className="inline-flex items-center gap-2">
            <Loader2 className="size-4 animate-spin" aria-hidden /> Starting camera…
          </span>
        ) : paused ? (
          'Showing result…'
        ) : (
          'Point the camera at a ticket QR code'
        )}
      </p>
    </div>
  )
}
