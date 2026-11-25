import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, ChevronDown, FlaskConical, MapPin, Wallet } from 'lucide-react'
import { Avatar } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { modeLabel, rupees, shortDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { MatchCandidate } from '@/lib/types'

const factorLabel: Record<string, string> = {
  destination: 'Destination',
  date: 'Travel date',
  start: 'Starting point',
  budget: 'Budget',
  transport: 'Transport',
}

export function scoreTone(score: number) {
  if (score >= 80) return { ring: 'text-emerald-700 bg-emerald-50 border-emerald-200', bar: 'bg-emerald-500', word: 'Excellent' }
  if (score >= 60) return { ring: 'text-brand-700 bg-brand-50 border-brand-200', bar: 'bg-brand-500', word: 'Good' }
  return { ring: 'text-slate-600 bg-slate-50 border-slate-200', bar: 'bg-slate-400', word: 'Possible' }
}

export function ScoreBadge({ score }: { score: number }) {
  const t = scoreTone(score)
  return (
    <div className={cn('flex size-16 shrink-0 flex-col items-center justify-center rounded-2xl border', t.ring)} aria-label={`Compatibility ${score} out of 100`}>
      <span className="font-display text-xl font-extrabold leading-none">{score}</span>
      <span className="mt-0.5 text-[10px] font-bold uppercase tracking-wide">{t.word}</span>
    </div>
  )
}

interface Props {
  match: MatchCandidate
  weights?: Record<string, number>
  actions?: ReactNode
}

/** Privacy-conscious by construction: the API only ever returns a first name and a neighbourhood. */
export function MatchCard({ match: m, weights, actions }: Props) {
  const [open, setOpen] = useState(false)
  const t = scoreTone(m.score)
  return (
    <Card className="overflow-hidden">
      <div className="p-4 sm:p-5">
        <div className="flex items-start gap-3.5">
          <Avatar name={m.name} className="size-12 text-base" />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <Link to={`/app/travellers/${m.userId}`} className="truncate text-[15px] font-bold text-navy-900 hover:text-brand-600">
                {m.name}
              </Link>
              {m.demoData && (
                <Badge tone="amber">
                  <FlaskConical /> Demo traveller
                </Badge>
              )}
            </div>
            <p className="flex items-center gap-1 text-xs text-muted-foreground">
              <MapPin className="size-3" /> Starts near {m.sourceArea || m.homeArea}
            </p>
            <p className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm font-semibold text-navy-900">
              {m.sourceCity} <ArrowRight className="size-3.5 text-navy-400" /> {m.destinationCity}
              <span className="font-medium text-muted-foreground">· {shortDate(m.travelDate)}</span>
            </p>
            <div className="mt-2 flex flex-wrap gap-1.5">
              <Badge tone="outline">{modeLabel[m.preferredMode]}</Badge>
              {m.seatsAvailable > 0 && <Badge tone="green">{m.seatsAvailable} seat{m.seatsAvailable > 1 ? 's' : ''} to share</Badge>}
              {m.costPerSeat != null && <Badge tone="outline">{rupees(m.costPerSeat)}/seat</Badge>}
              {m.maxBudget != null && (
                <Badge tone="outline">
                  <Wallet /> up to {rupees(m.maxBudget)}
                </Badge>
              )}
              {m.algorithm.toLowerCase().includes('v2') && <Badge tone="violet">Route-aware</Badge>}
            </div>
          </div>
          <ScoreBadge score={m.score} />
        </div>

        {m.reasons.length > 0 && (
          <ul className="mt-3.5 space-y-1">
            {m.reasons.slice(0, open ? undefined : 2).map((r) => (
              <li key={r} className="flex gap-2 text-[13px] text-navy-700">
                <span className={cn('mt-1.5 size-1.5 shrink-0 rounded-full', t.bar)} />
                {r}
              </li>
            ))}
          </ul>
        )}

        {open && (
          <div className="mt-4 space-y-2.5 border-t border-border pt-4">
            <p className="text-xs font-bold uppercase tracking-wide text-muted-foreground">Why {m.score}/100</p>
            {Object.entries(m.breakdown).map(([k, v]) => (
              <div key={k} className="grid grid-cols-[6.5rem_1fr_auto] items-center gap-3 text-[13px]">
                <span className="font-medium text-navy-800">{factorLabel[k] ?? k}</span>
                <Progress value={v} indicatorClassName={t.bar} />
                <span className="w-24 text-right text-xs text-muted-foreground">
                  {v}% fit{weights?.[k] !== undefined ? ` · wt ${weights[k]}` : ''}
                </span>
              </div>
            ))}
            {m.notes && <p className="rounded-lg bg-navy-50 px-3 py-2 text-[13px] text-navy-700">“{m.notes}”</p>}
          </div>
        )}
      </div>
      <div className="flex flex-wrap items-center gap-2 border-t border-border bg-navy-50/40 px-4 py-2.5 sm:px-5">
        <Button variant="ghost" size="sm" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
          <ChevronDown className={cn('transition-transform', open && 'rotate-180')} /> {open ? 'Hide score' : 'Score breakdown'}
        </Button>
        <div className="ml-auto flex flex-wrap gap-2">{actions}</div>
      </div>
    </Card>
  )
}
