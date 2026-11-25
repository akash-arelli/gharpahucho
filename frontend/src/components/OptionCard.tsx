import { useState, type ReactNode } from 'react'
import { ArrowRight, Bookmark, BookmarkCheck, Bus, Car, ChevronDown, ExternalLink, GitCompareArrows, Sparkles, TrainFront, TriangleAlert, Users } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { SourceBadge } from '@/components/common'
import { bookingLink } from '@/lib/bookingLinks'
import { duration, rupees, timeOf, shortDate, modeLabel, dayOffset } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { TransportMode, TravelOption } from '@/lib/types'

const modeIcon: Record<TransportMode, typeof Bus> = { TRAIN: TrainFront, BUS: Bus, SHARED_CAR: Users, CAB: Car, ANY: Car }

const tagMeta: Record<string, { label: string; tone: 'brand' | 'blue' | 'green' | 'neutral' | 'amber' | 'violet' }> = {
  BEST_OVERALL: { label: 'Best overall', tone: 'brand' },
  CHEAPEST: { label: 'Cheapest', tone: 'green' },
  FASTEST: { label: 'Fastest', tone: 'blue' },
  FEWEST_CHANGES: { label: 'Fewest changes', tone: 'violet' },
  WITHIN_BUDGET: { label: 'Within budget', tone: 'green' },
  ARRIVES_ON_TIME: { label: 'Arrives on time', tone: 'neutral' },
  ALTERNATIVE: { label: 'Alternative', tone: 'amber' },
}

interface Props {
  option: TravelOption
  travelDate: string
  rank?: number
  inCompare?: boolean
  compareDisabled?: boolean
  saved?: boolean
  onToggleCompare?: () => void
  onSave?: () => void
  saving?: boolean
  onExplain?: () => void
  explaining?: boolean
  explanation?: ReactNode
  explainEnabled?: boolean
  actions?: ReactNode
  compact?: boolean
  /** Journey cities, used to build the "check on the real site" link for trains and buses. */
  route?: { from: string; to: string }
}

