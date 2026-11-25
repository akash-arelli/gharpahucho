import { useEffect, useId, useRef, useState } from 'react'
import { MapPin } from 'lucide-react'
import { api } from '@/lib/api'
import type { CityDto } from '@/lib/types'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

interface Props {
  value: string
  onChange: (v: string) => void
  placeholder?: string
  id?: string
  invalid?: boolean
  className?: string
}

/** City autocomplete backed by the server gazetteer. Free text is accepted; the planner validates it. */
export function CityInput({ value, onChange, placeholder, id, invalid, className }: Props) {
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<CityDto[]>([])
  const [active, setActive] = useState(-1)
  const listId = useId()
  const wrapRef = useRef<HTMLDivElement>(null)
  const justPicked = useRef(false)

  useEffect(() => {
    if (justPicked.current) {
      justPicked.current = false
      return
    }
    const q = value.trim()
    if (q.length < 1) {
      setItems([])
      return
    }
    const ctrl = new AbortController()
    const t = setTimeout(() => {
      api
        .cities(q, ctrl.signal)
        .then((r) => {
          setItems(r)
          setActive(-1)
        })
        .catch(() => undefined)
    }, 150)
    return () => {
      clearTimeout(t)
      ctrl.abort()
    }
  }, [value])

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (!wrapRef.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  const pick = (c: CityDto) => {
    justPicked.current = true
    onChange(c.name)
    setOpen(false)
  }

  return (
    <div ref={wrapRef} className={cn('relative', className)}>
      <MapPin className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-navy-400" />
      <Input
        id={id}
        value={value}
        placeholder={placeholder}
        autoComplete="off"
        aria-invalid={invalid}
        role="combobox"
        aria-expanded={open && items.length > 0}
        aria-controls={listId}
        aria-autocomplete="list"
        className="pl-10"
        onChange={(e) => {
          onChange(e.target.value)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={(e) => {
          if (!open || items.length === 0) return
          if (e.key === 'ArrowDown') {
            e.preventDefault()
            setActive((a) => (a + 1) % items.length)
          } else if (e.key === 'ArrowUp') {
            e.preventDefault()
            setActive((a) => (a <= 0 ? items.length - 1 : a - 1))
          } else if (e.key === 'Enter' && active >= 0) {
            e.preventDefault()
            pick(items[active]!)
          } else if (e.key === 'Escape') {
            setOpen(false)
          }
        }}
      />
      {open && items.length > 0 && (
        <ul id={listId} role="listbox" className="absolute z-30 mt-1.5 max-h-64 w-full overflow-auto rounded-xl border border-border bg-white p-1 shadow-pop">
          {items.map((c, i) => (
            <li
              key={c.name}
              role="option"
              aria-selected={i === active}
              onMouseDown={(e) => {
                e.preventDefault()
                pick(c)
              }}
              onMouseEnter={() => setActive(i)}
              className={cn('flex cursor-pointer items-center justify-between rounded-lg px-3 py-2 text-sm', i === active ? 'bg-brand-50' : 'hover:bg-navy-50')}
            >
              <span className="font-semibold text-navy-900">{c.name}</span>
              <span className="text-xs text-muted-foreground">{c.state}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
