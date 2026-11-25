import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, Bus, Compass, FlaskConical, Languages, MapPinned, Mic, Navigation, ShieldCheck, Sparkles, TrainFront, Users, UsersRound, Wallet } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { Logo } from '@/components/Logo'
import { SourceBadge } from '@/components/common'
import { useAuth } from '@/lib/auth'
import { ApiError } from '@/lib/api'

const festivals = ['Diwali', 'Chhath Puja', 'Durga Puja', 'Dussehra', 'Pongal', 'Sankranti', 'Holi', 'Eid', 'Onam', 'Christmas']

export default function Landing() {
  const { user, demoLogin } = useAuth()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)

  const go = (path: string) => navigate(user ? path : `/register?next=${encodeURIComponent(path)}`)

  const tryDemo = async () => {
    setBusy(true)
    try {
      await demoLogin()
      navigate('/app')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not start the demo.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-h-dvh bg-white">
      <header className="absolute inset-x-0 top-0 z-30">
        <div className="mx-auto flex h-18 max-w-6xl items-center justify-between px-4 sm:px-6">
          <Logo />
          <div className="flex items-center gap-2">
            {user ? (
              <Button asChild variant="default" size="sm">
                <Link to="/app">Open dashboard</Link>
              </Button>
            ) : (
              <>
                <Button asChild variant="ghost" size="sm">
                  <Link to="/login">Sign in</Link>
                </Button>
                <Button asChild variant="default" size="sm" className="hidden sm:inline-flex">
                  <Link to="/register">Create account</Link>
                </Button>
              </>
            )}
          </div>
        </div>
      </header>

      {/* hero */}
      <section className="bg-hero relative overflow-hidden pb-16 pt-28 sm:pb-24 sm:pt-36">
        <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 sm:px-6 lg:grid-cols-[1.1fr_0.9fr]">
          <div className="animate-fade-up">
            <Badge tone="brand" className="mb-5 px-3 py-1 text-[13px]">
              🪔 Built for festival travel across India
            </Badge>
            <h1 className="text-4xl font-extrabold leading-[1.08] text-navy-900 sm:text-6xl">
              Every journey <span className="text-brand-500">home</span> starts here.
            </h1>
            <p className="mt-5 max-w-xl text-lg leading-relaxed text-navy-600">
              Tell GharPahucho where you’re going, in English, తెలుగు or हिन्दी. We compare real routes, find people travelling your way, and help you
              travel together for less.
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Button size="lg" variant="brand" onClick={() => go('/app/plan')}>
                Plan your journey <ArrowRight />
              </Button>
              <Button size="lg" variant="outline" onClick={() => go('/app/travellers')}>
                <Users /> Find travel companions
              </Button>
            </div>
            <button
              onClick={tryDemo}
              disabled={busy}
              className="mt-5 inline-flex items-center gap-1.5 text-sm font-semibold text-navy-600 underline-offset-4 hover:text-brand-600 hover:underline disabled:opacity-60"
            >
              <Sparkles className="size-4" /> {busy ? 'Starting demo…' : 'Or explore with the demo account, no sign-up'}
            </button>
          </div>

          <HeroCard />
        </div>
      </section>

      {/* festival strip */}
      <section className="border-y border-border bg-navy-950 py-4">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-x-7 gap-y-2 px-4 text-sm font-semibold text-navy-200">
          {festivals.map((f) => (
            <span key={f} className="opacity-80">
              {f}
            </span>
          ))}
        </div>
      </section>

      {/* how it works */}
      <section className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
        <div className="mx-auto max-w-2xl text-center">
          <p className="text-xs font-bold uppercase tracking-wider text-brand-600">How it works</p>
          <h2 className="mt-2 text-3xl font-extrabold text-navy-900 sm:text-4xl">From “I need to get home” to a plan in a minute</h2>
        </div>
        <div className="mt-12 grid gap-5 md:grid-cols-3">
          {[
            { icon: Compass, title: 'Say where and when', body: 'Type it, pick it, or just speak. “Naaku December 20th Bangalore nunchi Hyderabad vellali. Budget 1500.” works.' },
            { icon: Navigation, title: 'Compare honest options', body: 'Cost, duration, transfers and reasons for every option, with the source of each number shown, always.' },
            { icon: UsersRound, title: 'Travel together', body: 'Match with people on the same corridor, form a group, plan the ride and split the cost.' },
          ].map((s, i) => (
            <Card key={s.title} className="p-6">
              <div className="mb-4 flex items-center gap-3">
                <span className="inline-flex size-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                  <s.icon className="size-5" />
                </span>
                <span className="font-display text-sm font-bold text-navy-300">0{i + 1}</span>
              </div>
              <h3 className="text-lg font-bold text-navy-900">{s.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{s.body}</p>
            </Card>
          ))}
        </div>
      </section>

      {/* features */}
      <section className="bg-muted/70 py-20">
        <div className="mx-auto max-w-6xl px-4 sm:px-6">
          <div className="grid gap-5 sm:grid-cols-2">
            {[
              { icon: MapPinned, title: 'Smart trip planner', body: 'Cheapest, fastest, best overall, fewest transfers or within budget, scored by transparent rules rather than a black box.' },
              { icon: Users, title: 'Companion matching', body: 'A 0–100 compatibility score built from destination, date, starting point, budget and transport, with the reasons shown.' },
              { icon: Languages, title: 'Speaks your language', body: 'A multilingual assistant with voice input. Telugu and Hindi first, grounded in real planner results, never made up.' },
              { icon: Wallet, title: 'Cost sharing that adds up', body: 'See what a shared ride costs per person, and when moving your date by a day or two would save money.' },
            ].map((f) => (
              <Card key={f.title} className="flex gap-4 p-6">
                <span className="inline-flex size-12 shrink-0 items-center justify-center rounded-xl bg-navy-900 text-white">
                  <f.icon className="size-5" />
                </span>
                <div>
                  <h3 className="text-lg font-bold text-navy-900">{f.title}</h3>
                  <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{f.body}</p>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* honesty */}
      <section className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
        <div className="grid items-center gap-10 lg:grid-cols-2">
          <div>
            <p className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-brand-600">
              <ShieldCheck className="size-4" /> Honest by design
            </p>
            <h2 className="mt-2 text-3xl font-extrabold text-navy-900 sm:text-4xl">We never pass off sample data as real-time</h2>
            <p className="mt-4 leading-relaxed text-navy-600">
              Every result carries a label that says exactly what it is. Routes are really calculated. Rides come from real travellers. Train and bus
              timetables here are simulated samples for demonstration, and we say so on every card.
            </p>
          </div>
          <Card className="space-y-3.5 p-6">
            {[
              { s: 'ROUTE_CALCULATION' as const, t: 'Distance and drive time from a routing engine; fare is an estimate.', i: Navigation },
              { s: 'USER_TRIP' as const, t: 'Rides posted by travellers on GharPahucho.', i: Users },
              { s: 'SIMULATED_INVENTORY' as const, t: 'Simulated train and bus services. Not real-time, not bookable.', i: FlaskConical },
            ].map((r) => (
              <div key={r.s} className="flex items-start gap-3">
                <SourceBadge source={r.s} className="mt-0.5 shrink-0" />
                <p className="text-sm text-navy-700">{r.t}</p>
              </div>
            ))}
          </Card>
        </div>
      </section>

      {/* CTA */}
      <section className="px-4 pb-20 sm:px-6">
        <div className="mx-auto max-w-6xl overflow-hidden rounded-3xl bg-navy-900 px-6 py-14 text-center sm:px-12">
          <h2 className="text-3xl font-extrabold text-white sm:text-4xl">Ready to head home?</h2>
          <p className="mx-auto mt-3 max-w-xl text-navy-200">Plan the trip, find your people, and make the journey part of the celebration.</p>
          <div className="mt-7 flex flex-col justify-center gap-3 sm:flex-row">
            <Button size="lg" variant="brand" onClick={() => go('/app/plan')}>
              Plan your journey <ArrowRight />
            </Button>
            <Button size="lg" variant="outline" className="border-white/20 bg-transparent text-white hover:bg-white/10 hover:text-white" onClick={() => go('/app/travellers')}>
              Find travel companions
            </Button>
          </div>
        </div>
      </section>

      <footer className="border-t border-border py-8">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-3 px-4 text-sm text-muted-foreground sm:flex-row sm:px-6">
          <Logo />
          <p>Every journey home starts here. Route data © OpenStreetMap contributors.</p>
        </div>
      </footer>
    </div>
  )
}

/** A static preview. It is illustrative and labelled as such. */
function HeroCard() {
  return (
    <div className="relative animate-fade-up [animation-delay:120ms]">
      <div className="absolute -inset-4 -z-10 rounded-[2rem] bg-gradient-to-br from-brand-200/50 to-navy-200/40 blur-2xl" />
      <Card className="p-5 shadow-pop">
        <div className="flex items-center justify-between">
          <p className="text-xs font-bold uppercase tracking-wide text-muted-foreground">Example journey</p>
          <Badge tone="amber">
            <FlaskConical /> Illustration
          </Badge>
        </div>
        <div className="mt-3 flex items-center gap-3 font-display text-lg font-extrabold text-navy-900">
          Bengaluru <ArrowRight className="size-4 text-brand-500" /> Hyderabad
        </div>
        <p className="text-xs text-muted-foreground">20 Dec · 1 traveller · budget ₹1,500</p>

        <div className="mt-4 space-y-2.5">
          {[
            { icon: TrainFront, t: 'Overnight train', d: '≈ 11h · direct', tag: 'Cheapest', tone: 'green' as const },
            { icon: Users, t: 'Shared car with a traveller', d: '≈ 9h · 3 seats', tag: 'Best overall', tone: 'brand' as const },
            { icon: Bus, t: 'Sleeper bus', d: '≈ 10h · direct', tag: 'Within budget', tone: 'neutral' as const },
          ].map((o) => (
            <div key={o.t} className="flex items-center gap-3 rounded-xl border border-border bg-white p-3">
              <span className="inline-flex size-10 items-center justify-center rounded-lg bg-navy-50 text-navy-700">
                <o.icon className="size-5" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-bold text-navy-900">{o.t}</p>
                <p className="text-xs text-muted-foreground">{o.d}</p>
              </div>
              <Badge tone={o.tone}>{o.tag}</Badge>
            </div>
          ))}
        </div>

        <div className="mt-4 flex items-center gap-2 rounded-xl bg-navy-900 px-4 py-3 text-sm text-white">
          <Mic className="size-4 text-brand-400" />
          <span className="truncate">“Naaku Dec 20 Bangalore nunchi Hyderabad vellali”</span>
        </div>
      </Card>
    </div>
  )
}
