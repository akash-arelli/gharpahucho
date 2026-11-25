import { useQuery } from '@tanstack/react-query'
import { CalendarClock, Lock, Ticket } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { PassCta } from '@/components/PassCta'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { api } from '@/lib/api'
import { shortDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { BookingWindow, DayPressure } from '@/lib/types'

const LEVEL_BAR: Record<DayPressure['level'], string> = {
  LOW: 'bg-emerald-400',
  MODERATE: 'bg-amber-300',
  HIGH: 'bg-orange-400',
  PEAK: 'bg-red-500',
}

export const windowTone = (s: BookingWindow['status']) => (s === 'OPEN' ? 'green' : s === 'UPCOMING' ? 'amber' : 'neutral')
export const windowLabel = (s: BookingWindow['status']) => (s === 'OPEN' ? 'Booking open' : s === 'UPCOMING' ? 'Opens soon' : 'Closed')

/** When-to-book plan for a saved journey. What is unlocked is decided by the backend (Flagsmith: festival_booking_plan). */
export function BookingPlanCard({ tripId }: { tripId: string }) {
  const q = useQuery({ queryKey: ['booking-plan', tripId], queryFn: () => api.bookingPlan(tripId), staleTime: 60_000 })

  if (q.isLoading) return <Skeleton className="h-64 w-full rounded-2xl" />
  if (q.isError || !q.data) return null
  const p = q.data

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2">
          <CalendarClock className="size-4.5 text-brand-500" /> When to book
          {!p.fullAccess && <Badge tone="violet" className="ml-auto">Festival Pass</Badge>}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="rounded-xl bg-navy-50 p-3">
          <div className="flex items-center gap-2">
            <Badge tone={windowTone(p.railWindow.status)}>{windowLabel(p.railWindow.status)}</Badge>
            {p.festival && <Badge tone="brand">{p.festival.name}</Badge>}
          </div>
          <p className="mt-2 text-sm font-semibold text-navy-900">{p.railWindow.headline}</p>
        </div>

        {p.fullAccess && p.days.length > 0 && (
          <div>
            <p className="mb-2 text-xs font-bold uppercase tracking-wide text-navy-500">How crowded each day is likely to be</p>
            <div className="flex items-end gap-1.5" role="img" aria-label="Estimated crowding for the days around your date">
              {p.days.map((d) => (
                <div key={d.date} className="flex flex-1 flex-col items-center gap-1">
                  <span className="text-[10px] font-semibold text-navy-600">{d.calmer ? `${d.vsChosenPct}%` : d.chosen ? 'you' : ''}</span>
                  <div className="flex h-16 w-full items-end rounded-md bg-navy-50">
                    <div
                      className={cn('w-full rounded-md', LEVEL_BAR[d.level], d.chosen && 'ring-2 ring-navy-900')}
                      style={{ height: `${Math.max(12, d.pressure)}%` }}
                      title={`${shortDate(d.date)}: ${d.level.toLowerCase()}`}
                    />
                  </div>
                  <span className={cn('text-[10px]', d.chosen ? 'font-bold text-navy-900' : 'text-navy-500')}>{d.weekday}</span>
                  <span className="text-[10px] text-navy-500">{d.date.slice(8)}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        <ol className="space-y-3">
          {p.steps.map((s) => (
            <li key={s.order} className={cn('flex gap-3', s.locked && 'opacity-80')}>
              <span
                className={cn(
                  'mt-0.5 grid size-6 shrink-0 place-items-center rounded-full text-xs font-bold',
                  s.locked ? 'bg-violet-100 text-violet-700' : 'bg-brand-100 text-brand-700',
                )}
              >
                {s.locked ? <Lock className="size-3" /> : s.order}
              </span>
              <div className="min-w-0">
                <p className="text-sm font-bold text-navy-900">{s.title}</p>
                {s.detail && <p className="mt-0.5 text-[13px] leading-relaxed text-navy-700">{s.detail}</p>}
                {s.locked && <p className="mt-0.5 text-[12px] text-violet-700">Included with the Festival Pass</p>}
              </div>
            </li>
          ))}
        </ol>

        {p.lock && (
          <div className="rounded-xl border border-violet-200 bg-violet-50 p-3 text-[13px] text-violet-900">
            <div className="flex items-start gap-2">
              <Ticket className="mt-0.5 size-4 shrink-0" />
              <p>{p.lock.message}</p>
            </div>
            <PassCta className="mt-3 w-full" />
          </div>
        )}

        <ul className="space-y-1 text-[11px] leading-relaxed text-muted-foreground">
          {p.notes.map((n) => (
            <li key={n}>{n}</li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
