import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { ArrowLeftRight, ArrowRight, Bot, Sparkles, Wand2 } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field } from '@/components/ui/label'
import { Input, Select, Textarea } from '@/components/ui/input'
import { Choice, ToggleRow, modeOptions, priorityOptions } from '@/components/choice'
import { CityInput } from '@/components/CityInput'
import { FeatureOff, Notice, PageHeader } from '@/components/common'
import { VoiceButton, browserSpeechSupported } from '@/components/VoiceButton'
import { useAuth } from '@/lib/auth'
import { api, ApiError } from '@/lib/api'
import { useFeatures } from '@/lib/features'
import { addDays, languages, todayIso } from '@/lib/format'
import { usePlan } from '@/lib/plan'
import { FLAG, type ParseResponse, type PlanRequest, type Priority, type TransportMode, type TripDraft } from '@/lib/types'

interface FormState {
  from: string
  to: string
  date: string
  returnDate: string
  travellers: number
  budget: string
  mode: TransportMode
  priority: Priority
  arriveBy: string
  saveJourney: boolean
  discoverable: boolean
  offerSeats: boolean
  seats: number
  costPerSeat: string
}

const PROMPT_EXAMPLES = [
  'Naaku December 20th Bangalore nunchi Hyderabad vellali. Budget 1500.',
  'मुझे 25 दिसंबर को दिल्ली से पटना जाना है, बजट 2000, दो लोग',
  'Chennai to Madurai next Friday, cheapest, 2 people',
]

