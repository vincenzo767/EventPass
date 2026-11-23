import { ArrowLeft, Save } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { PageHeader } from '../../components/StaffLayout'
import { Button } from '../../components/ui/Button'
import { Alert, LoadingBlock } from '../../components/ui/Feedback'
import { SelectField, TextAreaField, TextField } from '../../components/ui/Field'
import { api, ApiError } from '../../lib/api'
import type { EventStatus, StaffEvent } from '../../lib/types'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

interface FormValues {
  title: string
  description: string
  venue: string
  date: string
  startTime: string
  endTime: string
  capacity: string
  status: EventStatus
  ticketPrefix: string
}

type Errors = Partial<Record<keyof FormValues | 'form', string>>

const empty: FormValues = {
  title: '',
  description: '',
  venue: '',
  date: '',
  startTime: '18:00',
  endTime: '21:00',
  capacity: '30',
  status: 'DRAFT',
  ticketPrefix: '',
}

/** Client-side checks mirror the server rules; the server validates again on save. */
export function validateEvent(values: FormValues): Errors {
  const errors: Errors = {}
  if (!values.title.trim()) errors.title = 'Title is required.'
  if (!values.description.trim()) errors.description = 'Description is required.'
  if (!values.venue.trim()) errors.venue = 'Venue is required.'
  if (!values.date) errors.date = 'Event date is required.'
  if (!values.startTime) errors.startTime = 'Start time is required.'
  if (!values.endTime) errors.endTime = 'End time is required.'
  if (values.startTime && values.endTime && values.endTime <= values.startTime) {
    errors.endTime = 'End time must be after the start time.'
  }
  const capacity = Number(values.capacity)
  if (!values.capacity || !Number.isInteger(capacity) || capacity < 1) errors.capacity = 'Capacity must be at least 1.'
  if (values.ticketPrefix && !/^[A-Za-z]{2,5}$/.test(values.ticketPrefix)) {
    errors.ticketPrefix = 'Ticket prefix must be 2 to 5 letters.'
  }
  return errors
}

export function EventFormPage() {
  const { eventId } = useParams()
  const editing = Boolean(eventId)
  const navigate = useNavigate()
  const [values, setValues] = useState<FormValues>(empty)
  const [original, setOriginal] = useState<StaffEvent>()
  const [errors, setErrors] = useState<Errors>({})
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)
  useDocumentTitle(editing ? 'Edit event' : 'New event')

  useEffect(() => {
    if (!eventId) return
    api
      .get<StaffEvent>(`/api/staff/events/${eventId}`)
      .then((event) => {
        setOriginal(event)
        setValues({
          title: event.title,
          description: event.description,
          venue: event.venue,
          date: event.date,
          startTime: event.startTime,
          endTime: event.endTime,
          capacity: String(event.capacity),
          status: event.status,
          ticketPrefix: event.ticketPrefix,
        })
      })
      .catch((e: unknown) => setErrors({ form: e instanceof ApiError ? e.message : 'Event could not be loaded.' }))
      .finally(() => setLoading(false))
  }, [eventId])

  function set<K extends keyof FormValues>(key: K, value: FormValues[K]) {
    setValues((current) => ({ ...current, [key]: value }))
    setErrors((current) => ({ ...current, [key]: undefined, form: undefined }))
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const clientErrors = validateEvent(values)
    setErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) return

    setSaving(true)
    const body = { ...values, capacity: Number(values.capacity), ticketPrefix: values.ticketPrefix.trim() || null }
    try {
      const saved = editing
        ? await api.put<StaffEvent>(`/api/staff/events/${eventId}`, body)
        : await api.post<StaffEvent>('/api/staff/events', body)
      navigate(`/staff/events/${saved.id}`, { state: { saved: editing ? 'updated' : 'created' } })
    } catch (e) {
      if (e instanceof ApiError) {
        setErrors({ ...(e.fieldErrors as Errors), form: Object.keys(e.fieldErrors).length ? undefined : e.message })
      } else {
        setErrors({ form: 'The event could not be saved.' })
      }
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <LoadingBlock />

  return (
    <>
      <Link to={editing ? `/staff/events/${eventId}` : '/staff/events'} className="mb-4 inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900">
        <ArrowLeft className="size-4" aria-hidden /> Back
      </Link>
      <PageHeader
        title={editing ? 'Edit event' : 'New event'}
        description="Dates and times are in Cebu time (Asia/Manila, UTC+8)."
      />

      <form onSubmit={onSubmit} noValidate className="max-w-3xl space-y-6 rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
        {errors.form && <Alert tone="error">{errors.form}</Alert>}

        <TextField label="Title" required maxLength={150} value={values.title} error={errors.title} onChange={(e) => set('title', e.target.value)} />
        <TextAreaField
          label="Description"
          required
          rows={4}
          maxLength={5000}
          value={values.description}
          error={errors.description}
          onChange={(e) => set('description', e.target.value)}
        />
        <TextField label="Venue" required maxLength={200} value={values.venue} error={errors.venue} onChange={(e) => set('venue', e.target.value)} />

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Date" type="date" required value={values.date} error={errors.date} onChange={(e) => set('date', e.target.value)} />
          <TextField label="Start time" type="time" required value={values.startTime} error={errors.startTime} onChange={(e) => set('startTime', e.target.value)} />
          <TextField label="End time" type="time" required value={values.endTime} error={errors.endTime} onChange={(e) => set('endTime', e.target.value)} />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField
            label="Capacity"
            type="number"
            inputMode="numeric"
            min={1}
            required
            value={values.capacity}
            error={errors.capacity}
            hint={original ? `${original.stats.registered} currently registered` : undefined}
            onChange={(e) => set('capacity', e.target.value)}
          />
          <SelectField label="Status" required value={values.status} error={errors.status} onChange={(e) => set('status', e.target.value as EventStatus)}>
            <option value="DRAFT">Draft (hidden from attendees)</option>
            <option value="PUBLISHED">Published (open for registration)</option>
            <option value="CLOSED">Closed (no new registrations)</option>
          </SelectField>
          <TextField
            label="Ticket code prefix"
            maxLength={5}
            value={values.ticketPrefix}
            error={errors.ticketPrefix}
            hint="Optional, e.g. CDM. Derived from the title if empty."
            className="uppercase"
            onChange={(e) => set('ticketPrefix', e.target.value.toUpperCase())}
          />
        </div>

        <div className="flex flex-col-reverse gap-2 border-t border-slate-200 pt-5 sm:flex-row sm:justify-end">
          <Link to={editing ? `/staff/events/${eventId}` : '/staff/events'} className="inline-flex h-10 items-center justify-center rounded-lg px-4 text-sm font-medium text-slate-700 hover:bg-slate-100">
            Cancel
          </Link>
          <Button type="submit" loading={saving} icon={<Save className="size-4" aria-hidden />}>
            {editing ? 'Save changes' : 'Create event'}
          </Button>
        </div>
      </form>
    </>
  )
}
