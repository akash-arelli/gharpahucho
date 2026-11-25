import { useQuery } from '@tanstack/react-query'
import { CalendarClock, Lock } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { PassCta } from '@/components/PassCta'
import { api } from '@/lib/api'
import { shortDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import { windowLabel, windowTone } from '@/components/BookingPlanCard'

/** Dashboard "booking radar": which of your journeys has a booking window you must not miss. */
export function BookingRadar() {
  const q = useQuery({ queryKey: ['booking-radar'], queryFn: api.bookingRadar, staleTime: 60_000 })
  if (!q.data || (q.data.items.length === 0 && q.data.hiddenByPlan === 0)) return null
  const r = q.data

  return (
    <section>
      <h2 className="mb-3 flex items-center gap-2 text-base font-bold text-navy-900">
        <CalendarClock className="size-4.5 text-brand-500" /> Booking radar
      </h2>
      <div className="space-y-2">
        {r.items.map((i) => (
          <div key={i.tripId} className={cn('rounded-2xl border bg-white p-3.5', i.urgent ? 'border-amber-300 bg-amber-50' : 'border-border')}>
            <div className="flex flex-wrap items-center gap-2">
              <p className="text-sm font-bold text-navy-900">{i.route}</p>
              <span className="text-xs text-muted-foreground">{shortDate(i.travelDate)}</span>
              <Badge tone={windowTone(i.status)} className="ml-auto">
                {windowLabel(i.status)}
              </Badge>
            </div>
            <p className="mt-1 text-[13px] text-navy-700">{i.headline}</p>
          </div>
        ))}
        {r.hiddenByPlan > 0 && (
          <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-violet-200 bg-violet-50 p-3.5 text-[13px] text-violet-900">
            <Lock className="size-4 shrink-0" />
            <p className="min-w-0 flex-1">
              {r.hiddenByPlan} more journey{r.hiddenByPlan > 1 ? 's are' : ' is'} on your radar. {r.upgradeMessage}
            </p>
            <PassCta label="Unlock" />
          </div>
        )}
      </div>
    </section>
  )
}
