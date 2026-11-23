import { describe, expect, it } from 'vitest'
import { formatDateTime, formatMobile, formatTime, formatTimeRange, spotsLabel } from './format'
import { normalizeMobile, registrationSchema } from './validation'

describe('Cebu time formatting', () => {
  it('always shows Asia/Manila time with a 12-hour clock', () => {
    // 10:00 UTC is 6:00 PM in Cebu, regardless of the test machine's time zone.
    expect(formatTime('2026-11-23T10:00:00Z')).toBe('6:00 PM')
    expect(formatTimeRange('2026-11-23T01:00:00Z', '2026-11-23T13:00:00Z')).toBe('9:00 AM – 9:00 PM')
    expect(formatDateTime('2026-11-22T16:30:00Z')).toContain('Nov 23, 2026')
  })

  it('formats seats and mobile numbers', () => {
    expect(spotsLabel(12, 30)).toBe('12 of 30 spots left')
    expect(formatMobile('09171234567')).toBe('0917 123 4567')
  })
})

describe('registration validation', () => {
  it('accepts a valid Philippine mobile number with spaces or dashes', () => {
    expect(normalizeMobile('0917-123 4567')).toBe('09171234567')
    const parsed = registrationSchema.safeParse({ fullName: 'Ana', email: 'ana@example.com', mobile: '0917 123 4567' })
    expect(parsed.success).toBe(true)
  })

  it.each(['0917123456', '091712345678', '08171234567', '0917abc4567'])('rejects mobile %s', (mobile) => {
    const parsed = registrationSchema.safeParse({ fullName: 'Ana', email: 'ana@example.com', mobile })
    expect(parsed.success).toBe(false)
  })

  it.each(['ana', 'ana@', 'ana@example', 'ana example@x.com'])('rejects email %s', (email) => {
    const parsed = registrationSchema.safeParse({ fullName: 'Ana', email, mobile: '09171234567' })
    expect(parsed.success).toBe(false)
  })
})
