import { expect, test } from '@playwright/test'
import { checkInCode, createTodayEvent, registerViaApi, signIn, uid } from './helpers'

/** Level 1 acceptance checks from the CubeTech assessment, run in a real browser. */

test('register for Cebu Dev Meetup 2026 and see a ticket with a QR code; refresh and find it again', async ({ page }) => {
  const email = `e2e.${uid()}@example.com`

  await page.goto('/')
  await page.getByRole('link', { name: /Cebu Dev Meetup 2026/ }).click()
  await expect(page.getByRole('heading', { name: 'Cebu Dev Meetup 2026' })).toBeVisible()
  await expect(page.getByText('Ayala Center Cebu, Function Room 2').first()).toBeVisible()
  await page.getByRole('link', { name: 'Register' }).click()

  await page.getByLabel(/Full name/).fill('Playwright Tester')
  await page.getByLabel(/Email address/).fill(email)
  await page.getByLabel(/Mobile number/).fill('09171234567')
  await page.getByRole('button', { name: 'Register' }).click()

  await expect(page.getByText("You're registered!")).toBeVisible()
  const qr = page.getByRole('img', { name: /QR code for ticket/ })
  await expect(qr).toBeVisible()
  expect(await qr.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(100)
  const code = (await page.getByTestId('ticket-code').textContent())!.trim()
  expect(code).toMatch(/^CDM-[A-Z2-9]{5}-[A-Z2-9]{5}$/)
  await expect(page.getByText('Playwright Tester')).toBeVisible()

  // Refresh: the ticket page reloads from the server.
  await page.reload()
  await expect(page.getByTestId('ticket-code')).toHaveText(code)

  // Find it again with ticket code + email.
  await page.goto('/tickets/find')
  await page.getByLabel('Ticket code').fill(code)
  await page.getByLabel('Email or mobile number').fill(email)
  await page.getByRole('button', { name: 'Find ticket' }).click()
  await expect(page.getByTestId('ticket-code')).toHaveText(code)
  await expect(page.getByRole('img', { name: /QR code for ticket/ })).toBeVisible()
})

test('wrong ticket code or wrong email shows "Ticket not found" and no details', async ({ page, request }) => {
  const event = await createTodayEvent(request, `Lookup Test ${uid()}`, 10)
  const email = `owner.${uid()}@example.com`
  const ticket = await registerViaApi(request, event.id, 'Secret Owner', email)

  for (const [code, contact] of [
    [ticket.ticketCode, 'someone.else@example.com'],
    ['CDM-23456-23456', email],
  ]) {
    await page.goto('/tickets/find')
    await page.getByLabel('Ticket code').fill(code)
    await page.getByLabel('Email or mobile number').fill(contact)
    await page.getByRole('button', { name: 'Find ticket' }).click()
    await expect(page.getByText('Ticket not found.')).toBeVisible()
    await expect(page.getByText('Secret Owner')).toHaveCount(0)
    await expect(page.getByRole('img', { name: /QR code/ })).toHaveCount(0)
  }
})

test('the same email cannot register twice for one event', async ({ page, request }) => {
  const event = await createTodayEvent(request, `Duplicate Test ${uid()}`, 10)
  const email = `dup.${uid()}@example.com`
  await registerViaApi(request, event.id, 'First Try', email)

  await page.goto(`/events/${event.id}/register`)
  await page.getByLabel(/Full name/).fill('Second Try')
  await page.getByLabel(/Email address/).fill(email.toUpperCase())
  await page.getByLabel(/Mobile number/).fill('09181234567')
  await page.getByRole('button', { name: 'Register' }).click()
  await expect(page.getByText('You are already registered')).toBeVisible()
})

test('a bad email and a 10-digit mobile number both show errors', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: /Cebu Dev Meetup 2026/ }).click()
  await page.getByRole('link', { name: 'Register' }).click()
  await page.getByLabel(/Full name/).fill('Bad Input')
  await page.getByLabel(/Email address/).fill('bad-email@nowhere')
  await page.getByLabel(/Mobile number/).fill('0917123456')
  await page.getByRole('button', { name: 'Register' }).click()
  await expect(page.getByText(/Enter a valid email address/)).toBeVisible()
  await expect(page.getByText(/Enter an 11-digit mobile number starting with 09/)).toBeVisible()
})

test('the seeded full event shows "Full" and the Register button is disabled', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: /Mactan Cloud & AI Summit/ }).click()
  await expect(page.getByText('Full', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: 'Register' })).toBeDisabled()
  await expect(page.getByRole('link', { name: 'Join the waitlist' })).toBeVisible()
})

test('opening a staff page while signed out goes to the sign-in page', async ({ page }) => {
  await page.goto('/staff/check-in')
  await expect(page).toHaveURL(/\/staff\/login/)
  await expect(page.getByRole('heading', { name: 'Staff sign in' })).toBeVisible()
})

test('check-in: valid, already checked in, made-up code, other event; attendee totals', async ({ page, request }) => {
  const event = await createTodayEvent(request, `Door Test ${uid()}`, 10)
  const other = await createTodayEvent(request, `Other Event ${uid()}`, 10)
  const ticket = await registerViaApi(request, event.id, 'Maria Clara', `maria.${uid()}@example.com`)
  await registerViaApi(request, event.id, 'Second Guest', `guest.${uid()}@example.com`)
  const otherTicket = await registerViaApi(request, other.id, 'Other Person', `other.${uid()}@example.com`)

  await signIn(page)
  await page.goto(`/staff/check-in/${event.id}`)
  await expect(page.getByTestId('live-counter')).toHaveText(/0 \/ 2/)

  let result = await checkInCode(page, ticket.ticketCode)
  await expect(result).toHaveAttribute('data-result', 'CHECKED_IN')
  await expect(result).toContainText('Checked in')
  await expect(result).toContainText('Maria Clara')
  await expect(page.getByTestId('live-counter')).toHaveText(/1 \/ 2/)

  result = await checkInCode(page, ticket.ticketCode)
  await expect(result).toHaveAttribute('data-result', 'ALREADY_CHECKED_IN')
  await expect(result).toContainText('Already checked in')
  await expect(result).toContainText('First checked in')

  result = await checkInCode(page, 'CDM-000001')
  await expect(result).toHaveAttribute('data-result', 'INVALID')
  await expect(result).toContainText('Invalid ticket')

  result = await checkInCode(page, otherTicket.ticketCode)
  await expect(result).toHaveAttribute('data-result', 'INVALID')
  await expect(result).not.toContainText('Other Person')

  await page.goto(`/staff/events/${event.id}`)
  await expect(page.getByTestId('registered-total')).toHaveText('2 / 10')
  await expect(page.getByTestId('checked-in-total')).toHaveText('1 / 2')
})
