import { expect, type APIRequestContext, type Page } from '@playwright/test'

export const ORGANIZER = {
  username: process.env.E2E_ORGANIZER_USER ?? 'organizer',
  password: process.env.E2E_ORGANIZER_PASSWORD ?? 'EventPass#2026',
}
export const DOOR = {
  username: process.env.E2E_DOOR_USER ?? 'door',
  password: process.env.E2E_DOOR_PASSWORD ?? 'DoorStaff#2026',
}

/** Unique suffix so repeated runs never collide on email addresses. */
export function uid(): string {
  return `${Date.now().toString(36)}${Math.floor(Math.random() * 1e4).toString(36)}`
}

/** Today's date in Cebu time as YYYY-MM-DD. */
export function todayInManila(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date())
}

async function csrf(request: APIRequestContext): Promise<string> {
  await request.get('/api/auth/csrf')
  const state = await request.storageState()
  const token = state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value
  if (!token) throw new Error('No CSRF cookie issued')
  return decodeURIComponent(token)
}

export async function apiPost(request: APIRequestContext, path: string, data: unknown) {
  const token = await csrf(request)
  return request.post(path, { data, headers: { 'X-XSRF-TOKEN': token } })
}

export async function apiLogin(request: APIRequestContext, user = ORGANIZER) {
  const response = await apiPost(request, '/api/auth/login', user)
  expect(response.ok(), await response.text()).toBeTruthy()
}

/** Creates a published event happening today (all day), via the organizer API. */
export async function createTodayEvent(request: APIRequestContext, title: string, capacity: number) {
  await apiLogin(request)
  const response = await apiPost(request, '/api/staff/events', {
    title,
    description: `${title} (created by the end-to-end tests).`,
    venue: 'Ayala Center Cebu, Function Room 2',
    date: todayInManila(),
    startTime: '00:00',
    endTime: '23:59',
    capacity,
    status: 'PUBLISHED',
    ticketPrefix: 'ETE',
  })
  expect(response.status(), await response.text()).toBe(201)
  return (await response.json()) as { id: number; title: string }
}

export async function registerViaApi(request: APIRequestContext, eventId: number, name: string, email: string, joinWaitlist = false) {
  const response = await apiPost(request, `/api/public/events/${eventId}/registrations`, {
    fullName: name,
    email,
    mobile: '09171234567',
    joinWaitlist,
  })
  expect(response.status(), await response.text()).toBe(201)
  return (await response.json()) as { ticketCode: string; accessToken: string; status: string; waitlistPosition?: number }
}

export async function signIn(page: Page, user = ORGANIZER) {
  await page.goto('/staff/login')
  await page.getByLabel('Username').fill(user.username)
  await page.getByLabel('Password').fill(user.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}

export async function checkInCode(page: Page, code: string) {
  const input = page.getByLabel('Enter ticket code')
  await input.fill(code)
  await input.press('Enter')
  return page.getByTestId('check-in-result')
}
