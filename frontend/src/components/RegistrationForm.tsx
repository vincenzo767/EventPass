import { zodResolver } from '@hookform/resolvers/zod'
import { ListPlus, UserPlus } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { api, ApiError } from '../lib/api'
import type { Ticket } from '../lib/types'
import { registrationSchema, type RegistrationInput, type RegistrationValues } from '../lib/validation'
import { Button } from './ui/Button'
import { Alert } from './ui/Feedback'
import { TextField } from './ui/Field'

interface RegistrationFormProps {
  eventId: number
  /** Start in waitlist mode (the event is known to be full). */
  waitlist: boolean
  onRegistered: (ticket: Ticket) => void
}

export function RegistrationForm({ eventId, waitlist: initialWaitlist, onRegistered }: RegistrationFormProps) {
  const [waitlist, setWaitlist] = useState(initialWaitlist)
  const [formError, setFormError] = useState<{ code: string; message: string }>()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegistrationInput, unknown, RegistrationValues>({
    resolver: zodResolver(registrationSchema),
    mode: 'onTouched',
    defaultValues: { fullName: '', email: '', mobile: '' },
  })

  async function submit(values: RegistrationValues, joinWaitlist = waitlist) {
    setFormError(undefined)
    try {
      const ticket = await api.post<Ticket>(`/api/public/events/${eventId}/registrations`, { ...values, joinWaitlist })
      onRegistered(ticket)
    } catch (e) {
      if (!(e instanceof ApiError)) throw e
      // Server-side validation errors are shown beside the matching field.
      for (const [field, message] of Object.entries(e.fieldErrors)) {
        if (field === 'fullName' || field === 'email' || field === 'mobile') {
          setError(field, { type: 'server', message })
        }
      }
      if (e.code === 'EVENT_FULL') setWaitlist(true)
      setFormError({ code: e.code, message: e.message })
    }
  }

  const onSubmit = handleSubmit((values) => submit(values))

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5" aria-label={waitlist ? 'Join the waitlist' : 'Registration form'}>
      {formError && <FormErrorAlert code={formError.code} message={formError.message} />}

      {waitlist && formError?.code !== 'EVENT_FULL' && (
        <Alert tone="warning" title="This event is full">
          You are joining the waitlist. Your registration will <strong>not be confirmed</strong> unless a spot opens and an
          organizer moves you up. You will see your waitlist position after submitting.
        </Alert>
      )}

      <TextField
        label="Full name"
        autoComplete="name"
        required
        error={errors.fullName?.message}
        placeholder="Juan dela Cruz"
        {...register('fullName')}
      />
      <TextField
        label="Email address"
        type="email"
        inputMode="email"
        autoComplete="email"
        autoCapitalize="none"
        spellCheck={false}
        required
        error={errors.email?.message}
        placeholder="juan@example.com"
        hint="You will use this email to find your ticket again."
        {...register('email')}
      />
      <TextField
        label="Mobile number"
        type="tel"
        inputMode="numeric"
        autoComplete="tel-national"
        required
        error={errors.mobile?.message}
        placeholder="09171234567"
        hint="11 digits, starting with 09."
        maxLength={13}
        {...register('mobile')}
      />

      <Button
        type="submit"
        size="lg"
        className="w-full"
        loading={isSubmitting}
        icon={waitlist ? <ListPlus className="size-5" aria-hidden /> : <UserPlus className="size-5" aria-hidden />}
      >
        {waitlist ? 'Join the waitlist' : 'Register'}
      </Button>
      <p className="text-center text-xs text-slate-500">
        Free event. We only use your details to manage your registration.
      </p>
    </form>
  )
}

function FormErrorAlert({ code, message }: { code: string; message: string }) {
  if (code === 'DUPLICATE_REGISTRATION') {
    return (
      <Alert tone="warning" title="You are already registered">
        This email already has a registration for this event.{' '}
        <Link to="/tickets/find" className="font-semibold underline">
          Find my ticket
        </Link>
      </Alert>
    )
  }
  if (code === 'EVENT_FULL') {
    return (
      <Alert tone="warning" title="The last spot was just taken">
        This event filled up while you were registering. You can still join the waitlist below; your registration will
        not be confirmed unless a spot opens.
      </Alert>
    )
  }
  if (code === 'VALIDATION_FAILED') {
    return <Alert tone="error">Please correct the highlighted fields.</Alert>
  }
  return <Alert tone="error">{message}</Alert>
}
