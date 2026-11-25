import { Link } from 'react-router-dom'
import { ArrowRight, Sparkles, Ticket } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { PASS_PATH, useIsPlus, usePlans } from '@/lib/pass'
import { rupees } from '@/lib/format'
import { cn } from '@/lib/utils'

/** The one call to action used inside every locked card, so the upgrade is never hard to find. */
export function PassCta({ label = 'Get the Festival Pass', className }: { label?: string; className?: string }) {
  return (
    <Button asChild size="sm" className={cn('bg-violet-600 text-white shadow-sm shadow-violet-600/30 hover:bg-violet-700', className)}>
      <Link to={PASS_PATH}>
        <Ticket /> {label}
      </Link>
    </Button>
  )
}

/** Always visible in the header: an obvious pill for free users, a quiet "Plus" badge for members. */
export function PassPill() {
  const plus = useIsPlus()
  const plans = usePlans()
  if (plus) {
    return (
      <Link
        to={PASS_PATH}
        className="inline-flex items-center gap-1 rounded-full bg-violet-100 px-2.5 py-1 text-xs font-bold text-violet-800 transition-colors hover:bg-violet-200"
        title="Festival Pass is active"
      >
        <Sparkles className="size-3" /> Plus
      </Link>
    )
  }
  return (
    <Link
      to={PASS_PATH}
      className="inline-flex items-center gap-1.5 rounded-full bg-violet-600 px-3 py-1.5 text-xs font-bold text-white shadow-sm shadow-violet-600/30 transition-colors hover:bg-violet-700 sm:text-[13px]"
    >
      <Ticket className="size-3.5" />
      <span className="sm:hidden">Pass</span>
      <span className="hidden sm:inline">Festival Pass{plans.data ? ` · ${rupees(plans.data.priceInr)}` : ''}</span>
    </Link>
  )
}

/** Dashboard banner for free users. */
export function PassBanner() {
  const plus = useIsPlus()
  if (plus) return null
  return (
    <div className="mt-5 flex flex-col gap-3 rounded-2xl border border-violet-200 bg-gradient-to-r from-violet-50 to-white p-4 sm:flex-row sm:items-center">
      <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-violet-600 text-white">
        <Ticket className="size-5" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-bold text-navy-900">Going home for the festival? Get the Festival Pass.</p>
        <p className="text-[13px] text-navy-700">Know the best day to book, get alerts when rides appear, and share a family link in Hindi or Telugu.</p>
      </div>
      <Button asChild size="sm" className="bg-violet-600 text-white hover:bg-violet-700">
        <Link to={PASS_PATH}>
          See what’s included <ArrowRight />
        </Link>
      </Button>
    </div>
  )
}
