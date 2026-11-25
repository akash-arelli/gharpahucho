import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BellRing, Car, X } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { PassCta } from '@/components/PassCta'
import { api, ApiError } from '@/lib/api'
import { useFeatures } from '@/lib/features'
import { rupees, shortDate } from '@/lib/format'
import { PASS_PATH } from '@/lib/pass'
import { FLAG } from '@/lib/types'

/** "Alert me": asks to hear about rides on this journey's route and date. The free plan has a small limit. */
export function AlertMeButton({ tripId, size = 'sm' }: { tripId: string; size?: 'sm' | 'default' }) {
  const { isEnabled } = useFeatures()
  const qc = useQueryClient()
  const navigate = useNavigate()
  const create = useMutation({
    mutationFn: () => api.createAlert(tripId),
    onSuccess: (a) => {
      toast.success(`We’ll tell you when a ride appears: ${a.route}`)
      void qc.invalidateQueries({ queryKey: ['alerts'] })
      void qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === 'PLAN_LIMIT') {
        toast.error(e.message, { action: { label: 'Get the pass', onClick: () => navigate(PASS_PATH) } })
      } else {
        toast.error(e instanceof ApiError ? e.message : 'Could not set the alert.')
      }
    },
  })
  if (!isEnabled(FLAG.rideAlerts)) return null
  return (
    <Button variant="softBrand" size={size} onClick={() => create.mutate()} loading={create.isPending}>
      <BellRing /> Alert me about rides
    </Button>
  )
}

/** Dashboard card: your alerts, and any rides that have appeared on those routes. */
export function RideAlertsCard() {
  const { isEnabled } = useFeatures()
  const qc = useQueryClient()
  const on = isEnabled(FLAG.rideAlerts)
  const q = useQuery({ queryKey: ['alerts'], queryFn: api.alerts, enabled: on, refetchInterval: 30_000 })
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['alerts'] })
    void qc.invalidateQueries({ queryKey: ['dashboard'] })
  }
  const remove = useMutation({ mutationFn: api.deleteAlert, onSuccess: refresh })
  const seen = useMutation({ mutationFn: api.seenAlert, onSuccess: refresh })

  if (!on || !q.data) return null
  const d = q.data
  const atLimit = !d.unlimited && d.alerts.length >= d.limit

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2">
          <BellRing className="size-4.5 text-brand-500" /> Ride alerts
          {d.alerts.some((a) => a.newRides > 0) && <Badge tone="green">New</Badge>}
          {!d.unlimited && (
            <span className="ml-auto text-xs font-medium text-muted-foreground">
              {d.alerts.length} of {d.limit}
            </span>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {d.alerts.length === 0 && (
          <p className="text-sm text-muted-foreground">
            Tell us a route and date and we’ll let you know when someone posts a ride. Use “Alert me about rides” on any of your journeys.
          </p>
        )}
        {d.alerts.map((a) => (
          <div key={a.id} className="rounded-xl border border-border p-3">
            <div className="flex items-start gap-2">
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-bold text-navy-900">{a.route}</p>
                <p className="text-xs text-muted-foreground">
                  {shortDate(a.travelDate)}
                  {a.flexDays > 0 ? ` ± ${a.flexDays} day${a.flexDays > 1 ? 's' : ''}` : ''}
                </p>
              </div>
              <button
                type="button"
                onClick={() => remove.mutate(a.id)}
                className="rounded-md p-1 text-navy-400 transition-colors hover:bg-navy-50 hover:text-navy-700"
                aria-label={`Remove alert for ${a.route}`}
              >
                <X className="size-4" />
              </button>
            </div>
            {a.rides.length === 0 ? (
              <p className="mt-2 text-[13px] text-navy-600">No rides yet. We’ll tell you the moment one appears.</p>
            ) : (
              <ul className="mt-2 space-y-1.5">
                {a.rides.map((r) => (
                  <li key={r.tripId}>
                    <Link to={`/app/travellers/${r.hostId}`} className="flex items-center gap-2 rounded-lg bg-emerald-50 px-2.5 py-2 text-[13px] transition-colors hover:bg-emerald-100">
                      <Car className="size-4 shrink-0 text-emerald-700" />
                      <span className="min-w-0 flex-1 truncate font-semibold text-navy-900">
                        {r.host} · {shortDate(r.travelDate)}
                        {r.startsNear ? ` · ${r.startsNear}` : ''}
                      </span>
                      <span className="shrink-0 text-xs text-navy-700">
                        {r.seats} seat{r.seats > 1 ? 's' : ''}
                        {r.costPerSeat ? ` · ${rupees(r.costPerSeat)}` : ''}
                      </span>
                      {r.fresh && <Badge tone="green">New</Badge>}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
            {a.newRides > 0 && (
              <Button variant="link" size="sm" className="mt-1 h-auto" onClick={() => seen.mutate(a.id)}>
                Mark as seen
              </Button>
            )}
          </div>
        ))}
        {atLimit && (
          <div className="flex flex-wrap items-center gap-2 rounded-xl border border-violet-200 bg-violet-50 p-3 text-[13px] text-violet-900">
            <p className="min-w-0 flex-1">{d.upgradeMessage ?? 'Get the Festival Pass for unlimited ride alerts.'}</p>
            <PassCta label="Unlimited alerts" />
          </div>
        )}
      </CardContent>
    </Card>
  )
}
