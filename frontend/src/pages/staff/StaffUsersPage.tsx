import { UserPlus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useStaff } from '../../auth/AuthContext'
import { PageHeader } from '../../components/StaffLayout'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Alert, LoadingBlock } from '../../components/ui/Feedback'
import { SelectField, TextField } from '../../components/ui/Field'
import { api, ApiError } from '../../lib/api'
import { formatDateShort } from '../../lib/format'
import type { StaffAccount, StaffRole } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

const emptyForm = { name: '', username: '', password: '', role: 'DOOR_STAFF' as StaffRole }

export function StaffUsersPage() {
  useDocumentTitle('Staff')
  const me = useStaff()
  const { data, error, loading, reload } = useAsync(() => api.get<StaffAccount[]>('/api/staff/users'), [])
  const [form, setForm] = useState(emptyForm)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>()

  async function create(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setErrors({})
    try {
      const created = await api.post<StaffAccount>('/api/staff/users', form)
      setNotice({ tone: 'success', text: `Account "${created.username}" created.` })
      setForm(emptyForm)
      await reload()
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length) setErrors(e.fieldErrors)
      else setNotice({ tone: 'error', text: e instanceof ApiError ? e.message : 'Account could not be created.' })
    } finally {
      setSaving(false)
    }
  }

  async function toggle(account: StaffAccount) {
    try {
      await api.patch(`/api/staff/users/${account.id}`, { active: !account.active })
      setNotice({ tone: 'success', text: `${account.name} ${account.active ? 'deactivated' : 'reactivated'}.` })
      await reload()
    } catch (e) {
      setNotice({ tone: 'error', text: e instanceof ApiError ? e.message : 'Update failed.' })
    }
  }

  return (
    <>
      <PageHeader title="Staff accounts" description="Organizers manage events and staff. Door staff can only check attendees in." />
      {notice && (
        <div className="mb-4">
          <Alert tone={notice.tone}>{notice.text}</Alert>
        </div>
      )}
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div>
          {loading && !data && <LoadingBlock />}
          {error && <Alert tone="error">{error.message}</Alert>}
          {data && (
            <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              {data.map((account) => (
                <li key={account.id} className="flex items-center justify-between gap-3 px-4 py-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-medium text-slate-900">{account.name}</span>
                      <Badge tone={account.role === 'ORGANIZER' ? 'brand' : 'neutral'}>
                        {account.role === 'ORGANIZER' ? 'Organizer' : 'Door staff'}
                      </Badge>
                      {!account.active && <Badge tone="muted">Inactive</Badge>}
                    </div>
                    <p className="text-sm text-slate-500">
                      @{account.username} · since {formatDateShort(account.createdAt)}
                    </p>
                  </div>
                  {account.id !== me.id && (
                    <Button size="sm" variant="secondary" onClick={() => void toggle(account)}>
                      {account.active ? 'Deactivate' : 'Reactivate'}
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>

        <form onSubmit={create} noValidate className="space-y-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm lg:self-start">
          <h2 className="font-semibold text-slate-900">Add staff member</h2>
          <TextField label="Full name" required value={form.name} error={errors.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <TextField
            label="Username"
            required
            autoCapitalize="none"
            value={form.username}
            error={errors.username}
            onChange={(e) => setForm({ ...form, username: e.target.value })}
          />
          <TextField
            label="Temporary password"
            type="password"
            autoComplete="new-password"
            required
            value={form.password}
            error={errors.password}
            hint="At least 10 characters."
            onChange={(e) => setForm({ ...form, password: e.target.value })}
          />
          <SelectField label="Role" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as StaffRole })}>
            <option value="DOOR_STAFF">Door staff (check-in only)</option>
            <option value="ORGANIZER">Organizer</option>
          </SelectField>
          <Button type="submit" className="w-full" loading={saving} icon={<UserPlus className="size-4" aria-hidden />}>
            Create account
          </Button>
        </form>
      </div>
    </>
  )
}
