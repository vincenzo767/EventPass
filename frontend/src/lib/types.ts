// Shapes of the EventPass REST API (see backend DTOs).

export type Availability = 'OPEN' | 'FULL' | 'CLOSED' | 'ENDED'
export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'
export type RegistrationStatus = 'CONFIRMED' | 'CHECKED_IN' | 'WAITLISTED' | 'CANCELLED'
export type StaffRole = 'ORGANIZER' | 'DOOR_STAFF'

export interface PublicEvent {
  id: number
  title: string
  description: string
  venue: string
  startsAt: string
  endsAt: string
  capacity: number
  spotsLeft: number
  availability: Availability
  registrationOpen: boolean
  waitlistOpen: boolean
  happeningNow: boolean
}

export interface EventStats {
  capacity: number
  registered: number
  confirmed: number
  checkedIn: number
  waitlisted: number
  cancelled: number
  spotsLeft: number
  /** Only present once the event has started. */
  noShows?: number
}

export interface StaffEvent {
  id: number
  title: string
  description: string
  venue: string
  startsAt: string
  endsAt: string
  date: string
  startTime: string
  endTime: string
  capacity: number
  status: EventStatus
  ticketPrefix: string
  availability: Availability
  started: boolean
  ended: boolean
  eventDay: boolean
  stats: EventStats
  updatedAt: string
}

export interface Ticket {
  ticketCode: string
  accessToken: string
  status: RegistrationStatus
  attendeeName: string
  maskedEmail: string
  registeredAt: string
  checkedInAt?: string
  cancelledAt?: string
  waitlistPosition?: number
  cancellable: boolean
  event: { id: number; title: string; venue: string; startsAt: string; endsAt: string }
  emailQueued?: boolean
}

export interface Attendee {
  id: number
  fullName: string
  email: string
  mobile: string
  ticketCode: string
  status: RegistrationStatus
  registeredAt: string
  checkedInAt?: string
  checkedInBy?: string
  checkInUndoneAt?: string
  checkInUndoneBy?: string
  cancelledAt?: string
  cancelledBy?: string
  waitlistPosition?: number
}

export interface AttendeeList {
  eventId: number
  eventTitle: string
  totals: EventStats
  attendees: Attendee[]
}

export type CheckInOutcome =
  | 'CHECKED_IN'
  | 'ALREADY_CHECKED_IN'
  | 'INVALID'
  | 'WAITLISTED'
  | 'CANCELLED'
  | 'NOT_EVENT_DAY'

export interface CheckInResponse {
  result?: CheckInOutcome
  label?: string
  tone?: 'SUCCESS' | 'WARNING' | 'ERROR'
  message: string
  registrationId?: number
  attendeeName?: string
  ticketCode?: string
  checkedInAt?: string
  stats: { checkedIn: number; registered: number; capacity: number }
}

export interface StaffMember {
  id: number
  name: string
  username: string
  role: StaffRole
}

export interface StaffAccount extends StaffMember {
  active: boolean
  createdAt: string
}

export interface AuditEntry {
  id: number
  action: string
  targetType: 'EVENT' | 'REGISTRATION' | 'STAFF_USER'
  targetId: number
  details?: string
  createdAt: string
  staffUserId: number
  staffName: string
  staffUsername?: string
}

export interface AuditPage {
  items: AuditEntry[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}
