import type { ReactNode } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { ArrowLeft, Check, X } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { PageHeader, SourceBadge } from '@/components/common'
import { duration, modeLabel, rupees, shortDate, timeOf } from '@/lib/format'
import { usePlan } from '@/lib/plan'
import { cn } from '@/lib/utils'
import type { TravelOption } from '@/lib/types'

interface Row {
  label: string
  render: (o: TravelOption) => ReactNode
  /** Returns a sortable number where lower is better; the best cell(s) are highlighted. */
  best?: (o: TravelOption) => number
  higherIsBetter?: boolean
}

const rows: Row[] = [
  { label: 'Price per person', render: (o) => <span className="font-display text-lg font-extrabold">{rupees(o.costPerPerson)}</span>, best: (o) => o.costPerPerson },
  { label: 'Total cost', render: (o) => rupees(o.totalCost), best: (o) => o.totalCost },
  { label: 'Duration', render: (o) => duration(o.durationMinutes), best: (o) => o.durationMinutes },
  { label: 'Departs', render: (o) => `${timeOf(o.departure)} · ${shortDate(o.departure)}` },
  { label: 'Arrives', render: (o) => `${timeOf(o.arrival)} · ${shortDate(o.arrival)}` },
  { label: 'Transfers', render: (o) => (o.transfers === 0 ? 'Direct' : o.transfers), best: (o) => o.transfers },
  { label: 'Mode', render: (o) => modeLabel[o.mode] },
  { label: 'Operator', render: (o) => o.operator },
  { label: 'Comfort', render: (o) => `${o.comfort}/5`, best: (o) => o.comfort, higherIsBetter: true },
  { label: 'Overall match', render: (o) => `${Math.round(o.overallScore)}/100`, best: (o) => o.overallScore, higherIsBetter: true },
  { label: 'Meets your requirements', render: (o) => (o.meetsConstraints ? <Check className="size-5 text-emerald-600" aria-label="Yes" /> : <X className="size-5 text-amber-600" aria-label="No" />) },
  { label: 'Availability', render: (o) => <Badge tone="outline">{o.availabilityLabel}</Badge> },
  { label: 'Data source', render: (o) => <SourceBadge source={o.dataSource} /> },
]

export default function Compare() {
  const plan = usePlan()
  const { response, request } = plan
  if (!response || !request) return <Navigate to="/app/plan" replace />
  const picked = plan.compare.map((id) => response.options.find((o) => o.id === id)).filter((o): o is TravelOption => Boolean(o))
  if (picked.length < 2) return <Navigate to="/app/results" replace />

  return (
    <>
      <PageHeader
        eyebrow="Comparison"
        title={`${request.sourceCity} → ${request.destinationCity}`}
        subtitle="The best value in each row is highlighted."
        actions={
          <Button variant="outline" asChild>
            <Link to="/app/results">
              <ArrowLeft /> Back to results
            </Link>
          </Button>
        }
      />
      <Card className="overflow-x-auto">
        <table className="w-full min-w-[34rem] border-collapse text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="sticky left-0 w-36 bg-white p-4 text-left text-xs font-bold uppercase tracking-wide text-muted-foreground" />
              {picked.map((o) => (
                <th key={o.id} className="p-4 text-left align-top">
                  <p className="font-bold text-navy-900">{o.title}</p>
                  <div className="mt-1.5 flex flex-wrap gap-1">
                    {o.tags.map((t) => (
                      <Badge key={t} tone={t === 'ALTERNATIVE' ? 'amber' : 'brand'}>
                        {t.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}
                      </Badge>
                    ))}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => {
              const scores = row.best ? picked.map(row.best) : []
              const target = row.best ? (row.higherIsBetter ? Math.max(...scores) : Math.min(...scores)) : null
              const allSame = scores.length > 0 && scores.every((s) => s === scores[0])
              return (
                <tr key={row.label} className="border-b border-border/70 last:border-0">
                  <th scope="row" className="sticky left-0 bg-white p-4 text-left text-[13px] font-semibold text-navy-600">
                    {row.label}
                  </th>
                  {picked.map((o, i) => {
                    const isBest = target !== null && !allSame && scores[i] === target
                    return (
                      <td key={o.id} className={cn('p-4 align-middle text-navy-900', isBest && 'bg-emerald-50 font-bold text-emerald-900')}>
                        {row.render(o)}
                      </td>
                    )
                  })}
                </tr>
              )
            })}
            <tr>
              <th scope="row" className="sticky left-0 bg-white p-4 text-left align-top text-[13px] font-semibold text-navy-600">
                Why it’s ranked here
              </th>
              {picked.map((o) => (
                <td key={o.id} className="p-4 align-top">
                  <ul className="space-y-1.5 text-[13px] text-navy-700">
                    {o.reasons.map((r) => (
                      <li key={r} className="flex gap-2">
                        <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-brand-400" />
                        {r}
                      </li>
                    ))}
                    {o.violations.map((v) => (
                      <li key={v} className="flex gap-2 text-amber-800">
                        <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-amber-500" />
                        {v}
                      </li>
                    ))}
                  </ul>
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </Card>
    </>
  )
}
