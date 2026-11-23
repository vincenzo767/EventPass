import { expect, test } from '@playwright/test'
import { apiLogin, apiPost, checkInCode, createTodayEvent, DOOR, registerViaApi, signIn, todayInManila, uid } from './helpers'

/** Level 2 (and role) acceptance checks. */

test('draft events are hidden until published', async ({ page }) => {
  const title = `Draft Event ${uid()}`
  await signIn(page)
  await page.goto('/staff/events/new')
  await page.getByLabel(/Title/).fill(title)
  await page.getByLabel(/Description/).fill('Hidden until published.')
  await page.getByLabel(/Venue/).fill('Cebu IT Park')
  await page.getByLabel(/^Date/).fill(todayInManila())
  await page.getByLabel(/Start time/).fill('00:00')
  await page.getByLabel(/End time/).fill('23:59')
  await page.getByLabel(/Capacity/).fill('20')
  await page.getByLabel(/Status/).selectOption('DRAFT')
  await page.getByRole('button', { name: 'Create event' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()

  const publicPage = await page.context().browser()!.newPage()
  await publicPage.goto('/')
  await expect(publicPage.getByRole('heading', { name: 'Upcoming events' })).toBeVisible()
  await expect(publicPage.getByText(title)).toHaveCount(0)

  await page.getByRole('button', { name: 'Publish' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Publish' }).click()
  await expect(page.getByText('Event published.')).toBeVisible()

  await publicPage.reload()
  await expect(publicPage.getByText(title)).toBeVisible()
  await publicPage.close()
})

test('end time before start time is blocked', async ({ page }) => {
  await signIn(page)
  await page.goto('/staff/events/new')
  await page.getByLabel(/Title/).fill('Backwards Event')
  await page.getByLabel(/Description/).fill('x')
  await page.getByLabel(/Venue/).fill('x')
  await page.getByLabel(/^Date/).fill(todayInManila())
  await page.getByLabel(/Start time/).fill('18:00')
  await page.getByLabel(/End time/).fill('17:00')
  await page.getByRole('button', { name: 'Create event' }).click()
  await expect(page.getByText('End time must be after the start time.')).toBeVisible()
})

test('capacity cannot be lowered below the number registered', async ({ page, request }) => {
  const event = await createTodayEvent(request, `Capacity Test ${uid()}`, 5)
  for (let i = 0; i < 3; i++) await registerViaApi(request, event.id, `Guest ${i}`, `cap${i}.${uid()}@example.com`)

  await signIn(page)
  await page.goto(`/staff/events/${event.id}/edit`)
  await page.getByLabel(/Capacity/).fill('2')
  await page.getByRole('button', { name: 'Save changes' }).click()
  await expect(page.getByText('Capacity cannot be lower than the 3 attendees already registered.')).toBeVisible()
})

test('waitlist: join, scan shows not confirmed, cancel frees a spot, promote, then check in', async ({ page, request }) => {
  const event = await createTodayEvent(request, `Waitlist Test ${uid()}`, 1)
  const confirmed = await registerViaApi(request, event.id, 'Seat Holder', `seat.${uid()}@example.com`)

  // Join the waitlist through the UI.
  await page.goto(`/events/${event.id}`)
  await expect(page.getByRole('button', { name: 'Register' })).toBeDisabled()
  await page.getByRole('link', { name: 'Join the waitlist' }).click()
  await page.getByLabel(/Full name/).fill('Wanda Waitlist')
  await page.getByLabel(/Email address/).fill(`wanda.${uid()}@example.com`)
  await page.getByLabel(/Mobile number/).fill('09171234567')
  await page.getByRole('button', { name: 'Join the waitlist' }).click()
  await expect(page.getByText('You are #1 on the waitlist')).toBeVisible()
  await expect(page.getByRole('img', { name: /QR code/ })).toHaveCount(0)
  const waitlistCode = (await page.getByTestId('ticket-code').textContent())!.trim()

  await signIn(page)
  await page.goto(`/staff/check-in/${event.id}`)
  let result = await checkInCode(page, waitlistCode)
  await expect(result).toContainText('On waitlist — not confirmed')

  // Cancel the confirmed registration: spots left goes up and its scan shows "Cancelled".
  await page.goto(`/staff/events/${event.id}`)
  await page.getByRole('button', { name: 'Cancel registration for Seat Holder' }).first().click()
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel registration' }).click()
  await expect(page.getByText(/One spot was freed/)).toBeVisible()
  const publicEvent = await (await request.get(`/api/public/events/${event.id}`)).json()
  expect(publicEvent.spotsLeft).toBe(1)

  // Promote the first person on the waitlist.
  await page.getByRole('button', { name: 'Promote next in line' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Promote' }).click()
  await expect(page.getByText(/Wanda Waitlist was promoted/)).toBeVisible()

  await page.goto(`/staff/check-in/${event.id}`)
  result = await checkInCode(page, confirmed.ticketCode)
  await expect(result).toContainText('Cancelled')
  result = await checkInCode(page, waitlistCode)
  await expect(result).toHaveAttribute('data-result', 'CHECKED_IN')
  await expect(result).toContainText('Wanda Waitlist')
})

test('checked-in registrations cannot be cancelled; undo check-in works and is recorded', async ({ page, request }) => {
  const event = await createTodayEvent(request, `Undo Test ${uid()}`, 5)
  const ticket = await registerViaApi(request, event.id, 'Undo Person', `undo.${uid()}@example.com`)

  await signIn(page)
  await page.goto(`/staff/check-in/${event.id}`)
  await expect(await checkInCode(page, ticket.ticketCode)).toHaveAttribute('data-result', 'CHECKED_IN')

  // Attendee self-cancel is refused for a checked-in ticket.
  const cancel = await apiPost(request, `/api/public/tickets/${ticket.accessToken}/cancel`, {})
  expect(cancel.status()).toBe(409)

  await page.goto(`/staff/events/${event.id}`)
  await expect(page.getByRole('button', { name: 'Cancel registration for Undo Person' })).toHaveCount(0)
  await page.getByRole('button', { name: 'Undo check-in for Undo Person' }).first().click()
  await page.getByRole('dialog').getByRole('button', { name: 'Undo check-in' }).click()
  await expect(page.getByText(/Check-in for Undo Person was undone/)).toBeVisible()
  await expect(page.getByText(/Check-in undone .* by Maria Santos/).filter({ visible: true }).first()).toBeVisible()

  await page.goto('/staff/audit')
  await expect(page.getByText(/Undid check-in of .*Undo Person/).first()).toBeVisible()

  await page.goto(`/staff/check-in/${event.id}`)
  await expect(await checkInCode(page, ticket.ticketCode)).toHaveAttribute('data-result', 'CHECKED_IN')
})

test('dashboard counts match the attendee list and CSV export downloads', async ({ page, request }) => {
  const event = await createTodayEvent(request, `Dashboard Test ${uid()}`, 2)
  await registerViaApi(request, event.id, 'Dash One', `d1.${uid()}@example.com`)
  await registerViaApi(request, event.id, 'Dash Two', `d2.${uid()}@example.com`)
  await registerViaApi(request, event.id, 'Dash Three', `d3.${uid()}@example.com`, true)

  await signIn(page)
  await page.goto('/staff')
  const card = page.locator('section', { has: page.getByRole('heading', { name: event.title }) })
  await expect(card.getByText('0 / 2 attendees checked in')).toBeVisible()
  await expect(card.locator('dl').last()).toContainText('Waitlisted1')

  await page.goto(`/staff/events/${event.id}`)
  await expect(page.getByTestId('registered-total')).toHaveText('2 / 2')
  const download = page.waitForEvent('download')
  await page.getByRole('link', { name: 'Export CSV' }).click()
  const file = await download
  expect(file.suggestedFilename()).toMatch(/attendees.*\.csv$/)
})

test('door staff cannot create or edit events; organizers can', async ({ page, request }) => {
  await signIn(page, DOOR)
  await expect(page.getByRole('link', { name: 'New event' })).toHaveCount(0)
  await page.goto('/staff/events/new')
  await expect(page.getByText('Organizer access required')).toBeVisible()

  // The server enforces it too.
  await apiLogin(request, DOOR)
  const response = await apiPost(request, '/api/staff/events', {
    title: 'Not allowed', description: 'x', venue: 'x', date: todayInManila(), startTime: '10:00', endTime: '11:00',
    capacity: 5, status: 'DRAFT',
  })
  expect(response.status()).toBe(403)
})
