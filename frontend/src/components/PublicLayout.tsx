import { CalendarDays, LogIn, Ticket } from 'lucide-react'
import { Link, NavLink, Outlet } from 'react-router'
import { Logo } from './Logo'

const navClass = ({ isActive }: { isActive: boolean }) =>
  `inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-brand-50 text-brand-800' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
  }`

export function PublicLayout() {
  return (
    <div className="flex min-h-dvh flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-white focus:px-3 focus:py-2 focus:shadow">
        Skip to content
      </a>
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-2 px-4 sm:px-6">
          <Link to="/" aria-label="EventPass home" className="rounded-lg">
            <Logo />
          </Link>
          <nav aria-label="Main" className="flex items-center gap-1">
            <NavLink to="/" end className={navClass}>
              <CalendarDays className="size-4" aria-hidden />
              <span className="hidden sm:inline">Events</span>
              <span className="sr-only sm:hidden">Events</span>
            </NavLink>
            <NavLink to="/tickets/find" className={navClass}>
              <Ticket className="size-4" aria-hidden />
              <span>Find my ticket</span>
            </NavLink>
          </nav>
        </div>
      </header>

      <main id="main" className="flex-1">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-6 text-sm text-slate-500 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <p>EventPass · All times are Cebu time (Asia/Manila, UTC+8)</p>
          <Link to="/staff" className="inline-flex items-center gap-1.5 hover:text-slate-800">
            <LogIn className="size-4" aria-hidden />
            Staff sign in
          </Link>
        </div>
      </footer>
    </div>
  )
}
