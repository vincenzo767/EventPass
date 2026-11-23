import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const decodeFromConstraints = vi.fn()

vi.mock('@zxing/browser', () => ({
  BrowserQRCodeReader: vi.fn().mockImplementation(() => ({ decodeFromConstraints })),
}))

import { describeCameraError, QrScanner } from './QrScanner'

function domError(name: string) {
  const error = new Error(name)
  error.name = name
  return error
}

describe('QrScanner', () => {
  beforeEach(() => {
    decodeFromConstraints.mockReset()
    Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true })
    Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia: vi.fn() }, configurable: true })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('explains how to recover when camera permission is blocked', async () => {
    decodeFromConstraints.mockRejectedValue(domError('NotAllowedError'))
    render(<QrScanner paused={false} onDecode={vi.fn()} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/Camera access was blocked/)
    expect(screen.getByRole('alert')).toHaveTextContent(/manual entry/)
  })

  it('reports a missing camera', async () => {
    decodeFromConstraints.mockRejectedValue(domError('NotFoundError'))
    render(<QrScanner paused={false} onDecode={vi.fn()} />)
    expect(await screen.findByRole('alert')).toHaveTextContent(/No camera was found/)
  })

  it('requires a secure context', async () => {
    Object.defineProperty(window, 'isSecureContext', { value: false, configurable: true })
    render(<QrScanner paused={false} onDecode={vi.fn()} />)
    expect(await screen.findByRole('alert')).toHaveTextContent(/secure \(HTTPS\) connection/)
    expect(decodeFromConstraints).not.toHaveBeenCalled()
  })

  it('requests the rear camera and forwards decoded codes unless paused', async () => {
    let callback: ((result: { getText: () => string } | undefined) => void) | undefined
    const stop = vi.fn()
    decodeFromConstraints.mockImplementation((_constraints, _video, cb) => {
      callback = cb
      return Promise.resolve({ stop })
    })
    const onDecode = vi.fn()
    const { rerender, unmount } = render(<QrScanner paused={false} onDecode={onDecode} />)

    expect(await screen.findByText('Point the camera at a ticket QR code')).toBeInTheDocument()
    const constraints = decodeFromConstraints.mock.calls[0][0]
    expect(constraints.video.facingMode).toEqual({ ideal: 'environment' })

    callback?.({ getText: () => 'CDM-7K3P9-Q2XHM' })
    expect(onDecode).toHaveBeenCalledWith('CDM-7K3P9-Q2XHM')

    rerender(<QrScanner paused={true} onDecode={onDecode} />)
    callback?.({ getText: () => 'CDM-AAAAA-BBBBB' })
    expect(onDecode).toHaveBeenCalledTimes(1)

    unmount()
    expect(stop).toHaveBeenCalled()
  })

  it('maps camera errors to actionable messages', () => {
    expect(describeCameraError(domError('NotReadableError'))).toMatch(/another app/)
    expect(describeCameraError(new Error('weird'))).toMatch(/could not be started/)
  })
})
