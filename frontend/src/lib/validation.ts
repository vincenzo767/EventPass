import { z } from 'zod'

// Mirrors the backend rules in RegistrationDtos.java. The server validates again on every request.
export const EMAIL_PATTERN =
  /^[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\.[A-Za-z]{2,}$/
export const MOBILE_PATTERN = /^09\d{9}$/

/** Removes spaces and dashes people commonly type in phone numbers ("0917 123 4567"). */
export function normalizeMobile(value: string): string {
  return value.replace(/[\s-]/g, '')
}

export const registrationSchema = z.object({
  fullName: z
    .string()
    .trim()
    .min(1, 'Full name is required.')
    .min(2, 'Full name must be at least 2 characters.')
    .max(120, 'Full name must be at most 120 characters.'),
  email: z
    .string()
    .trim()
    .min(1, 'Email address is required.')
    .max(254, 'Email address is too long.')
    .regex(EMAIL_PATTERN, 'Enter a valid email address, e.g. juan@example.com.'),
  mobile: z
    .string()
    .transform(normalizeMobile)
    .pipe(
      z
        .string()
        .min(1, 'Mobile number is required.')
        .regex(MOBILE_PATTERN, 'Enter an 11-digit mobile number starting with 09, e.g. 09171234567.'),
    ),
})

export type RegistrationInput = z.input<typeof registrationSchema>
export type RegistrationValues = z.output<typeof registrationSchema>

export const lookupSchema = z.object({
  ticketCode: z.string().trim().min(1, 'Enter your ticket code.'),
  contact: z.string().trim().min(1, 'Enter the email or mobile number you registered with.'),
})

export type LookupValues = z.infer<typeof lookupSchema>
