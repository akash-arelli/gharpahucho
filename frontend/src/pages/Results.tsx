import { useMemo, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, GitCompareArrows, Lightbulb, Pencil, Route as RouteIcon, Users } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState, FeatureOff, Notice, PageHeader, SourceBadge } from '@/components/common'
import { BookingPlanCard } from '@/components/BookingPlanCard'
import { FamilyLinkCard } from '@/components/FamilyLinkCard'
import { AlertMeButton } from '@/components/RideAlerts'
import { OptionCard } from '@/components/OptionCard'
import { RouteMap } from '@/components/RouteMap'
import { api, ApiError } from '@/lib/api'
import { useFeatures } from '@/lib/features'
import { duration, longDate, rupees, shortDate } from '@/lib/format'
import { usePlan } from '@/lib/plan'
import { cn } from '@/lib/utils'
import { FLAG, type TravelOption } from '@/lib/types'
import { useAuth } from '@/lib/auth'

type Filter = 'best' | 'cheapest' | 'fastest' | 'fewest' | 'budget'

const FILTERS: { v: Filter; label: string }[] = [
  { v: 'best', label: 'Best overall' },
  { v: 'cheapest', label: 'Cheapest' },
  { v: 'fastest', label: 'Fastest' },
  { v: 'fewest', label: 'Fewest transfers' },
  { v: 'budget', label: 'Within budget' },
]

function sortFor(filter: Filter, list: TravelOption[]) {
  const l = [...list]
  switch (filter) {
    case 'cheapest':
      return l.sort((a, b) => a.costPerPerson - b.costPerPerson || a.durationMinutes - b.durationMinutes)
    case 'fastest':
      return l.sort((a, b) => a.durationMinutes - b.durationMinutes || a.costPerPerson - b.costPerPerson)
    case 'fewest':
      return l.sort((a, b) => a.transfers - b.transfers || a.durationMinutes - b.durationMinutes)
    default:
      return l.sort((a, b) => b.overallScore - a.overallScore)
  }
}

