import { ShieldAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { EmptyState, LoadingBlock } from '../components/ui/Feedback'
import type { StaffRole } from '../lib/types'
import { useAuth } from './AuthContext'

/**
 * Client-side guard for staff routes. This only improves navigation: every staff API endpoint is
 * independently protected by the server (401 when signed out, 403 for the wrong role), and the server
 * also redirects signed-out browser requests for /staff/** to the sign-in page.
 */
export function RequireStaff({ children, role }: { children: ReactNode; role?: StaffRole }) {
  const { staff } = useAuth()
  const location = useLocation()

  if (staff === undefined) return <LoadingBlock label="Checking your session…" />
  if (staff === null) {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/staff/login?next=${next}`} replace />
  }
  if (role && staff.role !== role) {
    return (
      <EmptyState icon={<ShieldAlert className="size-6" />} title="Organizer access required">
        Your account ({staff.role === 'DOOR_STAFF' ? 'Door staff' : staff.role}) can check attendees in but cannot
        manage this area. Ask an organizer if you need access.
      </EmptyState>
    )
  }
  return <>{children}</>
}

/** Only allow redirect targets inside the staff area (prevents open redirects). */
export function safeNext(next: string | null): string {
  if (next && next.startsWith('/staff') && !next.startsWith('//') && !next.startsWith('/staff/login')) return next
  return '/staff'
}
