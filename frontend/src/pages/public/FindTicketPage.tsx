import { zodResolver } from '@hookform/resolvers/zod'
import { Search } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { Button } from '../../components/ui/Button'
import { Alert } from '../../components/ui/Feedback'
import { TextField } from '../../components/ui/Field'
import { api, ApiError } from '../../lib/api'
import type { Ticket } from '../../lib/types'
import { useDocumentTitle } from '../../lib/useDocumentTitle'
import { lookupSchema, type LookupValues } from '../../lib/validation'

export function FindTicketPage() {
  useDocumentTitle('Find my ticket')
  const navigate = useNavigate()
  const [failure, setFailure] = useState<string>()
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LookupValues>({ resolver: zodResolver(lookupSchema), defaultValues: { ticketCode: '', contact: '' } })

  const onSubmit = handleSubmit(async (values) => {
    setFailure(undefined)
    try {
      const ticket = await api.post<Ticket>('/api/public/tickets/lookup', values)
      navigate(`/tickets/${ticket.accessToken}`)
    } catch (e) {
      // Any mismatch gets the same answer; nothing about the ticket is revealed.
      setFailure(e instanceof ApiError && e.status !== 404 && e.status !== 400 ? e.message : 'Ticket not found.')
    }
  })

  return (
    <div className="mx-auto max-w-md px-4 py-8 sm:py-14">
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Find my ticket</h1>
      <p className="mt-1 text-slate-600">Enter your ticket code and the email or mobile number you registered with.</p>

      <form onSubmit={onSubmit} noValidate className="mt-6 space-y-5 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        {failure && (
          <Alert tone="error" title={failure}>
            {failure === 'Ticket not found.' && 'Check the ticket code and the email or mobile number, then try again.'}
          </Alert>
        )}
        <TextField
          label="Ticket code"
          placeholder="CDM-7K3P9-Q2XHM"
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          className="font-mono uppercase"
          required
          error={errors.ticketCode?.message}
          {...register('ticketCode')}
        />
        <TextField
          label="Email or mobile number"
          placeholder="juan@example.com or 09171234567"
          autoComplete="email"
          autoCapitalize="none"
          spellCheck={false}
          required
          error={errors.contact?.message}
          {...register('contact')}
        />
        <Button type="submit" size="lg" className="w-full" loading={isSubmitting} icon={<Search className="size-5" aria-hidden />}>
          Find ticket
        </Button>
      </form>
    </div>
  )
}
