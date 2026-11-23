import { FileQuestion } from 'lucide-react'
import { lazy, Suspense, type ReactNode } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router'
import { AuthProvider } from './auth/AuthContext'
import { RequireStaff } from './auth/RequireStaff'
import { PublicLayout } from './components/PublicLayout'
import { StaffLayout } from './components/StaffLayout'
import { ButtonLink } from './components/ui/Button'
import { EmptyState, LoadingBlock } from './components/ui/Feedback'
import { EventDetailPage } from './pages/public/EventDetailPage'
import { EventListPage } from './pages/public/EventListPage'
import { FindTicketPage } from './pages/public/FindTicketPage'
import { RegisterPage } from './pages/public/RegisterPage'
import { TicketPage } from './pages/public/TicketPage'
import { LoginPage } from './pages/staff/LoginPage'

// Staff pages are loaded on demand so attendees never download staff-only code (e.g. the QR scanner).
const AttendeesPage = lazy(() => import('./pages/staff/AttendeesPage').then((m) => ({ default: m.AttendeesPage })))
const AuditLogPage = lazy(() => import('./pages/staff/AuditLogPage').then((m) => ({ default: m.AuditLogPage })))
const CheckInPage = lazy(() => import('./pages/staff/CheckInPage').then((m) => ({ default: m.CheckInPage })))
const DashboardPage = lazy(() => import('./pages/staff/DashboardPage').then((m) => ({ default: m.DashboardPage })))
const EventFormPage = lazy(() => import('./pages/staff/EventFormPage').then((m) => ({ default: m.EventFormPage })))
const EventsPage = lazy(() => import('./pages/staff/EventsPage').then((m) => ({ default: m.EventsPage })))
const StaffUsersPage = lazy(() => import('./pages/staff/StaffUsersPage').then((m) => ({ default: m.StaffUsersPage })))

function Lazy({ children }: { children: ReactNode }) {
  return <Suspense fallback={<LoadingBlock />}>{children}</Suspense>
}

function NotFound() {
  return (
    <div className="mx-auto max-w-md px-4 py-16">
      <EmptyState icon={<FileQuestion className="size-6" />} title="Page not found">
        <p>The page you are looking for does not exist.</p>
        <div className="mt-4">
          <ButtonLink to="/" variant="secondary">
            Back to events
          </ButtonLink>
        </div>
      </EmptyState>
    </div>
  )
}

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<EventListPage />} />
        <Route path="events/:eventId" element={<EventDetailPage />} />
        <Route path="events/:eventId/register" element={<RegisterPage />} />
        <Route path="tickets/find" element={<FindTicketPage />} />
        <Route path="tickets/:token" element={<TicketPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>

      <Route path="staff/login" element={<LoginPage />} />
      <Route path="staff" element={<RequireStaff><StaffLayout /></RequireStaff>}>
        <Route index element={<Lazy><DashboardPage /></Lazy>} />
        <Route path="check-in" element={<Lazy><CheckInPage /></Lazy>} />
        <Route path="check-in/:eventId" element={<Lazy><CheckInPage /></Lazy>} />
        <Route path="events" element={<Lazy><EventsPage /></Lazy>} />
        <Route path="events/new" element={<RequireStaff role="ORGANIZER"><Lazy><EventFormPage /></Lazy></RequireStaff>} />
        <Route path="events/:eventId" element={<Lazy><AttendeesPage /></Lazy>} />
        <Route path="events/:eventId/edit" element={<RequireStaff role="ORGANIZER"><Lazy><EventFormPage /></Lazy></RequireStaff>} />
        <Route path="audit" element={<RequireStaff role="ORGANIZER"><Lazy><AuditLogPage /></Lazy></RequireStaff>} />
        <Route path="users" element={<RequireStaff role="ORGANIZER"><Lazy><StaffUsersPage /></Lazy></RequireStaff>} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  )
}
