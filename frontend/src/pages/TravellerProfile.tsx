import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, Languages, MapPin, ShieldCheck } from 'lucide-react'
import { Avatar } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { EmptyState, ErrorState, Notice } from '@/components/common'
import { api } from '@/lib/api'
import { languages, modeLabel, rupees, shortDate } from '@/lib/format'

export default function TravellerProfile() {
  const { userId } = useParams()
  const navigate = useNavigate()
  const q = useQuery({ queryKey: ['public-profile', userId], queryFn: () => api.publicProfile(userId!), enabled: Boolean(userId) })

  return (
    <div className="mx-auto max-w-3xl">
      <Button variant="ghost" size="sm" className="mb-4 -ml-2" onClick={() => navigate(-1)}>
        <ArrowLeft /> Back
      </Button>

      {q.isLoading ? (
        <Skeleton className="h-64" />
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={() => q.refetch()} title="Couldn’t load this traveller" />
      ) : q.data ? (
        <>
          <Card className="p-6">
            <div className="flex items-center gap-4">
              <Avatar name={q.data.name} className="size-16 text-xl" />
              <div>
                <h1 className="text-2xl font-extrabold text-navy-900">{q.data.name}</h1>
                <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
                  <span className="inline-flex items-center gap-1">
                    <MapPin className="size-3.5" /> {q.data.homeArea || 'Area not shared'}
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <Languages className="size-3.5" /> {languages.find((l) => l.code === q.data.preferredLanguage)?.label ?? q.data.preferredLanguage}
                  </span>
                  <span>Member since {shortDate(q.data.memberSince)}</span>
                </div>
              </div>
            </div>
          </Card>

          <Notice tone="info" className="mt-4">
            <span className="inline-flex items-center gap-1.5 font-semibold">
              <ShieldCheck className="size-4" /> Privacy
            </span>{' '}
            Only a first name, neighbourhood and journeys they chose to make visible are shown here. There are no addresses or contact details.
          </Notice>

          <Card className="mt-5">
            <CardHeader>
              <CardTitle>Upcoming journeys</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {q.data.upcomingTrips.length === 0 ? (
                <EmptyState title="No visible journeys" body="This traveller hasn’t made any upcoming journey visible." />
              ) : (
                q.data.upcomingTrips.map((t) => (
                  <div key={t.tripId} className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-xl border border-border p-3.5">
                    <p className="flex items-center gap-2 font-bold text-navy-900">
                      {t.sourceCity} <ArrowRight className="size-3.5 text-navy-400" /> {t.destinationCity}
                    </p>
                    <span className="text-sm text-muted-foreground">
                      {shortDate(t.travelDate)} · starts near {t.sourceArea}
                    </span>
                    <div className="ml-auto flex gap-1.5">
                      <Badge tone="outline">{modeLabel[t.preferredMode]}</Badge>
                      {t.seatsAvailable > 0 && <Badge tone="green">{t.seatsAvailable} seats</Badge>}
                      {t.costPerSeat != null && <Badge tone="outline">{rupees(t.costPerSeat)}/seat</Badge>}
                    </div>
                  </div>
                ))
              )}
            </CardContent>
          </Card>

          <div className="mt-5 flex justify-end">
            <Button asChild variant="brand">
              <Link to="/app/travellers">Back to matches</Link>
            </Button>
          </div>
        </>
      ) : null}
    </div>
  )
}
