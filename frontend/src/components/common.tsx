import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, Database, FlaskConical, Info, Navigation, Power, RefreshCw, Users, Radio, type LucideIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { ApiError } from '@/lib/api'
import { sourceMeta } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { DataSource } from '@/lib/types'

const sourceIcon: Record<DataSource, LucideIcon> = {
  ROUTE_CALCULATION: Navigation,
  USER_TRIP: Users,
  SIMULATED_INVENTORY: FlaskConical,
  LIVE_PROVIDER: Radio,
}

/** Every result shows where its data comes from. Hover for the full explanation. */
export function SourceBadge({ source, label, className }: { source: DataSource; label?: string; className?: string }) {
  const meta = sourceMeta[source]
  const Icon = sourceIcon[source] ?? Database
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Badge tone={meta.tone} className={cn('cursor-help', className)}>
          <Icon />
          {label ?? meta.label}
        </Badge>
      </TooltipTrigger>
      <TooltipContent>{meta.help}</TooltipContent>
    </Tooltip>
  )
}

export function PageHeader({ title, subtitle, actions, eyebrow }: { title: string; subtitle?: ReactNode; actions?: ReactNode; eyebrow?: string }) {
  return (
    <div className="mb-6 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
      <div className="min-w-0">
        {eyebrow && <p className="mb-1 text-xs font-bold uppercase tracking-wider text-brand-600">{eyebrow}</p>}
        <h1 className="text-2xl font-extrabold text-navy-900 sm:text-3xl">{title}</h1>
        {subtitle && <p className="mt-1.5 max-w-2xl text-sm text-muted-foreground sm:text-base">{subtitle}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export function EmptyState({ icon: Icon = Info, title, body, action, className }: { icon?: LucideIcon; title: string; body?: ReactNode; action?: ReactNode; className?: string }) {
  return (
    <div className={cn('flex flex-col items-center rounded-2xl border border-dashed border-navy-200 bg-white/60 px-6 py-10 text-center', className)}>
      <span className="mb-3 inline-flex size-12 items-center justify-center rounded-2xl bg-navy-50 text-navy-500">
        <Icon className="size-6" />
      </span>
      <h3 className="text-base font-bold text-navy-900">{title}</h3>
      {body && <p className="mt-1 max-w-md text-sm text-muted-foreground">{body}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function ErrorState({ error, onRetry, title = 'Something went wrong' }: { error: unknown; onRetry?: () => void; title?: string }) {
  const err = error instanceof ApiError ? error : null
  if (err?.isFeatureDisabled) return <FeatureOff message={err.message} />
  return (
    <div role="alert" className="flex flex-col items-center rounded-2xl border border-red-200 bg-red-50/60 px-6 py-8 text-center">
      <AlertTriangle className="mb-2 size-7 text-destructive" />
      <h3 className="text-base font-bold text-navy-900">{title}</h3>
      <p className="mt-1 max-w-md text-sm text-navy-700">{err?.message ?? (error instanceof Error ? error.message : 'Unexpected error.')}</p>
      {err?.requestId && <p className="mt-1 text-xs text-muted-foreground">Reference: {err.requestId}</p>}
      {onRetry && (
        <Button variant="outline" size="sm" className="mt-4" onClick={onRetry}>
          <RefreshCw /> Try again
        </Button>
      )}
    </div>
  )
}

/** Shown when a feature flag is off, so a disabled feature reads as a decision rather than a bug. */
export function FeatureOff({ message, name }: { message?: string; name?: string }) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-amber-200 bg-amber-50/70 px-6 py-10 text-center">
      <span className="mb-3 inline-flex size-12 items-center justify-center rounded-2xl bg-amber-100 text-amber-700">
        <Power className="size-6" />
      </span>
      <h3 className="text-base font-bold text-navy-900">{name ? `${name} is switched off` : 'This feature is switched off'}</h3>
      <p className="mt-1 max-w-md text-sm text-navy-700">
        {message ?? 'It has been disabled by a feature flag for your account. Flags can be changed remotely, so it may come back without any update.'}
      </p>
      <Button asChild variant="outline" size="sm" className="mt-4">
        <Link to="/app/features">See feature flags</Link>
      </Button>
    </div>
  )
}

export function Notice({ tone = 'info', children, className }: { tone?: 'info' | 'warn' | 'sample'; children: ReactNode; className?: string }) {
  const styles = {
    info: 'border-sky-200 bg-sky-50 text-sky-900',
    warn: 'border-amber-200 bg-amber-50 text-amber-900',
    sample: 'border-amber-200 bg-amber-50 text-amber-900',
  }[tone]
  const Icon = tone === 'sample' ? FlaskConical : tone === 'warn' ? AlertTriangle : Info
  return (
    <div className={cn('flex gap-2.5 rounded-xl border px-3.5 py-2.5 text-[13px] leading-relaxed', styles, className)}>
      <Icon className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0">{children}</div>
    </div>
  )
}

export function Stat({ label, value, hint, icon: Icon }: { label: string; value: ReactNode; hint?: string; icon?: LucideIcon }) {
  return (
    <Card className="flex items-center gap-3.5 p-4">
      {Icon && (
        <span className="inline-flex size-11 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
          <Icon className="size-5" />
        </span>
      )}
      <div className="min-w-0">
        <p className="font-display text-2xl font-extrabold leading-none text-navy-900">{value}</p>
        <p className="mt-1 truncate text-xs font-semibold text-muted-foreground">{label}</p>
        {hint && <p className="truncate text-xs text-muted-foreground">{hint}</p>}
      </div>
    </Card>
  )
}

export function Spinner({ className }: { className?: string }) {
  return <span className={cn('inline-block size-5 animate-spin rounded-full border-2 border-navy-200 border-t-brand-500', className)} role="status" aria-label="Loading" />
}

export function FullPageSpinner() {
  return (
    <div className="flex min-h-dvh items-center justify-center">
      <Spinner className="size-8" />
    </div>
  )
}
