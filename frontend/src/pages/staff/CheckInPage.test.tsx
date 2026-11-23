import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

interface ScannerProps {
  paused: boolean
  onDecode: (text: string) => void
}
let scanner: ScannerProps | undefined

// Replace the real camera with a controllable fake.
vi.mock('../../components/checkin/QrScanner', () => ({
  QrScanner: (props: ScannerProps) => {
    scanner = props
    return <div data-testid="fake-scanner" data-paused={String(props.paused)} />
  },
}))

import { CheckInConsole, RESULT_DISPLAY_MS } from './CheckInPage'

const event = {
  id: 1,
  title: 'Cebu Dev Meetup 2026',
  startsAt: '2026-11-23T01:00:00Z',
  endsAt: '2026-11-23T13:00:00Z',
  capacity: 30,
  eventDay: true,
  stats: { checkedIn: 4, registered: 18 },
}

function checkInResponse(code: string) {
  if (code === 'CDM-7K3P9-Q2XHM') {
    return {
      result: 'CHECKED_IN',
      label: 'Checked in',
      tone: 'SUCCESS',
      message: 'Welcome!',
      registrationId: 7,
      attendeeName: 'Juan dela Cruz',
      ticketCode: code,
      checkedInAt: '2026-11-23T02:15:00Z',
      stats: { checkedIn: 5, registered: 18, capacity: 30 },
    }
  }
  return { result: 'INVALID', label: 'Invalid ticket', tone: 'ERROR', message: 'No match.', stats: { checkedIn: 5, registered: 18, capacity: 30 } }
}

let checkInCalls: string[]

beforeEach(() => {
  scanner = undefined
  checkInCalls = []
  document.cookie = 'XSRF-TOKEN=t'
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/check-ins')) {
        const code = JSON.parse(String(init?.body)).ticketCode as string
        checkInCalls.push(code)
        return new Response(JSON.stringify(checkInResponse(code)), { status: 200 })
      }
      return new Response(JSON.stringify(event), { status: 200 })
    }),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
})

async function startCamera() {
  render(
    <MemoryRouter>
      <CheckInConsole eventId="1" />
    </MemoryRouter>,
  )
  expect(await screen.findByTestId('live-counter')).toHaveTextContent('4 / 18')
  await userEvent.click(screen.getByRole('button', { name: 'Start camera' }))
  await waitFor(() => expect(scanner).toBeDefined())
}

describe('Camera check-in flow', () => {
  it('shows the result, holds ~2 seconds, then automatically gets ready for the next scan', async () => {
    await startCamera()

    act(() => scanner!.onDecode('CDM-7K3P9-Q2XHM'))

    const result = await screen.findByTestId('check-in-result')
    expect(result).toHaveTextContent('Checked in')
    expect(result).toHaveTextContent('Juan dela Cruz')
    expect(screen.getByTestId('live-counter')).toHaveTextContent('5 / 18')
    expect(screen.getByTestId('fake-scanner')).toHaveAttribute('data-paused', 'true')

    // The same QR still in front of the camera is not processed again.
    act(() => scanner!.onDecode('CDM-7K3P9-Q2XHM'))
    expect(checkInCalls).toEqual(['CDM-7K3P9-Q2XHM'])

    await waitFor(() => expect(screen.queryByTestId('check-in-result')).not.toBeInTheDocument(), {
      timeout: RESULT_DISPLAY_MS + 1500,
    })
    expect(screen.getByTestId('fake-scanner')).toHaveAttribute('data-paused', 'false')

    // Still ignored right after the reset (repeat window), but a different ticket is processed.
    act(() => scanner!.onDecode('CDM-7K3P9-Q2XHM'))
    act(() => scanner!.onDecode('CDM-ZZZZZ-ZZZZZ'))
    expect(await screen.findByTestId('check-in-result')).toHaveTextContent('Invalid ticket')
    expect(checkInCalls).toEqual(['CDM-7K3P9-Q2XHM', 'CDM-ZZZZZ-ZZZZZ'])
  })

  it('supports manual ticket entry as a fallback', async () => {
    render(
      <MemoryRouter>
        <CheckInConsole eventId="1" />
      </MemoryRouter>,
    )
    const input = await screen.findByLabelText('Enter ticket code')
    await userEvent.type(input, 'CDM-7K3P9-Q2XHM{Enter}')

    expect(await screen.findByTestId('check-in-result')).toHaveTextContent('Checked in')
    expect(input).toHaveValue('')
  })
})
