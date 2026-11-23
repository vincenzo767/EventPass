// All dates and times are shown in Cebu time (Asia/Manila, UTC+8) using a 12-hour clock,
// regardless of the viewer's device time zone.

export const EVENT_TIME_ZONE = 'Asia/Manila'

const dateLong = new Intl.DateTimeFormat('en-PH', {
  timeZone: EVENT_TIME_ZONE,
  weekday: 'long',
  month: 'long',
  day: 'numeric',
  year: 'numeric',
})

const dateShort = new Intl.DateTimeFormat('en-PH', {
  timeZone: EVENT_TIME_ZONE,
  weekday: 'short',
  month: 'short',
  day: 'numeric',
  year: 'numeric',
})

const time = new Intl.DateTimeFormat('en-US', {
  timeZone: EVENT_TIME_ZONE,
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
})

const dateTime = new Intl.DateTimeFormat('en-US', {
  timeZone: EVENT_TIME_ZONE,
  month: 'short',
  day: 'numeric',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
})

const dateTimeCompact = new Intl.DateTimeFormat('en-US', {
  timeZone: EVENT_TIME_ZONE,
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
})

const dayParts = new Intl.DateTimeFormat('en-US', {
  timeZone: EVENT_TIME_ZONE,
  month: 'short',
  day: 'numeric',
})

export function formatDateLong(iso: string): string {
  return dateLong.format(new Date(iso))
}

export function formatDateShort(iso: string): string {
  return dateShort.format(new Date(iso))
}

/** e.g. "6:00 PM" */
export function formatTime(iso: string): string {
  return time.format(new Date(iso))
}

/** e.g. "9:00 AM – 9:00 PM" */
export function formatTimeRange(startIso: string, endIso: string): string {
  return `${formatTime(startIso)} – ${formatTime(endIso)}`
}

/** e.g. "Nov 23, 2026, 6:42 PM" */
export function formatDateTime(iso: string | undefined | null): string {
  return iso ? dateTime.format(new Date(iso)) : '—'
}

/** e.g. "Nov 23, 6:42 PM" — for dense tables where the year is obvious. */
export function formatDateTimeCompact(iso: string | undefined | null): string {
  return iso ? dateTimeCompact.format(new Date(iso)) : '—'
}

/** Month abbreviation and day number for calendar-style date badges. */
export function dateBadge(iso: string): { month: string; day: string } {
  const parts = dayParts.formatToParts(new Date(iso))
  return {
    month: parts.find((p) => p.type === 'month')?.value.toUpperCase() ?? '',
    day: parts.find((p) => p.type === 'day')?.value ?? '',
  }
}

/** "12 of 30 spots left" */
export function spotsLabel(spotsLeft: number, capacity: number): string {
  return `${spotsLeft} of ${capacity} ${capacity === 1 ? 'spot' : 'spots'} left`
}

/** "0917 123 4567" */
export function formatMobile(mobile: string): string {
  return mobile.length === 11 ? `${mobile.slice(0, 4)} ${mobile.slice(4, 7)} ${mobile.slice(7)}` : mobile
}

export function plural(count: number, singular: string, pluralForm = singular + 's'): string {
  return `${count} ${count === 1 ? singular : pluralForm}`
}
