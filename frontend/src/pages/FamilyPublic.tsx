import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CalendarDays, CheckCircle2, Clock, MapPin, Navigation, TrainFront } from 'lucide-react'
import { FullPageSpinner } from '@/components/common'
import { Logo } from '@/components/Logo'
import { ApiError, api } from '@/lib/api'
import { cn } from '@/lib/utils'
import type { FamilyStatus } from '@/lib/types'

const TONE: Record<FamilyStatus, { box: string; icon: typeof Clock }> = {
  PLANNED: { box: 'bg-sky-50 text-sky-900 border-sky-200', icon: CalendarDays },
  ON_THE_WAY: { box: 'bg-emerald-50 text-emerald-900 border-emerald-200', icon: Navigation },
  DELAYED: { box: 'bg-amber-50 text-amber-900 border-amber-200', icon: Clock },
  REACHED: { box: 'bg-emerald-100 text-emerald-900 border-emerald-300', icon: CheckCircle2 },
}

const COPY = {
  en: { missingTitle: 'This link is no longer active', missing: 'The traveller may have stopped sharing, or the journey is over. Please ask them for a new link.', updated: 'Updated', date: 'Date', meet: 'Starting near' },
  hi: { missingTitle: 'यह लिंक अब चालू नहीं है', missing: 'हो सकता है यात्री ने साझा करना बंद कर दिया हो, या यात्रा पूरी हो चुकी हो। कृपया उनसे नया लिंक माँगें।', updated: 'अपडेट', date: 'तारीख़', meet: 'शुरुआत' },
  te: { missingTitle: 'ఈ లింక్ ఇప్పుడు పనిచేయడం లేదు', missing: 'ప్రయాణికుడు షేరింగ్ ఆపివేసి ఉండవచ్చు లేదా ప్రయాణం ముగిసి ఉండవచ్చు. దయచేసి కొత్త లింక్ అడగండి.', updated: 'నవీకరణ', date: 'తేదీ', meet: 'ప్రారంభం' },
} as const

/**
 * The page a family member opens from a shared link. No login, no app, read-only. It auto-refreshes so a parent can leave
 * it open. Only the first name, route, date and a status are shown.
 */
export default function FamilyPublic() {
  const { token = '' } = useParams()
  const q = useQuery({
    queryKey: ['family-view', token],
    queryFn: () => api.familyView(token),
    refetchInterval: 20_000,
    retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 2,
  })

  if (q.isLoading) return <FullPageSpinner />

  if (q.isError || !q.data) {
    return (
      <Shell>
        <div className="rounded-2xl border border-border bg-white p-6 text-center">
          <h1 className="text-xl font-extrabold text-navy-900">{COPY.en.missingTitle}</h1>
          <p className="mt-2 text-sm text-navy-700">{COPY.en.missing}</p>
        </div>
      </Shell>
    )
  }

  const v = q.data
  const t = COPY[v.language] ?? COPY.en
  const tone = TONE[v.status] ?? TONE.PLANNED
  const Icon = tone.icon

  return (
    <Shell>
      <div className="rounded-3xl border border-border bg-white p-5 shadow-sm sm:p-6" lang={v.language}>
        {v.familyName && <p className="mb-1 text-sm font-semibold text-navy-500">{v.familyName}</p>}
        <h1 className="text-2xl font-extrabold leading-snug text-navy-900">{v.headline}</h1>

        <div className={cn('mt-4 flex items-center gap-3 rounded-2xl border p-4', tone.box)} role="status">
          <Icon className="size-7 shrink-0" />
          <p className="text-xl font-extrabold">{v.statusLabel}</p>
        </div>

        {v.message && <p className="mt-4 rounded-2xl bg-navy-50 p-4 text-lg font-medium text-navy-900">“{v.message}”</p>}

        <dl className="mt-5 space-y-3 text-[15px]">
          <div className="flex items-center gap-3">
            <TrainFront className="size-5 shrink-0 text-navy-400" />
            <dd className="font-bold text-navy-900">
              {v.sourceCity} → {v.destinationCity}
            </dd>
          </div>
          <div className="flex items-center gap-3">
            <CalendarDays className="size-5 shrink-0 text-navy-400" />
            <dt className="sr-only">{t.date}</dt>
            <dd className="text-navy-800">
              {new Date(`${v.travelDate}T00:00:00`).toLocaleDateString(`${v.language}-IN`, { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' })}
              {v.departureTime ? ` · ${v.departureTime.slice(0, 5)}` : ''}
            </dd>
          </div>
          {v.meetingArea && (
            <div className="flex items-center gap-3">
              <MapPin className="size-5 shrink-0 text-navy-400" />
              <dt className="sr-only">{t.meet}</dt>
              <dd className="text-navy-800">{v.meetingArea}</dd>
            </div>
          )}
        </dl>

        <p className="mt-5 text-xs text-muted-foreground">
          {t.updated}: {new Date(v.updatedAt).toLocaleString(v.language === 'en' ? 'en-IN' : `${v.language}-IN`, { dateStyle: 'medium', timeStyle: 'short' })}
        </p>
      </div>
      <p className="mt-4 px-2 text-center text-xs leading-relaxed text-muted-foreground">{v.footer}</p>
    </Shell>
  )
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-dvh bg-muted/50 px-4 py-6">
      <div className="mx-auto max-w-md">
        <div className="mb-5 flex justify-center">
          <Logo to="/" />
        </div>
        {children}
      </div>
    </div>
  )
}