export function OptionCard({
  option: o,
  travelDate,
  rank,
  inCompare,
  compareDisabled,
  saved,
  onToggleCompare,
  onSave,
  saving,
  onExplain,
  explaining,
  explanation,
  explainEnabled,
  actions,
  compact,
  route,
}: Props) {
  const [open, setOpen] = useState(false)
  const Icon = modeIcon[o.mode] ?? Car
  const arrivalDay = dayOffset(o.arrival, travelDate)
  const isAlt = !o.meetsConstraints
  const link = bookingLink(o.mode, route?.from, route?.to)

  return (
    <Card className={cn('overflow-hidden transition-shadow hover:shadow-pop', inCompare && 'ring-2 ring-brand-500', isAlt && 'border-amber-300 bg-amber-50/30')}>
      <div className="p-4 sm:p-5">
        <div className="flex items-start gap-3">
          <span className={cn('inline-flex size-11 shrink-0 items-center justify-center rounded-xl', isAlt ? 'bg-amber-100 text-amber-700' : 'bg-navy-50 text-navy-700')}>
            <Icon className="size-5" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-1.5">
              {rank !== undefined && <span className="text-xs font-bold text-muted-foreground">#{rank}</span>}
              <h3 className="truncate text-[15px] font-bold text-navy-900">{o.title}</h3>
            </div>
            <p className="truncate text-xs text-muted-foreground">
              {modeLabel[o.mode]} · {o.operator}
              {o.hostName ? ` · hosted by ${o.hostName}` : ''}
            </p>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {o.tags.map((t) => {
                const m = tagMeta[t]
                return (
                  <Badge key={t} tone={m?.tone ?? 'neutral'}>
                    {t === 'ALTERNATIVE' && <TriangleAlert />}
                    {m?.label ?? t}
                  </Badge>
                )
              })}
            </div>
          </div>
          <div className="shrink-0 text-right">
            <p className="font-display text-xl font-extrabold leading-none text-navy-900 sm:text-2xl">{rupees(o.costPerPerson)}</p>
            <p className="mt-1 text-xs text-muted-foreground">per person</p>
            {o.totalCost !== o.costPerPerson && <p className="text-xs font-semibold text-navy-700">{rupees(o.totalCost)} total</p>}
          </div>
        </div>

        {/* timeline */}
        <div className="mt-4 flex items-center gap-3 rounded-xl bg-navy-50/70 px-3.5 py-3">
          <div className="text-center">
            <p className="font-display text-lg font-bold text-navy-900">{timeOf(o.departure)}</p>
            <p className="text-[11px] text-muted-foreground">{shortDate(o.departure)}</p>
          </div>
          <div className="flex flex-1 flex-col items-center gap-1">
            <span className="text-xs font-semibold text-navy-700">{duration(o.durationMinutes)}</span>
            <div className="relative h-px w-full bg-navy-300">
              <ArrowRight className="absolute -right-1 -top-[7px] size-3.5 text-navy-400" />
            </div>
            <span className="text-[11px] text-muted-foreground">{o.transfers === 0 ? 'Direct' : `${o.transfers} change${o.transfers > 1 ? 's' : ''}`}</span>
          </div>
          <div className="text-center">
            <p className="font-display text-lg font-bold text-navy-900">
              {timeOf(o.arrival)}
              {arrivalDay > 0 && <sup className="ml-0.5 text-[10px] font-bold text-brand-600">+{arrivalDay}</sup>}
            </p>
            <p className="text-[11px] text-muted-foreground">{shortDate(o.arrival)}</p>
          </div>
        </div>

        {isAlt && o.violations.length > 0 && (
          <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-[13px] text-amber-900">
            <p className="font-semibold">Shown as an alternative, it doesn’t meet all your requirements:</p>
            <ul className="mt-1 list-disc space-y-0.5 pl-5">
              {o.violations.map((v) => (
                <li key={v}>{v}</li>
              ))}
            </ul>
          </div>
        )}

        {!compact && o.reasons.length > 0 && (
          <ul className="mt-3 space-y-1">
            {o.reasons.slice(0, open ? undefined : 3).map((r) => (
              <li key={r} className="flex gap-2 text-[13px] text-navy-700">
                <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-brand-400" />
                {r}
              </li>
            ))}
          </ul>
        )}

        {o.festiveNote && <p className="mt-2.5 text-[13px] font-medium text-brand-700">🪔 {o.festiveNote}</p>}

        <div className="mt-3.5 flex flex-wrap items-center gap-2">
          <SourceBadge source={o.dataSource} />
          <Badge tone="outline" title={o.dataSourceNote}>
            {o.availabilityLabel}
          </Badge>
          {o.seatsLeft != null && <Badge tone="outline">{o.seatsLeft} seat{o.seatsLeft === 1 ? '' : 's'} offered</Badge>}
          <span className="ml-auto text-xs font-semibold text-muted-foreground">Match {Math.round(o.overallScore)}</span>
        </div>

        {explanation && <div className="mt-3 rounded-xl border border-violet-200 bg-violet-50/70 p-3.5 text-[13px] leading-relaxed text-navy-800">{explanation}</div>}

        {open && (
          <div className="mt-3 space-y-2 border-t border-border pt-3">
            {o.legs.map((l, i) => (
              <div key={i} className="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-[13px]">
                <Badge tone="neutral">{modeLabel[l.mode]}</Badge>
                <span className="font-semibold text-navy-900">
                  {l.from} → {l.to}
                </span>
                <span className="text-muted-foreground">
                  {timeOf(l.departure)}–{timeOf(l.arrival)} · {l.serviceName}
                </span>
              </div>
            ))}
            {o.stops && <p className="text-xs text-muted-foreground">Stops: {o.stops}</p>}
            <p className="text-xs text-muted-foreground">{o.dataSourceNote}</p>
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2 border-t border-border bg-navy-50/40 px-4 py-2.5 sm:px-5">
        <Button variant="ghost" size="sm" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
          <ChevronDown className={cn('transition-transform', open && 'rotate-180')} /> {open ? 'Less' : 'Details'}
        </Button>
        {onToggleCompare && (
          <Button variant={inCompare ? 'softBrand' : 'ghost'} size="sm" onClick={onToggleCompare} disabled={!inCompare && compareDisabled}>
            <GitCompareArrows /> {inCompare ? 'In comparison' : 'Compare'}
          </Button>
        )}
        {onExplain && explainEnabled && (
          <Button variant="ghost" size="sm" onClick={onExplain} loading={explaining}>
            {!explaining && <Sparkles />} Explain
          </Button>
        )}
        <div className="ml-auto flex gap-2">
          {link && (
            <Button variant="soft" size="sm" asChild>
              <a href={link.href} target="_blank" rel="noopener noreferrer" title={link.hint}>
                <ExternalLink /> {link.label}
              </a>
            </Button>
          )}
          {actions}
          {onSave && (
            <Button variant={saved ? 'soft' : 'outline'} size="sm" onClick={onSave} loading={saving} disabled={saved}>
              {!saving && (saved ? <BookmarkCheck /> : <Bookmark />)} {saved ? 'Saved' : 'Save'}
            </Button>
          )}
        </div>
      </div>
    </Card>
  )
}
