import { Bus, Car, Check, Clock, TrainFront, Users, Wallet, Zap, type LucideIcon } from 'lucide-react'
import type { Priority, TransportMode } from '@/lib/types'
import { Switch } from '@/components/ui/switch'
import { cn } from '@/lib/utils'

export const modeOptions: { v: TransportMode; label: string; icon: LucideIcon }[] = [
  { v: 'ANY', label: 'Any', icon: Zap },
  { v: 'TRAIN', label: 'Train', icon: TrainFront },
  { v: 'BUS', label: 'Bus', icon: Bus },
  { v: 'SHARED_CAR', label: 'Shared ride', icon: Users },
  { v: 'CAB', label: 'Cab', icon: Car },
]
export const priorityOptions: { v: Priority; label: string; icon: LucideIcon; hint: string }[] = [
  { v: 'CHEAPEST', label: 'Cheapest', icon: Wallet, hint: 'Lowest fare first' },
  { v: 'FASTEST', label: 'Fastest', icon: Clock, hint: 'Shortest journey' },
  { v: 'COMFORTABLE', label: 'Comfortable', icon: Check, hint: 'Fewer changes, better seats' },
]

export function Choice<T extends string>({ value, options, onChange }: { value: T; options: { v: T; label: string; icon?: LucideIcon; hint?: string }[]; onChange: (v: T) => void }) {
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3" role="radiogroup">
      {options.map((o) => {
        const active = o.v === value
        return (
          <button
            key={o.v}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(o.v)}
            className={cn(
              'flex items-center gap-2.5 rounded-xl border px-3.5 py-3 text-left transition-all',
              active ? 'border-brand-500 bg-brand-50 ring-2 ring-brand-500/20' : 'border-input bg-white hover:border-navy-300',
            )}
          >
            {o.icon && <o.icon className={cn('size-4.5 shrink-0', active ? 'text-brand-600' : 'text-navy-400')} />}
            <span className="min-w-0">
              <span className="block text-sm font-semibold text-navy-900">{o.label}</span>
              {o.hint && <span className="block truncate text-[11px] text-muted-foreground">{o.hint}</span>}
            </span>
          </button>
        )
      })}
    </div>
  )
}

export function ToggleRow({ title, body, checked, onChange }: { title: string; body: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <div>
        <p className="text-sm font-semibold text-navy-900">{title}</p>
        <p className="mt-0.5 text-[13px] text-muted-foreground">{body}</p>
      </div>
      <Switch checked={checked} onCheckedChange={onChange} aria-label={title} />
    </div>
  )
}
