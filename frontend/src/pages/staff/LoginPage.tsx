import { LogIn } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { safeNext } from '../../auth/RequireStaff'
import { Logo } from '../../components/Logo'
import { Button } from '../../components/ui/Button'
import { Alert } from '../../components/ui/Feedback'
import { TextField } from '../../components/ui/Field'
import { ApiError } from '../../lib/api'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

export function LoginPage() {
  useDocumentTitle('Staff sign in')
  const { staff, login } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ username?: string; password?: string; form?: string }>({})
  const [submitting, setSubmitting] = useState(false)

  if (staff) return <Navigate to={next} replace />

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const fieldErrors = {
      username: username.trim() ? undefined : 'Enter your username.',
      password: password ? undefined : 'Enter your password.',
    }
    setErrors(fieldErrors)
    if (fieldErrors.username || fieldErrors.password) return

    setSubmitting(true)
    try {
      await login(username.trim(), password)
      navigate(next, { replace: true })
    } catch (e) {
      setErrors({ form: e instanceof ApiError ? e.message : 'Sign-in failed. Please try again.' })
      setPassword('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center bg-slate-50 px-4 py-10">
      <Link to="/" aria-label="EventPass home" className="mb-8 rounded-lg">
        <Logo subtitle="Staff" />
      </Link>
      <div className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">Staff sign in</h1>
        <p className="mt-1 text-sm text-slate-600">For event organizers and door staff.</p>

        {params.get('next') && !errors.form && (
          <div className="mt-4">
            <Alert tone="info">Please sign in to open that page.</Alert>
          </div>
        )}

        <form onSubmit={onSubmit} noValidate className="mt-5 space-y-4">
          {errors.form && <Alert tone="error">{errors.form}</Alert>}
          <TextField
            label="Username"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            required
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            error={errors.username}
          />
          <TextField
            label="Password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={errors.password}
          />
          <Button type="submit" size="lg" className="w-full" loading={submitting} icon={<LogIn className="size-5" aria-hidden />}>
            Sign in
          </Button>
        </form>
      </div>
      <Link to="/" className="mt-6 text-sm text-slate-500 hover:text-slate-800">
        ← Back to public events
      </Link>
    </div>
  )
}
