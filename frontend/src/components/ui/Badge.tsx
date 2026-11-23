import type { ReactNode } from 'react'
import type { Availability, EventStatus, RegistrationStatus } from '../../lib/types'

type Tone = 'neutral' | 'brand' | 'success' | 'warning' | 'danger' | 'muted'

const tones: Record<Tone, string> = {
  neutral: 'bg-slate-100 text-slate-700 ring-slate-200',
  brand: 'bg-brand-50 text-brand-800 ring-brand-200',
  success: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  warning: 'bg-amber-50 text-amber-800 ring-amber-200',
  danger: 'bg-red-50 text-red-800 ring-red-200',
  muted: 'bg-slate-50 text-slate-500 ring-slate-200',
}

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${tones[tone]}`}>
      {children}
    </span>
  )
}

const registrationLabels: Record<RegistrationStatus, [string, Tone]> = {
  CONFIRMED: ['Confirmed', 'brand'],
  CHECKED_IN: ['Checked in', 'success'],
  WAITLISTED: ['Waitlisted', 'warning'],
  CANCELLED: ['Cancelled', 'muted'],
}

export function RegistrationStatusBadge({ status }: { status: RegistrationStatus }) {
  const [label, tone] = registrationLabels[status]
  return <Badge tone={tone}>{label}</Badge>
}

const availabilityLabels: Record<Availability, [string, Tone]> = {
  OPEN: ['Open', 'success'],
  FULL: ['Full', 'danger'],
  CLOSED: ['Registration closed', 'neutral'],
  ENDED: ['Ended', 'muted'],
}

export function AvailabilityBadge({ availability }: { availability: Availability }) {
  const [label, tone] = availabilityLabels[availability]
  return <Badge tone={tone}>{label}</Badge>
}

const eventStatusLabels: Record<EventStatus, [string, Tone]> = {
  DRAFT: ['Draft', 'neutral'],
  PUBLISHED: ['Published', 'brand'],
  CLOSED: ['Closed', 'muted'],
}

export function EventStatusBadge({ status }: { status: EventStatus }) {
  const [label, tone] = eventStatusLabels[status]
  return <Badge tone={tone}>{label}</Badge>
}
