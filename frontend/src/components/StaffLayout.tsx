import { CalendarCog, LayoutDashboard, LogOut, ScanLine, ScrollText, Users } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { Logo } from './Logo'

interface NavItem {
  to: string
  label: string
  icon: ReactNode
  end?: boolean
  organizerOnly?: boolean
}

const items: NavItem[] = [
  { to: '/staff', label: 'Dashboard', icon: <LayoutDashboard className="size-4" aria-hidden />, end: true },
  { to: '/staff/check-in', label: 'Check-in', icon: <ScanLine className="size-4" aria-hidden /> },
  { to: '/staff/events', label: 'Events', icon: <CalendarCog className="size-4" aria-hidden /> },
  { to: '/staff/audit', label: 'Audit log', icon: <ScrollText className="size-4" aria-hidden />, organizerOnly: true },
  { to: '/staff/users', label: 'Staff', icon: <Users className="size-4" aria-hidden />, organizerOnly: true },
]

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `flex shrink-0 items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-brand-50 text-brand-800' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
  }`

export function StaffLayout() {
  const { staff, logout } = useAuth()
  const navigate = useNavigate()
  const visible = items.filter((item) => !item.organizerOnly || staff?.role === 'ORGANIZER')

  async function signOut() {
    await logout()
    navigate('/staff/login', { replace: true })
  }

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-3 px-4 sm:px-6">
          <Link to="/staff" aria-label="EventPass staff dashboard" className="rounded-lg">
            <Logo subtitle="Staff" />
          </Link>
          {staff && (
            <div className="flex items-center gap-3">
              <div className="hidden text-right sm:block">
                <p className="text-sm font-medium text-slate-900">{staff.name}</p>
                <p className="text-xs text-slate-500">{staff.role === 'ORGANIZER' ? 'Organizer' : 'Door staff'}</p>
              </div>
              <button
                type="button"
                onClick={signOut}
                className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-300 px-3 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                <LogOut className="size-4" aria-hidden />
                Sign out
              </button>
            </div>
          )}
        </div>
        {staff && (
          <nav aria-label="Staff" className="border-t border-slate-100 lg:hidden">
            <div className="mx-auto flex max-w-7xl gap-1 overflow-x-auto px-3 py-2">
              {visible.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>
                  {item.icon}
                  {item.label}
                </NavLink>
              ))}
            </div>
          </nav>
        )}
      </header>

      <div className="mx-auto flex max-w-7xl gap-8 px-4 py-6 sm:px-6 lg:py-8">
        {staff && (
          <nav aria-label="Staff sections" className="hidden w-52 shrink-0 lg:block">
            <div className="sticky top-24 space-y-1">
              {visible.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>
                  {item.icon}
                  {item.label}
                </NavLink>
              ))}
              <div className="pt-4">
                <Link to="/" className="px-3 text-xs text-slate-500 hover:text-slate-800">
                  View public site →
                </Link>
              </div>
            </div>
          </nav>
        )}
        <main id="main" className="min-w-0 flex-1">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
        {description && <div className="mt-1 text-sm text-slate-600">{description}</div>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}
