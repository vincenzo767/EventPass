export function LogoMark({ className = 'size-8' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="8" className="fill-brand-700" />
      <path
        d="M9 11.5A2.5 2.5 0 0 1 11.5 9h9a2.5 2.5 0 0 1 2.5 2.5v2a2.5 2.5 0 0 0 0 5v2a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 9 20.5v-2a2.5 2.5 0 0 0 0-5z"
        fill="#fff"
      />
      <path d="M13 16.2l2 2 4-4.4" className="stroke-brand-700" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function Logo({ subtitle }: { subtitle?: string }) {
  return (
    <span className="flex items-center gap-2.5">
      <LogoMark />
      <span className="text-lg font-semibold tracking-tight text-slate-900">EventPass</span>
      {subtitle && (
        <span className="rounded-md bg-slate-100 px-1.5 py-0.5 text-xs font-medium text-slate-600">{subtitle}</span>
      )}
    </span>
  )
}
