import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Bell, Bookmark, CalendarDays, Clock, MapPinned, Plus, Route, Sparkles, Users, UsersRound } from 'lucide-react'
import { Avatar } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { EmptyState, ErrorState, Notice, PageHeader, SourceBadge, Stat } from '@/components/common'
import { BookingRadar } from '@/components/BookingRadar'
import { PassBanner } from '@/components/PassCta'
import { AlertMeButton, RideAlertsCard } from '@/components/RideAlerts'
import { OptionCard } from '@/components/OptionCard'
import { ScoreBadge } from '@/components/MatchCard'
import { api } from '@/lib/api'
import { useFeatures } from '@/lib/features'
import { modeLabel, relativeTime, rupees, shortDate } from '@/lib/format'
import { usePlan } from '@/lib/plan'
import { FLAG } from '@/lib/types'
import { groupStatusTone } from '@/pages/Groups'

export default function Dashboard() {
  const { isEnabled } = useFeatures()
  const plan = usePlan()
  const navigate = useNavigate()
  const q = useQuery({ queryKey: ['dashboard'], queryFn: api.dashboard, refetchInterval: 30_000 })

  if (q.isLoading) return <DashboardSkeleton />
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} title="Couldn’t load your dashboard" />
  const d = q.data!
  const rec = d.recommendedPlan
  const plannerOn = isEnabled(FLAG.planner)
  const companionsOn = isEnabled(FLAG.companions)
  const topOption = rec?.options.find((o) => o.meetsConstraints) ?? rec?.options[0]
  const notifications = d.pendingRequests + d.groupInvitations.length

  return (
    <>
      <PageHeader
        eyebrow="Dashboard"
        title={`Namaste, ${d.greetingName}`}
        subtitle="Here’s where your journeys stand."
        actions={
          plannerOn ? (
            <Button asChild variant="brand">
              <Link to="/app/plan">
                <Plus /> Plan your journey
              </Link>
            </Button>
          ) : undefined
        }
      />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat icon={Route} label="Upcoming journeys" value={d.upcomingJourneys.length} />
        <Stat icon={Bookmark} label="Saved options" value={d.savedTrips.length} />
        <Stat icon={Bell} label="Pending requests" value={d.pendingRequests} />
        <Stat icon={UsersRound} label="Your groups" value={d.myGroups.length} />
      </div>

      <PassBanner />

      {notifications > 0 && (
        <div className="mt-5 flex flex-col gap-3 rounded-2xl border border-brand-200 bg-brand-50 p-4 sm:flex-row sm:items-center">
          <Bell className="size-5 text-brand-600" />
          <p className="flex-1 text-sm font-semibold text-navy-900">
            {d.pendingRequests > 0 && `${d.pendingRequests} traveller request${d.pendingRequests > 1 ? 's' : ''} waiting`}
            {d.pendingRequests > 0 && d.groupInvitations.length > 0 && ' · '}
            {d.groupInvitations.length > 0 && `${d.groupInvitations.length} group invitation${d.groupInvitations.length > 1 ? 's' : ''}`}
          </p>
          <div className="flex gap-2">
            {d.pendingRequests > 0 && (
              <Button asChild size="sm" variant="brand">
                <Link to="/app/travellers">View requests</Link>
              </Button>
            )}
            {d.groupInvitations.length > 0 && (
              <Button asChild size="sm" variant="outline">
                <Link to="/app/groups">View invitations</Link>
              </Button>
            )}
          </div>
        </div>
      )}

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="min-w-0 space-y-6">
          {plannerOn && <BookingRadar />}

          {/* recommended plan */}
          {plannerOn && (
            <section>
              <h2 className="mb-3 flex items-center gap-2 text-base font-bold text-navy-900">
                <Sparkles className="size-4.5 text-brand-500" /> Recommended for your next journey
              </h2>
              {rec && topOption ? (
                <div className="space-y-3">
                  <p className="text-sm text-muted-foreground">
                    {rec.request.sourceCity} → {rec.request.destinationCity} · {shortDate(rec.request.travelDate)}
                  </p>
                  <OptionCard option={topOption} travelDate={rec.request.travelDate} route={{ from: rec.request.sourceCity, to: rec.request.destinationCity }} compact />
                  <Button
                    variant="outline"
                    onClick={() => {
                      plan.setPlan(rec.request, rec, null)
                      navigate('/app/results')
                    }}
                  >
                    See all {rec.options.length} options <ArrowRight />
                  </Button>
                </div>
              ) : (
                <EmptyState
                  icon={MapPinned}
                  title="Plan your first journey"
                  body="Tell us where you’re headed and we’ll compare the options."
                  action={
                    <Button asChild variant="brand">
                      <Link to="/app/plan">Plan your journey</Link>
                    </Button>
                  }
                />
              )}
            </section>
          )}

          {/* upcoming */}
          <section>
            <h2 className="mb-3 flex items-center gap-2 text-base font-bold text-navy-900">
              <CalendarDays className="size-4.5 text-brand-500" /> Upcoming journeys
            </h2>
            {d.upcomingJourneys.length === 0 ? (
              <Notice tone="info">No journeys saved yet. Plan one and keep it here.</Notice>
            ) : (
              <div className="space-y-3">
                {d.upcomingJourneys.map((t) => (
                  <Card key={t.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 p-4">
                    <div className="min-w-0 flex-1">
                      <p className="flex items-center gap-2 font-bold text-navy-900">
                        {t.sourceCity} <ArrowRight className="size-3.5 text-navy-400" /> {t.destinationCity}
                      </p>
                      <p className="text-sm text-muted-foreground">
                        {shortDate(t.travelDate)} · {t.travellers} traveller{t.travellers > 1 ? 's' : ''} · {modeLabel[t.preferredMode]}
                      </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      {t.dataOrigin === 'DEMO_SEED' && <Badge tone="amber">Demo data</Badge>}
                      <AlertMeButton tripId={t.id} />
                      <Badge tone={t.visibility === 'DISCOVERABLE' ? 'green' : 'outline'}>{t.visibility === 'DISCOVERABLE' ? 'Visible' : 'Private'}</Badge>
                      {companionsOn && (
                        <Button asChild size="sm" variant="softBrand">
                          <Link to={`/app/travellers?tripId=${t.id}`}>
                            <Users /> Companions
                          </Link>
                        </Button>
                      )}
                    </div>
                  </Card>
                ))}
              </div>
            )}
          </section>

          {/* saved */}
          {d.savedTrips.length > 0 && (
            <section>
              <h2 className="mb-3 flex items-center gap-2 text-base font-bold text-navy-900">
                <Bookmark className="size-4.5 text-brand-500" /> Saved options
              </h2>
              <div className="grid gap-3 sm:grid-cols-2">
                {d.savedTrips.slice(0, 4).map((s) => (
                  <Card key={s.id} className="p-4">
                    <p className="truncate text-sm font-bold text-navy-900">{s.title}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {shortDate(s.travelDate)} · {rupees(s.option.costPerPerson)} per person
                    </p>
                    <div className="mt-2">
                      <SourceBadge source={s.option.dataSource} />
                    </div>
                  </Card>
                ))}
              </div>
            </section>
          )}
        </div>

        <aside className="space-y-6">
          <RideAlertsCard />
          {companionsOn && (
            <Card>
              <CardHeader className="flex-row items-center justify-between pb-2">
                <CardTitle>Compatible travellers</CardTitle>
                <Button asChild variant="link" size="sm">
                  <Link to="/app/travellers">All</Link>
                </Button>
              </CardHeader>
              <CardContent className="space-y-3">
                {d.compatibleTravellers.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No matches yet. Make a journey visible to be found.</p>
                ) : (
                  d.compatibleTravellers.slice(0, 3).map((m) => (
                    <Link key={m.tripId} to={`/app/travellers/${m.userId}`} className="flex items-center gap-3 rounded-xl p-1.5 transition-colors hover:bg-navy-50">
                      <Avatar name={m.name} />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-bold text-navy-900">{m.name}</p>
                        <p className="truncate text-xs text-muted-foreground">
                          {m.sourceCity} → {m.destinationCity} · {shortDate(m.travelDate)}
                        </p>
                      </div>
                      <div className="scale-75">
                        <ScoreBadge score={m.score} />
                      </div>
                    </Link>
                  ))
                )}
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader className="flex-row items-center justify-between pb-2">
              <CardTitle>Your groups</CardTitle>
              <Button asChild variant="link" size="sm">
                <Link to="/app/groups">All</Link>
              </Button>
            </CardHeader>
            <CardContent className="space-y-2.5">
              {d.groupInvitations.map((g) => (
                <Link key={g.id} to={`/app/groups/${g.id}`} className="block rounded-xl border border-brand-200 bg-brand-50/60 p-3">
                  <p className="text-sm font-bold text-navy-900">{g.name}</p>
                  <p className="text-xs text-brand-700">Invitation from {g.ownerName}</p>
                </Link>
              ))}
              {d.myGroups.length === 0 && d.groupInvitations.length === 0 ? (
                <p className="text-sm text-muted-foreground">You aren’t in any group yet.</p>
              ) : (
                d.myGroups.slice(0, 3).map((g) => (
                  <Link key={g.id} to={`/app/groups/${g.id}`} className="flex items-center justify-between gap-2 rounded-xl p-2 transition-colors hover:bg-navy-50">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-bold text-navy-900">{g.name}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {g.sourceCity} → {g.destinationCity} · {g.joinedMembers} joined
                      </p>
                    </div>
                    <Badge tone={groupStatusTone[g.status]}>{g.status.toLowerCase()}</Badge>
                  </Link>
                ))
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-2">
                <Clock className="size-4 text-navy-400" /> Recent activity
              </CardTitle>
            </CardHeader>
            <CardContent>
              {d.recentActivity.length === 0 ? (
                <p className="text-sm text-muted-foreground">Nothing yet.</p>
              ) : (
                <ul className="space-y-3">
                  {d.recentActivity.slice(0, 6).map((a, i) => (
                    <li key={i} className="flex gap-2.5 text-[13px]">
                      <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-brand-400" />
                      <div>
                        <p className="text-navy-800">{a.message}</p>
                        <p className="text-[11px] text-muted-foreground">{relativeTime(a.createdAt)}</p>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </aside>
      </div>
    </>
  )
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-16 w-80" />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-20" />
        ))}
      </div>
      <Skeleton className="h-64" />
    </div>
  )
}
