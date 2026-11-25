import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Copy, ExternalLink, HeartHandshake, Lock, MessageCircle, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input, Select } from '@/components/ui/input'
import { PassCta } from '@/components/PassCta'
import { api, ApiError } from '@/lib/api'
import { useFeatures } from '@/lib/features'
import { relativeTime } from '@/lib/format'
import { useIsPlus } from '@/lib/pass'
import { cn } from '@/lib/utils'
import { FLAG, type FamilyLink, type FamilyStatus } from '@/lib/types'

const STATUSES: { v: FamilyStatus; label: string }[] = [
  { v: 'PLANNED', label: 'Planned' },
  { v: 'ON_THE_WAY', label: 'On the way' },
  { v: 'DELAYED', label: 'Running late' },
  { v: 'REACHED', label: 'Reached' },
]
const LANGS = [
  { v: 'hi', label: 'Hindi' },
  { v: 'te', label: 'Telugu' },
  { v: 'en', label: 'English' },
]

const absolute = (path: string) => `${window.location.origin}${path}`

/** A page for family to follow this journey in their language. Plus only; the server enforces it (Flagsmith: family_link). */
export function FamilyLinkCard({ tripId }: { tripId: string }) {
  const { isEnabled } = useFeatures()
  const plus = useIsPlus()
  const on = isEnabled(FLAG.familyLink)

  if (!on) {
    if (plus) return null
    return (
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2">
            <HeartHandshake className="size-4.5 text-brand-500" /> Family link
            <Badge tone="violet" className="ml-auto">
              Festival Pass
            </Badge>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="flex items-start gap-2 text-[13px] leading-relaxed text-navy-700">
            <Lock className="mt-0.5 size-4 shrink-0 text-violet-600" />
            Give your parents a simple page in Hindi or Telugu that shows you are on the way and when you reach. No app or login for them.
          </p>
          <PassCta className="w-full" />
        </CardContent>
      </Card>
    )
  }
  return <FamilyLinkManager tripId={tripId} />
}

function FamilyLinkManager({ tripId }: { tripId: string }) {
  const qc = useQueryClient()
  const key = ['family-links', tripId]
  const q = useQuery({ queryKey: key, queryFn: () => api.familyLinks(tripId) })
  const [language, setLanguage] = useState('hi')
  const [familyName, setFamilyName] = useState('')
  const refresh = () => qc.invalidateQueries({ queryKey: key })
  const fail = (e: unknown) => toast.error(e instanceof ApiError ? e.message : 'Something went wrong.')

  const create = useMutation({
    mutationFn: () => api.createFamilyLink(tripId, { language, familyName: familyName.trim() || undefined }),
    onSuccess: () => {
      setFamilyName('')
      void refresh()
      toast.success('Family link created')
    },
    onError: fail,
  })

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2">
          <HeartHandshake className="size-4.5 text-brand-500" /> Family link
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-[13px] leading-relaxed text-navy-700">
          Share a page your family can open without an app. It shows your first name, route and status only. Never your phone number or location.
        </p>

        {(q.data ?? []).map((l) => (
          <LinkRow key={l.id} link={l} onChanged={() => void refresh()} onError={fail} />
        ))}

        <div className="space-y-2 rounded-xl bg-navy-50 p-3">
          <p className="text-xs font-bold uppercase tracking-wide text-navy-500">New link</p>
          <div className="grid grid-cols-[1fr_6.5rem] gap-2">
            <Input value={familyName} onChange={(e) => setFamilyName(e.target.value)} placeholder="Who is it for? e.g. Amma" maxLength={60} aria-label="Who is the link for" />
            <Select value={language} onChange={(e) => setLanguage(e.target.value)} aria-label="Language">
              {LANGS.map((l) => (
                <option key={l.v} value={l.v}>
                  {l.label}
                </option>
              ))}
            </Select>
          </div>
          <Button variant="brand" size="sm" className="w-full" onClick={() => create.mutate()} loading={create.isPending}>
            Create family link
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

function LinkRow({ link, onChanged, onError }: { link: FamilyLink; onChanged: () => void; onError: (e: unknown) => void }) {
  const [message, setMessage] = useState(link.message ?? '')
  const url = absolute(link.path)
  const setStatus = useMutation({
    mutationFn: (status: FamilyStatus) => api.setFamilyStatus(link.id, status, message.trim() || undefined),
    onSuccess: () => {
      onChanged()
      toast.success('Your family can see the update')
    },
    onError,
  })
  const revoke = useMutation({ mutationFn: () => api.revokeFamilyLink(link.id), onSuccess: onChanged, onError })
  const lang = LANGS.find((l) => l.v === link.language)?.label

  return (
    <div className="space-y-2.5 rounded-xl border border-border p-3">
      <div className="flex items-center gap-2">
        <p className="min-w-0 flex-1 truncate text-sm font-bold text-navy-900">
          {link.familyName || 'Family'} <span className="font-normal text-muted-foreground">· {lang}</span>
        </p>
        <span className="text-[11px] text-muted-foreground">
          {link.views} view{link.views === 1 ? '' : 's'}
          {link.lastViewedAt ? ` · ${relativeTime(link.lastViewedAt)}` : ''}
        </span>
      </div>

      <div className="flex flex-wrap gap-1.5">
        {STATUSES.map((s) => (
          <button
            key={s.v}
            type="button"
            disabled={setStatus.isPending}
            onClick={() => setStatus.mutate(s.v)}
            className={cn(
              'rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors',
              link.status === s.v ? 'border-navy-900 bg-navy-900 text-white' : 'border-border bg-white text-navy-700 hover:border-navy-300',
            )}
          >
            {s.label}
          </button>
        ))}
      </div>
      <Input value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Optional note, e.g. Left home at 6" maxLength={200} aria-label="Note for your family" className="h-9 text-[13px]" />

      <div className="flex flex-wrap gap-1.5">
        <Button
          size="sm"
          variant="soft"
          onClick={() => {
            void navigator.clipboard.writeText(url).then(() => toast.success('Link copied'))
          }}
        >
          <Copy /> Copy
        </Button>
        <Button size="sm" variant="soft" asChild>
          <a href={`https://wa.me/?text=${encodeURIComponent(url)}`} target="_blank" rel="noreferrer">
            <MessageCircle /> WhatsApp
          </a>
        </Button>
        <Button size="sm" variant="soft" asChild>
          <a href={link.path} target="_blank" rel="noreferrer">
            <ExternalLink /> Preview
          </a>
        </Button>
        <Button size="sm" variant="ghost" className="ml-auto text-red-600 hover:bg-red-50" onClick={() => revoke.mutate()} loading={revoke.isPending}>
          <Trash2 /> Stop sharing
        </Button>
      </div>
    </div>
  )
}
