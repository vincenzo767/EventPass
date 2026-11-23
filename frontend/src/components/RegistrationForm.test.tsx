import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RegistrationForm } from './RegistrationForm'

function renderForm(waitlist = false) {
  const onRegistered = vi.fn()
  render(
    <MemoryRouter>
      <RegistrationForm eventId={1} waitlist={waitlist} onRegistered={onRegistered} />
    </MemoryRouter>,
  )
  return { onRegistered, user: userEvent.setup() }
}

function mockFetch(status: number, body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status }))
  vi.stubGlobal('fetch', fetchMock)
  document.cookie = 'XSRF-TOKEN=test-token'
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('RegistrationForm', () => {
  it('shows errors next to every empty required field and does not submit', async () => {
    const fetchMock = mockFetch(201, {})
    const { user } = renderForm()

    await user.click(screen.getByRole('button', { name: 'Register' }))

    expect(await screen.findByText('Full name is required.')).toBeInTheDocument()
    expect(screen.getByText('Email address is required.')).toBeInTheDocument()
    expect(screen.getByText('Mobile number is required.')).toBeInTheDocument()
    expect(screen.getByLabelText(/Full name/)).toHaveAttribute('aria-invalid', 'true')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects a bad email and a 10-digit mobile number', async () => {
    const fetchMock = mockFetch(201, {})
    const { user } = renderForm()

    await user.type(screen.getByLabelText(/Full name/), 'Juan dela Cruz')
    await user.type(screen.getByLabelText(/Email address/), 'juan@invalid')
    await user.type(screen.getByLabelText(/Mobile number/), '0917123456')
    await user.click(screen.getByRole('button', { name: 'Register' }))

    expect(await screen.findByText(/Enter a valid email address/)).toBeInTheDocument()
    expect(screen.getByText(/Enter an 11-digit mobile number starting with 09/)).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('submits normalized values with the CSRF header and reports the ticket', async () => {
    const ticket = { ticketCode: 'CDM-7K3P9-Q2XHM', accessToken: 'abc', status: 'CONFIRMED' }
    const fetchMock = mockFetch(201, ticket)
    const { user, onRegistered } = renderForm()

    await user.type(screen.getByLabelText(/Full name/), 'Juan dela Cruz')
    await user.type(screen.getByLabelText(/Email address/), 'juan@example.com')
    await user.type(screen.getByLabelText(/Mobile number/), '0917 123 4567')
    await user.click(screen.getByRole('button', { name: 'Register' }))

    await waitFor(() => expect(onRegistered).toHaveBeenCalledWith(ticket))
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/public/events/1/registrations')
    expect(JSON.parse(init.body)).toEqual({
      fullName: 'Juan dela Cruz',
      email: 'juan@example.com',
      mobile: '09171234567',
      joinWaitlist: false,
    })
    expect(init.headers['X-XSRF-TOKEN']).toBe('test-token')
  })

  it('shows a friendly message for a duplicate registration', async () => {
    mockFetch(409, {
      code: 'DUPLICATE_REGISTRATION',
      message: 'This email is already registered for this event.',
      fieldErrors: { email: 'This email is already registered for this event.' },
    })
    const { user, onRegistered } = renderForm()

    await user.type(screen.getByLabelText(/Full name/), 'Juan dela Cruz')
    await user.type(screen.getByLabelText(/Email address/), 'juan@example.com')
    await user.type(screen.getByLabelText(/Mobile number/), '09171234567')
    await user.click(screen.getByRole('button', { name: 'Register' }))

    expect(await screen.findByText('You are already registered')).toBeInTheDocument()
    expect(screen.getByText('This email is already registered for this event.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Find my ticket' })).toHaveAttribute('href', '/tickets/find')
    expect(onRegistered).not.toHaveBeenCalled()
  })

  it('switches to waitlist mode when the last seat is taken during submission', async () => {
    mockFetch(409, { code: 'EVENT_FULL', message: 'Sorry, this event is now full.' })
    const { user } = renderForm()

    await user.type(screen.getByLabelText(/Full name/), 'Juan dela Cruz')
    await user.type(screen.getByLabelText(/Email address/), 'juan@example.com')
    await user.type(screen.getByLabelText(/Mobile number/), '09171234567')
    await user.click(screen.getByRole('button', { name: 'Register' }))

    expect(await screen.findByText('The last spot was just taken')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Join the waitlist' })).toBeInTheDocument()
  })
})
