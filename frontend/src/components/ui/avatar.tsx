import { cn } from '@/lib/utils'
import { initials } from '@/lib/format'

const palette = [
  'bg-navy-100 text-navy-800',
  'bg-brand-100 text-brand-700',
  'bg-emerald-100 text-emerald-800',
  'bg-sky-100 text-sky-800',
  'bg-violet-100 text-violet-800',
  'bg-rose-100 text-rose-800',
]

/** Deterministic initials avatar: no photos are ever stored or shown. */
function Avatar({ name, className }: { name: string; className?: string }) {
  const tone = palette[[...name].reduce((a, c) => a + c.charCodeAt(0), 0) % palette.length]
  return (
    <span className={cn('inline-flex size-10 shrink-0 items-center justify-center rounded-full text-sm font-bold', tone, className)} aria-hidden>
      {initials(name)}
    </span>
  )
}

export { Avatar }