export default function Planner() {
  const { user } = useAuth()
  const { isEnabled } = useFeatures()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const plan = usePlan()
  const today = todayIso()

  const [form, setForm] = useState<FormState>(() => ({
    from: params.get('from') ?? user?.homeCity ?? '',
    to: params.get('to') ?? '',
    date: params.get('date') ?? addDays(today, 21),
    returnDate: params.get('returnDate') ?? '',
    travellers: Number(params.get('travellers')) || 1,
    budget: params.get('budget') ?? (user?.preferences?.defaultMaxBudget ? String(user.preferences.defaultMaxBudget) : ''),
    mode: (params.get('mode') as TransportMode | null) ?? user?.preferences?.preferredMode ?? 'ANY',
    priority: (params.get('priority') as Priority | null) ?? user?.preferences?.priority ?? 'CHEAPEST',
    arriveBy: params.get('arriveBy') ?? '',
    saveJourney: true,
    discoverable: false,
    offerSeats: false,
    seats: 2,
    costPerSeat: '',
  }))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [lang, setLang] = useState(user?.preferredLanguage ?? 'en')
  const [text, setText] = useState('')
  const [parsed, setParsed] = useState<ParseResponse | null>(null)

  const plannerOn = isEnabled(FLAG.planner)
  const companionsOn = isEnabled(FLAG.companions)
  const sharedOn = isEnabled(FLAG.sharedRides)
  const voiceOn = isEnabled(FLAG.voice)
  const assistantOn = isEnabled(FLAG.assistant)
  const speech = languages.find((l) => l.code === lang)?.speech ?? 'en-IN'
  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setForm((f) => ({ ...f, [k]: v }))

  const applyDraft = (d: TripDraft) =>
    setForm((f) => ({
      ...f,
      from: d.sourceCity ?? f.from,
      to: d.destinationCity ?? f.to,
      date: d.travelDate ?? f.date,
      returnDate: d.returnDate ?? f.returnDate,
      travellers: d.travellers ?? f.travellers,
      budget: d.maxBudget != null ? String(d.maxBudget) : f.budget,
      mode: d.preferredMode ?? f.mode,
      priority: d.priority ?? f.priority,
      arriveBy: d.arriveBy ?? f.arriveBy,
    }))

  const parse = useMutation({
    mutationFn: (t: string) => api.parse(t, lang),
    onSuccess: (r) => {
      setParsed(r)
      applyDraft(r.draft)
      setErrors({})
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not read that.'),
  })

  const voice = useMutation({
    mutationFn: (blob: Blob) => api.voice(blob, lang),
    onSuccess: (r) => {
      setText(r.transcript)
      setParsed({
        draft: r.assistant.draft,
        extractionSource: r.assistant.extractionSource,
        detectedLanguage: r.transcriptLanguage,
        missing: [],
        notices: [`Heard via ${r.transcriptionSource.replace('_', ' ').toLowerCase()}.`, ...r.assistant.notices],
      })
      applyDraft(r.assistant.draft)
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Voice input failed. Please type instead.'),
  })

  const validate = () => {
    const e: Record<string, string> = {}
    if (!form.from.trim()) e.from = 'Where are you starting from?'
    if (!form.to.trim()) e.to = 'Where are you going?'
    if (form.from.trim() && form.to.trim() && form.from.trim().toLowerCase() === form.to.trim().toLowerCase()) e.to = 'Pick a different destination.'
    if (!form.date) e.date = 'Choose a travel date.'
    else if (form.date < today) e.date = 'That date has already passed.'
    if (form.returnDate && form.returnDate < form.date) e.returnDate = 'Return must be after departure.'
    if (form.budget && Number(form.budget) <= 0) e.budget = 'Enter a positive amount.'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const request = useMemo<PlanRequest>(
    () => ({
      sourceCity: form.from.trim(),
      destinationCity: form.to.trim(),
      travelDate: form.date,
      returnDate: form.returnDate || undefined,
      travellers: form.travellers,
      maxBudget: form.budget ? Number(form.budget) : undefined,
      preferredMode: form.mode,
      priority: form.priority,
      arriveBy: form.arriveBy || undefined,
    }),
    [form],
  )

  const submit = useMutation({
    mutationFn: async () => {
      const response = await api.plan(request)
      let tripId: string | null = null
      if (form.saveJourney) {
        try {
          const trip = await api.createTrip({
            ...request,
            willingToShare: user?.preferences?.willingToShare ?? true,
            visibility: companionsOn && form.discoverable ? 'DISCOVERABLE' : 'PRIVATE',
            seatsAvailable: sharedOn && form.offerSeats ? form.seats : 0,
            costPerSeat: sharedOn && form.offerSeats && form.costPerSeat ? Number(form.costPerSeat) : undefined,
            flexDays: 1,
          })
          tripId = trip.id
        } catch (e) {
          toast.warning(e instanceof ApiError ? `Plan ready, but the journey wasn’t saved: ${e.message}` : 'Plan ready, but the journey wasn’t saved.')
        }
      }
      return { response, tripId }
    },
    onSuccess: ({ response, tripId }) => {
      plan.setPlan(request, response, tripId)
      navigate('/app/results')
    },
    onError: (e) => {
      if (e instanceof ApiError && e.details && typeof e.details.fields === 'object') setErrors(e.details.fields as Record<string, string>)
      toast.error(e instanceof ApiError ? e.message : 'Could not plan this journey.')
    },
  })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (validate()) submit.mutate()
  }

  if (!plannerOn) {
    return (
      <>
        <PageHeader title="Plan your journey" />
        <FeatureOff name="The smart trip planner" />
      </>
    )
  }

  return (
    <>
      <PageHeader eyebrow="Smart trip planner" title="Plan your journey" subtitle="Tell us where and when. We’ll compare real routes, shared rides and sample transport, and show you the reasoning." />

      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <form onSubmit={onSubmit} className="space-y-6" noValidate>
          <Card>
            <CardHeader>
              <CardTitle>Journey</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <Field label="From" htmlFor="from" error={errors.sourceCity ?? errors.from}>
                <CityInput id="from" value={form.from} onChange={(v) => set('from', v)} placeholder="Bengaluru" invalid={Boolean(errors.from)} />
              </Field>
              <div className="relative">
                <Field label="To" htmlFor="to" error={errors.destinationCity ?? errors.to}>
                  <CityInput id="to" value={form.to} onChange={(v) => set('to', v)} placeholder="Hyderabad" invalid={Boolean(errors.to)} />
                </Field>
                <button
                  type="button"
                  onClick={() => setForm((f) => ({ ...f, from: f.to, to: f.from }))}
                  className="absolute -top-1 right-0 hidden items-center gap-1 text-xs font-semibold text-navy-500 hover:text-brand-600 sm:inline-flex"
                  aria-label="Swap origin and destination"
                >
                  <ArrowLeftRight className="size-3.5" /> Swap
                </button>
              </div>
              <Field label="Travel date" htmlFor="date" error={errors.travelDate ?? errors.date}>
                <Input id="date" type="date" min={today} value={form.date} onChange={(e) => set('date', e.target.value)} aria-invalid={Boolean(errors.date)} />
              </Field>
              <Field label="Return date (optional)" htmlFor="return" error={errors.returnDate}>
                <Input id="return" type="date" min={form.date || today} value={form.returnDate} onChange={(e) => set('returnDate', e.target.value)} />
              </Field>
              <Field label="Travellers" htmlFor="travellers">
                <Select id="travellers" value={form.travellers} onChange={(e) => set('travellers', Number(e.target.value))}>
                  {[1, 2, 3, 4, 5, 6].map((n) => (
                    <option key={n} value={n}>
                      {n} {n === 1 ? 'person' : 'people'}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Budget per person (₹)" htmlFor="budget" error={errors.maxBudget ?? errors.budget} hint="Optional. Options above this are labelled as alternatives.">
                <Input id="budget" type="number" inputMode="numeric" min={0} step={100} value={form.budget} onChange={(e) => set('budget', e.target.value)} placeholder="1500" />
              </Field>
              <Field label="Need to arrive by (optional)" htmlFor="arrive" className="sm:col-span-2">
                <Input id="arrive" type="time" value={form.arriveBy} onChange={(e) => set('arriveBy', e.target.value)} className="sm:max-w-48" />
              </Field>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Preferences</CardTitle>
            </CardHeader>
            <CardContent className="space-y-5">
              <div>
                <p className="mb-2 text-[13px] font-semibold text-navy-800">Transport</p>
                <Choice value={form.mode} options={modeOptions} onChange={(v) => set('mode', v)} />
              </div>
              <div>
                <p className="mb-2 text-[13px] font-semibold text-navy-800">What matters most</p>
                <Choice value={form.priority} options={priorityOptions} onChange={(v) => set('priority', v)} />
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Save &amp; share</CardTitle>
              <CardDescription>Optional. Saving the journey lets you find companions for it later.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <ToggleRow title="Save this as one of my journeys" body="Kept private unless you choose otherwise below." checked={form.saveJourney} onChange={(v) => set('saveJourney', v)} />
              {form.saveJourney && companionsOn && (
                <ToggleRow
                  title="Let travellers going my way find this journey"
                  body="Shows your first name, neighbourhood and trip details to other travellers. Never contact information."
                  checked={form.discoverable}
                  onChange={(v) => set('discoverable', v)}
                />
              )}
              {form.saveJourney && sharedOn && (
                <>
                  <ToggleRow title="I’m driving and have spare seats" body="Offer seats so others can join you and share the cost." checked={form.offerSeats} onChange={(v) => set('offerSeats', v)} />
                  {form.offerSeats && (
                    <div className="grid gap-4 sm:grid-cols-2">
                      <Field label="Seats on offer">
                        <Select value={form.seats} onChange={(e) => set('seats', Number(e.target.value))}>
                          {[1, 2, 3, 4, 5, 6].map((n) => (
                            <option key={n} value={n}>
                              {n}
                            </option>
                          ))}
                        </Select>
                      </Field>
                      <Field label="Price per seat (₹)" hint="Leave empty to let GharPahucho estimate a fair share.">
                        <Input type="number" min={0} step={50} value={form.costPerSeat} onChange={(e) => set('costPerSeat', e.target.value)} />
                      </Field>
                    </div>
                  )}
                </>
              )}
            </CardContent>
          </Card>

          <div className="sticky bottom-20 z-20 md:static">
            <Button type="submit" variant="brand" size="lg" className="w-full shadow-pop md:w-auto md:shadow-none" loading={submit.isPending}>
              Find my options <ArrowRight />
            </Button>
          </div>
        </form>

        {/* describe your trip */}
        <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start">
          <Card className="overflow-hidden border-brand-200">
            <CardHeader className="bg-brand-50/60">
              <CardTitle className="flex items-center gap-2">
                <Wand2 className="size-4.5 text-brand-600" /> Describe your trip
              </CardTitle>
              <CardDescription>Type or speak it in your own words. We’ll fill in the form.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3 pt-4">
              <Select value={lang} onChange={(e) => setLang(e.target.value)} aria-label="Language" className="h-10">
                {languages.map((l) => (
                  <option key={l.code} value={l.code}>
                    {l.label}
                  </option>
                ))}
              </Select>
              <Textarea value={text} onChange={(e) => setText(e.target.value)} placeholder={PROMPT_EXAMPLES[0]} className="min-h-28" maxLength={600} />
              <div className="flex gap-2">
                <Button type="button" variant="brand" className="flex-1" onClick={() => text.trim() && parse.mutate(text.trim())} loading={parse.isPending} disabled={!text.trim()}>
                  {!parse.isPending && <Sparkles />} Fill the form
                </Button>
                {voiceOn ? (
                  <VoiceButton mode="server" languageTag={speech} busy={voice.isPending} onAudio={(b) => voice.mutate(b)} />
                ) : browserSpeechSupported() ? (
                  <VoiceButton mode="browser" languageTag={speech} onText={(t) => { setText(t); parse.mutate(t) }} />
                ) : null}
              </div>
              {!voiceOn && browserSpeechSupported() && (
                <p className="text-[11px] text-muted-foreground">Voice here uses your browser’s speech recognition, not Sarvam AI (voice feature is off).</p>
              )}

              <div className="flex flex-wrap gap-1.5 pt-1">
                {PROMPT_EXAMPLES.map((ex) => (
                  <button key={ex} type="button" onClick={() => setText(ex)} className="rounded-full bg-navy-50 px-2.5 py-1 text-[11px] font-medium text-navy-600 hover:bg-navy-100">
                    {ex.length > 32 ? `${ex.slice(0, 32)}…` : ex}
                  </button>
                ))}
              </div>

              {parsed && (
                <div className="space-y-2 rounded-xl bg-navy-50 p-3 text-[13px]" aria-live="polite">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <Badge tone={parsed.extractionSource === 'SARVAM_AI' ? 'violet' : 'neutral'}>
                      <Bot /> {parsed.extractionSource === 'SARVAM_AI' ? 'Understood by Sarvam AI' : 'Understood by built-in rules'}
                    </Badge>
                    {parsed.detectedLanguage && <Badge tone="outline">{parsed.detectedLanguage}</Badge>}
                  </div>
                  {parsed.missing.length > 0 && <p className="font-medium text-amber-800">Still needed: {parsed.missing.join(', ')}.</p>}
                  {parsed.notices.map((n) => (
                    <p key={n} className="text-muted-foreground">
                      {n}
                    </p>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {!assistantOn && (
            <Notice tone="info">
              The conversational assistant is switched off, but this text reader still works with built-in rules.
            </Notice>
          )}
        </aside>
      </div>
    </>
  )
}
