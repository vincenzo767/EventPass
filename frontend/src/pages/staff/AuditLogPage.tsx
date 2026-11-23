import { ScrollText } from 'lucide-react'
import { useState } from 'react'
import { PageHeader } from '../../components/StaffLayout'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Alert, EmptyState, LoadingBlock } from '../../components/ui/Feedback'
import { api } from '../../lib/api'
import { formatDateTime } from '../../lib/format'
import type { AuditPage } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useDocumentTitle } from '../../lib/useDocumentTitle'

const actionLabels: Record<string, [string, 'brand' | 'success' | 'warning' | 'danger' | 'neutral']> = {
  EVENT_CREATED: ['Event created', 'brand'],
  EVENT_UPDATED: ['Event edited', 'brand'],
  EVENT_STATUS_CHANGED: ['Event status changed', 'brand'],
  REGISTRATION_CANCELLED: ['Registration cancelled', 'danger'],
  WAITLIST_PROMOTED: ['Waitlist promotion', 'warning'],
  CHECK_IN: ['Check-in', 'success'],
  CHECK_IN_UNDONE: ['Check-in undone', 'warning'],
  STAFF_CREATED: ['Staff account created', 'neutral'],
  STAFF_UPDATED: ['Staff account updated', 'neutral'],
}

export function AuditLogPage() {
  useDocumentTitle('Audit log')
  const [page, setPage] = useState(0)
  const { data, error, loading } = useAsync(() => api.get<AuditPage>(`/api/staff/audit-logs?page=${page}&size=25`), [page])

  return (
    <>
      <PageHeader title="Audit log" description="Who did what, and when. Times are Cebu time." />
      {loading && !data && <LoadingBlock />}
      {error && <Alert tone="error">{error.message}</Alert>}
      {data && data.items.length === 0 && (
        <EmptyState icon={<ScrollText className="size-6" />} title="No activity yet">
          Event changes, cancellations, promotions and check-ins will be recorded here.
        </EmptyState>
      )}
      {data && data.items.length > 0 && (
        <>
          <ol className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            {data.items.map((entry) => {
              const [label, tone] = actionLabels[entry.action] ?? [entry.action, 'neutral' as const]
              return (
                <li key={entry.id} className="flex flex-col gap-1 px-4 py-3 sm:flex-row sm:items-start sm:gap-4">
                  <time dateTime={entry.createdAt} className="shrink-0 text-sm tabular-nums text-slate-500 sm:w-48">
                    {formatDateTime(entry.createdAt)}
                  </time>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge tone={tone}>{label}</Badge>
                      <span className="text-sm font-medium text-slate-900">{entry.staffName}</span>
                      {entry.staffUsername && <span className="text-xs text-slate-500">@{entry.staffUsername}</span>}
                    </div>
                    {entry.details && <p className="mt-1 text-sm text-slate-700">{entry.details}</p>}
                    <p className="mt-0.5 text-xs text-slate-400">
                      {entry.targetType.replace('_', ' ').toLowerCase()} #{entry.targetId}
                    </p>
                  </div>
                </li>
              )
            })}
          </ol>
          <div className="mt-4 flex items-center justify-between text-sm text-slate-600">
            <span>
              Page {data.page + 1} of {Math.max(1, data.totalPages)} · {data.totalItems} entries
            </span>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" disabled={page === 0 || loading} onClick={() => setPage((p) => p - 1)}>
                Newer
              </Button>
              <Button size="sm" variant="secondary" disabled={page + 1 >= data.totalPages || loading} onClick={() => setPage((p) => p + 1)}>
                Older
              </Button>
            </div>
          </div>
        </>
      )}
    </>
  )
}
