import { Link } from 'react-router-dom'
import { Check, FlaskConical, PartyPopper, ShieldCheck, Ticket } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ErrorState, Notice, PageHeader } from '@/components/common'
import { rupees } from '@/lib/format'
import { usePlanChange, usePlans } from '@/lib/pass'
import { cn } from '@/lib/utils'

/**
 * Festival Pass page. In demo mode the checkout takes no payment details at all: one click upgrades the account.
 * (A real launch would swap the button for a payment provider; the page and the plan logic stay the same.)
 */
export default function Pass() {
  const plans = usePlans()
  const { upgrade, downgrade } = usePlanChange()

  if (plans.isLoading) return <Skeleton className="h-96 w-full max-w-3xl" />
  if (plans.isError || !plans.data) return <ErrorState error={plans.error} onRetry={() => plans.refetch()} title="Couldn’t load the Festival Pass" />
  const p = plans.data
  const isPlus = p.currentPlan === 'plus'

  return (
    <>
      <PageHeader
        eyebrow="Festival Pass"
        title={isPlus ? 'You have the Festival Pass' : 'Travel home without the festival stress'}
        subtitle="Know when to book, hear about rides the moment they appear, and let your family follow your journey in their own language."
        actions={isPlus ? <Badge tone="violet">Plus · active</Badge> : undefined}
      />

      <div className="grid max-w-5xl gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-4">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle>What you get</CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              <div className="grid grid-cols-[1fr_6.5rem_6.5rem] gap-x-3 border-b border-border px-5 pb-2 text-[11px] font-bold uppercase tracking-wide text-navy-500 sm:grid-cols-[1fr_8rem_8rem]">
                <span />
                <span>Free</span>
                <span className="text-violet-700">Festival Pass</span>
              </div>
              <ul className="divide-y divide-border">
                {p.perks.map((perk) => (
                  <li key={perk.title} className="grid grid-cols-[1fr_6.5rem_6.5rem] items-start gap-x-3 px-5 py-3.5 sm:grid-cols-[1fr_8rem_8rem]">
                    <div className="min-w-0">
                      <p className="text-sm font-bold text-navy-900">{perk.title}</p>
                      <p className="mt-0.5 text-[13px] leading-relaxed text-navy-600">{perk.detail}</p>
                    </div>
                    <p className="text-[13px] text-navy-600">{perk.free}</p>
                    <p className="flex items-start gap-1 text-[13px] font-bold text-violet-700">
                      <Check className="mt-0.5 size-3.5 shrink-0" /> {perk.plus}
                    </p>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
          <p className="text-xs leading-relaxed text-muted-foreground">
            Crowd levels are estimates built from the festival calendar and the railway advance-booking window. They are not live fares or seat availability.
          </p>
        </div>

        <aside className="lg:sticky lg:top-24 lg:self-start">
          <Card className={cn(isPlus ? 'border-violet-300 bg-violet-50/50' : 'border-violet-200')}>
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-2">
                <Ticket className="size-4.5 text-violet-600" /> {isPlus ? 'Your plan' : 'Checkout'}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {isPlus ? (
                <>
                  <div className="flex items-start gap-3 rounded-xl bg-white p-3.5">
                    <PartyPopper className="mt-0.5 size-5 text-violet-600" />
                    <div>
                      <p className="text-sm font-bold text-navy-900">Festival Pass is on</p>
                      <p className="mt-0.5 text-[13px] text-navy-700">Your booking plan, unlimited ride alerts and family links are all unlocked.</p>
                    </div>
                  </div>
                  <div className="flex flex-col gap-2">
                    <Button asChild variant="brand">
                      <Link to="/app">Go to my dashboard</Link>
                    </Button>
                    <Button asChild variant="outline">
                      <Link to="/app/plan">Plan a journey</Link>
                    </Button>
                  </div>
                  {p.demoCheckoutAvailable && (
                    <Button variant="link" size="sm" onClick={() => downgrade.mutate()} loading={downgrade.isPending} className="text-muted-foreground">
                      Switch back to Free (demo)
                    </Button>
                  )}
                </>
              ) : (
                <>
                  <div className="rounded-xl bg-violet-50 p-3.5">
                    <p className="text-sm font-semibold text-navy-800">Festival Pass</p>
                    <p className="mt-0.5">
                      <span className="font-display text-3xl font-extrabold text-navy-900">{rupees(p.priceInr)}</span>
                      <span className="ml-1.5 text-sm text-navy-600">{p.period}</span>
                    </p>
                  </div>

                  {p.demoCheckoutAvailable ? (
                    <>
                      <Notice tone="sample">
                        <p className="font-semibold">Demo checkout</p>
                        <p className="mt-0.5">No card, UPI or bank details are asked for and nothing is charged. This simply switches the pass on for your account.</p>
                      </Notice>
                      <Button variant="brand" size="lg" className="w-full" onClick={() => upgrade.mutate()} loading={upgrade.isPending}>
                        <FlaskConical /> Continue with demo checkout
                      </Button>
                    </>
                  ) : (
                    <Notice tone="info">Checkout isn’t open in this environment yet.</Notice>
                  )}
                  <p className="flex items-start gap-1.5 text-[11px] leading-relaxed text-muted-foreground">
                    <ShieldCheck className="mt-0.5 size-3.5 shrink-0" /> You can switch back to Free at any time.
                  </p>
                </>
              )}
            </CardContent>
          </Card>
        </aside>
      </div>
    </>
  )
}
