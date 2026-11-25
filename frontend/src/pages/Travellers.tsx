import { useEffect, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Eye, EyeOff, HandHeart, Send, Undo2, Users, X } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Select, Textarea } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Switch } from '@/components/ui/switch'
import { Avatar } from '@/components/ui/avatar'
import { EmptyState, ErrorState, FeatureOff, Notice, PageHeader } from '@/components/common'
import { MatchCard } from '@/components/MatchCard'
import { api, ApiError } from '@/lib/api'
import { useFeatures } from '@/lib/features'
import { relativeTime, shortDate } from '@/lib/format'
import { FLAG, type Interest, type InterestStatus, type MatchCandidate } from '@/lib/types'

const statusTone: Record<InterestStatus, 'amber' | 'green' | 'red' | 'neutral'> = { PENDING: 'amber', ACCEPTED: 'green', REJECTED: 'red', WITHDRAWN: 'neutral' }
const statusText: Record<InterestStatus, string> = { PENDING: 'Request sent', ACCEPTED: 'Matched', REJECTED: 'Declined', WITHDRAWN: 'Withdrawn' }

export default function Travellers() {
  const { isEnabled } = useFeatures()
  const [params, setParams] = useSearchParams()
  const qc = useQueryClient()
  const on = isEnabled(FLAG.companions)

  const trips = useQuery({ queryKey: ['trips'], queryFn: () => api.trips(0, 50), enabled: on })
  const planned = (trips.data?.content ?? []).filter((t) => t.status === 'PLANNED')
  const tripId = params.get('tripId') ?? planned[0]?.id
  const trip = planned.find((t) => t.id === tripId)

  useEffect(() => {
    if (!params.get('tripId') && planned[0]) setParams({ tripId: planned[0].id }, { replace: true })
  }, [params, planned, setParams])

  const matches = useQuery({ queryKey: ['matches', tripId], queryFn: () => api.matches(tripId!, 0, 24), enabled: on && Boolean(tripId) })
  const inbox = useQuery({ queryKey: ['inbox'], queryFn: api.inbox, enabled: on, refetchInterval: 15_000 })

  const [target, setTarget] = useState<MatchCandidate | null>(null)
  const [message, setMessage] = useState('')

  const express = useMutation({
    mutationFn: () => api.express({ fromTripId: tripId!, toTripId: target!.tripId, message: message.trim() || undefined }),
    onSuccess: () => {
      toast.success(`Request sent to ${target?.name}`)
      setTarget(null)
      setMessage('')
      void qc.invalidateQueries({ queryKey: ['matches'] })
      void qc.invalidateQueries({ queryKey: ['inbox'] })
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not send the request.'),
  })

  const visibility = useMutation({
    mutationFn: (discoverable: boolean) => api.patchTrip(tripId!, { visibility: discoverable ? 'DISCOVERABLE' : 'PRIVATE' }),
    onSuccess: (t) => {
      toast.success(t.visibility === 'DISCOVERABLE' ? 'Others going your way can now find this journey.' : 'This journey is private again.')
      void qc.invalidateQueries({ queryKey: ['trips'] })
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not update visibility.'),
  })

  const respond = useMutation({
    mutationFn: (v: { id: string; accept: boolean }) => api.respond(v.id, v.accept),
    onSuccess: (i, v) => {
      toast.success(v.accept ? `You’re matched with ${i.otherName}` : 'Request declined')
      void qc.invalidateQueries({ queryKey: ['inbox'] })
      void qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not respond.'),
  })

  const withdraw = useMutation({
    mutationFn: (id: string) => api.withdraw(id),
    onSuccess: () => {
      toast.success('Request withdrawn')
      void qc.invalidateQueries({ queryKey: ['inbox'] })
      void qc.invalidateQueries({ queryKey: ['matches'] })
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not withdraw.'),
  })

  if (!on) {
    return (
      <>
        <PageHeader title="Travel companions" />
        <FeatureOff name="Travel companion matching" />
      </>
    )
  }

  const pending = (inbox.data?.incoming ?? []).filter((i) => i.status === 'PENDING').length

  return (
    <>
      <PageHeader eyebrow="Travel companions" title="Find people going your way" subtitle="Matched on destination, date, starting point, budget and transport. You only ever see a first name and a neighbourhood." />

      <Tabs defaultValue="discover">
        <TabsList>
          <TabsTrigger value="discover">Discover</TabsTrigger>
          <TabsTrigger value="requests">
            Requests {pending > 0 && <Badge tone="brand">{pending}</Badge>}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="discover" className="space-y-4">
          {trips.isLoading ? (
            <Skeleton className="h-24" />
          ) : trips.isError ? (
            <ErrorState error={trips.error} onRetry={() => trips.refetch()} />
          ) : planned.length === 0 ? (
            <EmptyState
              icon={Users}
              title="Plan a journey first"
              body="Companions are matched against one of your saved journeys."
              action={
                <Button asChild variant="brand">
                  <Link to="/app/plan">Plan your journey</Link>
                </Button>
              }
            />
          ) : (
            <>
              <Card className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center">
                <div className="flex-1">
                  <label htmlFor="trip" className="text-[13px] font-semibold text-navy-800">
                    Matching for
                  </label>
                  <Select id="trip" value={tripId} onChange={(e) => setParams({ tripId: e.target.value })} className="mt-1.5">
                    {planned.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.sourceCity} → {t.destinationCity} · {shortDate(t.travelDate)}
                      </option>
                    ))}
                  </Select>
                </div>
                {trip && (
                  <div className="flex items-center gap-3 rounded-xl bg-navy-50 px-4 py-3">
                    {trip.visibility === 'DISCOVERABLE' ? <Eye className="size-5 text-emerald-600" /> : <EyeOff className="size-5 text-navy-400" />}
                    <div className="mr-2">
                      <p className="text-sm font-semibold text-navy-900">{trip.visibility === 'DISCOVERABLE' ? 'Visible to others' : 'Private'}</p>
                      <p className="text-xs text-muted-foreground">{trip.visibility === 'DISCOVERABLE' ? 'Appears in their matches' : 'Only you can see it'}</p>
                    </div>
                    <Switch checked={trip.visibility === 'DISCOVERABLE'} onCheckedChange={(v) => visibility.mutate(v)} disabled={visibility.isPending} aria-label="Let others find this journey" />
                  </div>
                )}
              </Card>

              {matches.isLoading ? (
                <div className="space-y-4">
                  {[0, 1, 2].map((i) => (
                    <Skeleton key={i} className="h-44" />
                  ))}
                </div>
              ) : matches.isError ? (
                <ErrorState error={matches.error} onRetry={() => matches.refetch()} />
              ) : matches.data ? (
                <>
                  <div className="flex flex-wrap items-center gap-2 text-[13px] text-muted-foreground">
                    <Badge tone={matches.data.algorithm.toLowerCase().includes('v2') ? 'violet' : 'outline'}>
                      {matches.data.algorithm.toLowerCase().includes('v2') ? 'Route-aware algorithm (experimental)' : 'Standard algorithm'}
                    </Badge>
                    <span>
                      Showing {matches.data.matches.length} of {matches.data.total} · min score {matches.data.minScore} · within {matches.data.radiusKm} km
                    </span>
                  </div>
                  {matches.data.notices.map((n) => (
                    <Notice key={n} tone="info">
                      {n}
                    </Notice>
                  ))}
                  {matches.data.matches.length === 0 ? (
                    <EmptyState
                      icon={Users}
                      title="No compatible travellers yet"
                      body="Try making your journey visible, widening your date flexibility, or check back closer to the festival."
                    />
                  ) : (
                    <div className="grid gap-4 lg:grid-cols-2">
                      {matches.data.matches.map((m) => (
                        <MatchCard
                          key={m.tripId}
                          match={m}
                          weights={matches.data!.weights}
                          actions={
                            m.interestStatus ? (
                              <Badge tone={statusTone[m.interestStatus]} className="py-1.5">
                                {statusText[m.interestStatus]}
                              </Badge>
                            ) : (
                              <Button size="sm" variant="brand" onClick={() => setTarget(m)}>
                                <HandHeart /> I’m interested
                              </Button>
                            )
                          }
                        />
                      ))}
                    </div>
                  )}
                </>
              ) : null}
            </>
          )}
        </TabsContent>

        <TabsContent value="requests" className="space-y-6">
          {inbox.isLoading ? (
            <Skeleton className="h-40" />
          ) : inbox.isError ? (
            <ErrorState error={inbox.error} onRetry={() => inbox.refetch()} />
          ) : (
            <>
              <section>
                <h2 className="mb-3 text-base font-bold text-navy-900">Received</h2>
                {inbox.data!.incoming.length === 0 ? (
                  <EmptyState icon={Send} title="No requests yet" body="When someone is interested in travelling with you, it will show up here." />
                ) : (
                  <div className="space-y-3">
                    {inbox.data!.incoming.map((i) => (
                      <RequestRow
                        key={i.id}
                        i={i}
                        actions={
                          i.status === 'PENDING' ? (
                            <>
                              <Button size="sm" variant="outline" onClick={() => respond.mutate({ id: i.id, accept: false })} disabled={respond.isPending}>
                                <X /> Decline
                              </Button>
                              <Button size="sm" variant="brand" onClick={() => respond.mutate({ id: i.id, accept: true })} disabled={respond.isPending}>
                                <Check /> Accept
                              </Button>
                            </>
                          ) : undefined
                        }
                      />
                    ))}
                  </div>
                )}
              </section>
              <section>
                <h2 className="mb-3 text-base font-bold text-navy-900">Sent</h2>
                {inbox.data!.outgoing.length === 0 ? (
                  <EmptyState icon={Send} title="Nothing sent" body="Express interest from the Discover tab." />
                ) : (
                  <div className="space-y-3">
                    {inbox.data!.outgoing.map((i) => (
                      <RequestRow
                        key={i.id}
                        i={i}
                        actions={
                          i.status === 'PENDING' ? (
                            <Button size="sm" variant="ghost" onClick={() => withdraw.mutate(i.id)} disabled={withdraw.isPending}>
                              <Undo2 /> Withdraw
                            </Button>
                          ) : undefined
                        }
                      />
                    ))}
                  </div>
                )}
              </section>
            </>
          )}
        </TabsContent>
      </Tabs>

      <Dialog open={target !== null} onOpenChange={(o) => !o && setTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Travel with {target?.name}?</DialogTitle>
            <DialogDescription>
              {target?.sourceCity} → {target?.destinationCity} · {target && shortDate(target.travelDate)}. They’ll see your first name and journey. Contact details are never shared.
            </DialogDescription>
          </DialogHeader>
          <Textarea value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Add a short message (optional)" maxLength={300} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setTarget(null)}>
              Cancel
            </Button>
            <Button variant="brand" onClick={() => express.mutate()} loading={express.isPending}>
              Send request
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

function RequestRow({ i, actions }: { i: Interest; actions?: ReactNode }) {
  return (
    <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
      <div className="flex min-w-0 flex-1 items-start gap-3">
        <Avatar name={i.otherName} />
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <Link to={`/app/travellers/${i.otherUserId}`} className="font-bold text-navy-900 hover:text-brand-600">
              {i.otherName}
            </Link>
            <Badge tone={statusTone[i.status]}>{statusText[i.status]}</Badge>
            <span className="text-xs text-muted-foreground">{relativeTime(i.createdAt)}</span>
          </div>
          <p className="text-sm text-navy-700">
            {i.sourceCity} → {i.destinationCity} · {shortDate(i.travelDate)}
          </p>
          {i.message && <p className="mt-1 text-[13px] italic text-muted-foreground">“{i.message}”</p>}
        </div>
      </div>
      <div className="flex shrink-0 flex-wrap gap-2">
        {actions}
        {i.status === 'ACCEPTED' && i.groupId && (
          <Button asChild size="sm" variant="softBrand">
            <Link to={`/app/groups/${i.groupId}`}>Open group</Link>
          </Button>
        )}
      </div>
    </Card>
  )
}
