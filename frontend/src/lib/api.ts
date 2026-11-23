// Minimal fetch wrapper for the same-origin EventPass API.
// - Sends the session cookie (same origin) and the CSRF double-submit header on state-changing calls.
// - Normalizes failures into ApiError with the server's safe message and per-field errors.

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

export const UNAUTHORIZED_EVENT = 'eventpass:unauthorized'
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((part) => part.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

async function ensureCsrfToken(): Promise<string | undefined> {
  let token = readCookie(CSRF_COOKIE)
  if (!token) {
    await fetch('/api/auth/csrf', { credentials: 'same-origin' })
    token = readCookie(CSRF_COOKIE)
  }
  return token ? decodeURIComponent(token) : undefined
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export async function apiRequest<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    const token = await ensureCsrfToken()
    if (token) headers[CSRF_HEADER] = token
  }

  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK', 'Unable to reach the server. Check your connection and try again.')
  }

  if (response.status === 204) return undefined as T
  const text = await response.text()
  const data = text ? safeJson(text) : undefined

  if (!response.ok) {
    if (response.status === 401 && path.startsWith('/api/staff')) {
      // Session expired or signed out elsewhere: let the auth layer send the user to sign-in.
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    }
    const error = (data ?? {}) as { code?: string; message?: string; fieldErrors?: Record<string, string> }
    throw new ApiError(
      response.status,
      error.code ?? 'HTTP_' + response.status,
      error.message ?? defaultMessage(response.status),
      error.fieldErrors ?? {},
    )
  }
  return data as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

function defaultMessage(status: number): string {
  if (status === 401) return 'Please sign in to continue.'
  if (status === 403) return 'You do not have permission to perform this action.'
  if (status === 404) return 'Not found.'
  if (status === 429) return 'Too many attempts. Please wait a minute and try again.'
  return 'Something went wrong. Please try again.'
}

export const api = {
  get: <T>(path: string) => apiRequest<T>('GET', path),
  post: <T>(path: string, body?: unknown) => apiRequest<T>('POST', path, body ?? {}),
  put: <T>(path: string, body: unknown) => apiRequest<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => apiRequest<T>('PATCH', path, body),
}