export default function Results() {
  const plan = usePlan()
  const { isEnabled } = useFeatures()
  const { user } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [filter, setFilter] = useState<Filter>('best')
  const [showAlt, setShowAlt] = useState(true)
  const [explained, setExplained] = useState<Record<string, string>>({})
  const [explainingId, setExplainingId] = useState<string | null>(null)
  const [explainNotes, setExplainNotes] = useState<Record<string, string>>({})

  const saved = useQuery({ queryKey: ['saved'], queryFn: api.savedTrips, staleTime: 30_000 })
  const savedIds = useMemo(() => new Set((saved.data ?? []).map((s) => `${s.option.id}|${s.travelDate}`)), [saved.data])

  const rerun = useMutation({
    mutationFn: (date: string) => api.plan({ ...plan.request!, travelDate: date }),
    onSuccess: (res, date) => {
      plan.setPlan({ ...plan.request!, travelDate: date }, res, plan.tripId)
      toast.success(`Showing options for ${longDate(date)}`)
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not re-plan.'),
  })

  const save = useMutation({
    mutationFn: (o: TravelOption) =>
      api.saveOption({
        tripId: plan.tripId ?? undefined,
        title: `${plan.request!.sourceCity} → ${plan.request!.destinationCity} · ${o.title}`,
        sourceCity: plan.request!.sourceCity,
        destinationCity: plan.request!.destinationCity,
        travelDate: plan.request!.travelDate,
        option: o,
      }),
    onSuccess: () => {
      toast.success('Saved to your dashboard')
      void qc.invalidateQueries({ queryKey: ['saved'] })
      void qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not save.'),
  })

  const explain = useMutation({
    mutationFn: (o: TravelOption) => {
      setExplainingId(o.id)
      return api.explain({ plan: plan.request!, optionId: o.id, language: user?.preferredLanguage })
    },
    onSuccess: (r, o) => {
      if (r.available && r.explanation) setExplained((m) => ({ ...m, [o.id]: r.explanation! }))
      else setExplainNotes((m) => ({ ...m, [o.id]: r.notice ?? 'AI explanations are unavailable right now. The reasons listed on the card come from our scoring rules.' }))
    },
    onError: (e, o) => setExplainNotes((m) => ({ ...m, [o.id]: e instanceof ApiError ? e.message : 'Explanation failed.' })),
    onSettled: () => setExplainingId(null),
  })

  const { request, response } = plan
  const budgetSet = request?.maxBudget != null

  const { primary, alternatives } = useMemo(() => {
    const opts = response?.options ?? []
    let main = opts.filter((o) => o.meetsConstraints)
    if (filter === 'budget') main = main.filter((o) => o.tags.includes('WITHIN_BUDGET'))
    return { primary: sortFor(filter, main), alternatives: sortFor(filter, opts.filter((o) => !o.meetsConstraints)) }
  }, [response, filter])

  if (!isEnabled(FLAG.planner)) return <FeatureOff name="The smart trip planner" />
  if (!request || !response) return <Navigate to="/app/plan" replace />

  const r = response.route
  const compareOn = plan.compare.length
  const altOn = isEnabled(FLAG.alternatives)
  const budgetOn = isEnabled(FLAG.budget)
  const explainOn = isEnabled(FLAG.explanations)

  const card = (o: TravelOption, i?: number) => (
    <OptionCard
      key={o.id}
      option={o}
      rank={i !== undefined ? i + 1 : undefined}
      travelDate={request.travelDate}
      route={{ from: request.sourceCity, to: request.destinationCity }}
      inCompare={plan.compare.includes(o.id)}
      compareDisabled={plan.compare.length >= 3}
      onToggleCompare={() => plan.toggleCompare(o.id)}
      saved={savedIds.has(`${o.id}|${request.travelDate}`)}
      onSave={() => save.mutate(o)}
      saving={save.isPending && save.variables?.id === o.id}
      explainEnabled={explainOn}
      onExplain={() => explain.mutate(o)}
      explaining={explainingId === o.id}
      explanation={
        explained[o.id] ? (
          <>
            <p className="mb-1 text-[11px] font-bold uppercase tracking-wide text-violet-700">Explained by Sarvam AI</p>
            {explained[o.id]}
          </>
        ) : explainNotes[o.id] ? (
          <span className="text-muted-foreground">{explainNotes[o.id]}</span>
        ) : undefined
      }
    />
  )

  return (
    <>
      <PageHeader
        eyebrow="Recommendations"
        title={`${request.sourceCity} → ${request.destinationCity}`}
        subtitle={`${longDate(request.travelDate)} · ${request.travellers ?? 1} traveller${(request.travellers ?? 1) > 1 ? 's' : ''}${budgetSet ? ` · budget ${rupees(request.maxBudget)}` : ''}`}
        actions={
          <>
            <Button variant="outline" asChild>
              <Link to="/app/plan">
                <Pencil /> Edit journey
              </Link>
            </Button>
            {plan.tripId && <AlertMeButton tripId={plan.tripId} size="default" />}
            {isEnabled(FLAG.companions) && plan.tripId && (
              <Button variant="softBrand" asChild>
                <Link to={`/app/travellers?tripId=${plan.tripId}`}>
                  <Users /> Find companions
                </Link>
              </Button>
            )}
          </>
        }
      />

      {response.festive && (
        <div className="mb-5 flex items-start gap-3 rounded-2xl border border-brand-200 bg-brand-50 px-4 py-3.5">
          <span className="text-2xl leading-none">🪔</span>
          <div>
            <p className="text-sm font-bold text-navy-900">
              {response.festive.name} season <Badge tone="brand" className="ml-1.5">Demand {response.festive.demandLevel}/5</Badge>
            </p>
            <p className="mt-0.5 text-[13px] text-navy-700">{response.festive.note}</p>
          </div>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_21rem]">
        <div className="min-w-0 space-y-4">
          {/* filters */}
          <div className="flex flex-wrap items-center gap-2" role="tablist" aria-label="Sort and filter options">
            {FILTERS.filter((f) => f.v !== 'budget' || budgetSet).map((f) => (
              <button
                key={f.v}
                role="tab"
                aria-selected={filter === f.v}
                onClick={() => setFilter(f.v)}
                className={cn(
                  'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
                  filter === f.v ? 'border-navy-900 bg-navy-900 text-white' : 'border-border bg-white text-navy-700 hover:border-navy-300',
                )}
              >
                {f.label}
              </button>
            ))}
            {altOn && alternatives.length > 0 && (
              <label className="ml-auto flex cursor-pointer items-center gap-2 text-[13px] font-medium text-navy-700">
                <input type="checkbox" checked={showAlt} onChange={(e) => setShowAlt(e.target.checked)} className="size-4 accent-brand-500" />
                Show alternatives
              </label>
            )}
          </div>

          <p className="text-[13px] text-muted-foreground">
            {response.withinConstraints} of {response.totalCandidates} options match your requirements.
          </p>

          {response.notices.length > 0 && (
            <div className="space-y-2">
              {response.notices.map((n) => (
                <Notice key={n} tone="sample">
                  {n}
                </Notice>
              ))}
            </div>
          )}

          {rerun.isPending && <p className="text-sm font-medium text-navy-600">Re-planning…</p>}

          {primary.length === 0 ? (
            <EmptyState
              icon={RouteIcon}
              title={filter === 'budget' ? 'Nothing fits that budget' : 'No options match every requirement'}
              body={alternatives.length > 0 ? 'Alternatives below relax one requirement and say which.' : 'Try changing the date, transport mode or budget.'}
              action={
                <Button asChild variant="outline">
                  <Link to="/app/plan">Adjust my journey</Link>
                </Button>
              }
            />
          ) : (
            <div className="space-y-4">{primary.map((o, i) => card(o, i))}</div>
          )}

          {altOn && showAlt && alternatives.length > 0 && (
            <section className="space-y-4 pt-2">
              <div>
                <h2 className="text-lg font-bold text-navy-900">Alternatives</h2>
                <p className="text-[13px] text-muted-foreground">These don’t meet all of your requirements. Each card says which one it misses.</p>
              </div>
              {alternatives.map((o) => card(o))}
            </section>
          )}

          <Notice tone="info">{response.disclaimer}</Notice>
        </div>

        {/* sidebar */}
        <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start">
          <Card className="overflow-hidden">
            <RouteMap route={r} className="h-56 w-full" />
            <CardContent className="space-y-2 pt-4">
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <p className="font-display text-xl font-extrabold text-navy-900">{Math.round(r.distanceKm)} km</p>
                  <p className="text-xs text-muted-foreground">Road distance</p>
                </div>
                <div>
                  <p className="font-display text-xl font-extrabold text-navy-900">{duration(r.durationMinutes)}</p>
                  <p className="text-xs text-muted-foreground">By road</p>
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-2 pt-1">
                <SourceBadge source="ROUTE_CALCULATION" label={r.measured ? `Measured · ${r.engine}` : 'Estimated'} />
              </div>
              <p className="text-xs leading-relaxed text-muted-foreground">{r.note}</p>
            </CardContent>
          </Card>

          {plan.tripId && <BookingPlanCard tripId={plan.tripId} />}
          {plan.tripId && <FamilyLinkCard tripId={plan.tripId} />}

          {budgetOn && response.budgetTips.length > 0 && (
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="flex items-center gap-2">
                  <Lightbulb className="size-4.5 text-brand-500" /> Ways to save
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {response.budgetTips.map((t) => (
                  <div key={t.title} className="rounded-xl bg-emerald-50 p-3">
                    <div className="flex items-start justify-between gap-2">
                      <p className="text-sm font-bold text-navy-900">{t.title}</p>
                      <Badge tone="green">Save {rupees(t.savingPerPerson)}</Badge>
                    </div>
                    <p className="mt-1 text-[13px] text-navy-700">{t.detail}</p>
                    {t.suggestedDate && (
                      <Button size="sm" variant="soft" className="mt-2" onClick={() => rerun.mutate(t.suggestedDate!)} loading={rerun.isPending}>
                        Try {shortDate(t.suggestedDate)} <ArrowRight />
                      </Button>
                    )}
                  </div>
                ))}
              </CardContent>
            </Card>
          )}
        </aside>
      </div>

      {compareOn > 0 && (
        <div className="fixed inset-x-0 bottom-[4.5rem] z-30 px-4 md:bottom-6">
          <div className="mx-auto flex max-w-xl items-center gap-3 rounded-2xl bg-navy-900 px-4 py-3 text-white shadow-pop">
            <GitCompareArrows className="size-5 text-brand-400" />
            <p className="flex-1 text-sm font-semibold">{compareOn} selected {compareOn < 2 && <span className="font-normal text-navy-300">· pick one more</span>}</p>
            <Button size="sm" variant="ghost" className="text-navy-200 hover:bg-white/10 hover:text-white" onClick={plan.clearCompare}>
              Clear
            </Button>
            <Button size="sm" variant="brand" disabled={compareOn < 2} onClick={() => navigate('/app/compare')}>
              Compare
            </Button>
          </div>
        </div>
      )}
    </>
  )
}
