import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Bot, Send, Sparkles, UserRound } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input, Select } from '@/components/ui/input'
import { FeatureOff, Notice, PageHeader } from '@/components/common'
import { MatchCard } from '@/components/MatchCard'
import { OptionCard } from '@/components/OptionCard'
import { VoiceButton, browserSpeechSupported } from '@/components/VoiceButton'
import { useAuth } from '@/lib/auth'
import { api, ApiError } from '@/lib/api'
import { useFeatures } from '@/lib/features'
import { addDays, languages, todayIso } from '@/lib/format'
import { cn } from '@/lib/utils'
import { FLAG, type AssistantAction, type AssistantResponse, type TripDraft } from '@/lib/types'

interface Msg {
  id: number
  role: 'user' | 'assistant'
  text: string
  meta?: AssistantResponse
  via?: string
}

const SUGGESTIONS: Record<string, string[]> = {
  en: ['Cheapest way from Bengaluru to Hyderabad on 20 December under 1500', 'Find travellers going Chennai to Madurai', 'Show my groups'],
  hi: ['मुझे 25 दिसंबर को दिल्ली से पटना जाना है, बजट 2000', 'मेरे साथ कौन चल सकता है?', 'मेरे ग्रुप दिखाओ'],
  te: ['Naaku December 20th Bangalore nunchi Hyderabad vellali. Budget 1500.', 'నాతో ప్రయాణించే వాళ్ళు ఉన్నారా?', 'నా గ్రూపులు చూపించు'],
}

function plannerQuery(d: TripDraft) {
  const p = new URLSearchParams()
  if (d.sourceCity) p.set('from', d.sourceCity)
  if (d.destinationCity) p.set('to', d.destinationCity)
  if (d.travelDate) p.set('date', d.travelDate)
  if (d.travellers) p.set('travellers', String(d.travellers))
  if (d.maxBudget) p.set('budget', String(d.maxBudget))
  if (d.preferredMode) p.set('mode', d.preferredMode)
  if (d.priority) p.set('priority', d.priority)
  if (d.arriveBy) p.set('arriveBy', d.arriveBy)
  return p.toString()
}

export default function Assistant() {
  const { user } = useAuth()
  const { isEnabled } = useFeatures()
  const navigate = useNavigate()
  const [lang, setLang] = useState(user?.preferredLanguage ?? 'en')
  const [input, setInput] = useState('')
  const [msgs, setMsgs] = useState<Msg[]>([])
  const [context, setContext] = useState<TripDraft | null>(null)
  const idRef = useRef(0)
  const endRef = useRef<HTMLDivElement>(null)

  const on = isEnabled(FLAG.assistant)
  const voiceOn = isEnabled(FLAG.voice)
  const speech = languages.find((l) => l.code === lang)?.speech ?? 'en-IN'

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [msgs.length])

  const push = (m: Omit<Msg, 'id'>) => setMsgs((s) => [...s, { ...m, id: ++idRef.current }])

  const chat = useMutation({
    mutationFn: (text: string) => api.chat(text, context, lang),
    onSuccess: (r) => {
      setContext(r.draft)
      push({ role: 'assistant', text: r.reply, meta: r })
    },
    onError: (e) => {
      push({ role: 'assistant', text: e instanceof ApiError ? e.message : 'I couldn’t reach the server. Please try again.' })
    },
  })

  const voice = useMutation({
    mutationFn: (blob: Blob) => api.voice(blob, lang, context),
    onSuccess: (r) => {
      push({ role: 'user', text: r.transcript, via: 'Sarvam speech-to-text' })
      setContext(r.assistant.draft)
      push({ role: 'assistant', text: r.assistant.reply, meta: r.assistant })
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Voice input failed. Please type instead.'),
  })

  const send = (text: string, via?: string) => {
    const t = text.trim()
    if (!t || chat.isPending) return
    push({ role: 'user', text: t, via })
    setInput('')
    chat.mutate(t)
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    send(input)
  }

  const act = (a: AssistantAction, draft: TripDraft) => {
    switch (a.type) {
      case 'OPEN_RECOMMENDATIONS':
      case 'CREATE_TRIP':
        navigate(`/app/plan?${plannerQuery(draft)}`)
        break
      case 'OPEN_TRAVELLERS':
        navigate('/app/travellers')
        break
      case 'OPEN_GROUPS':
        navigate('/app/groups')
        break
      default:
        toast.info(a.label)
    }
  }

  if (!on) {
    return (
      <>
        <PageHeader title="Assistant" />
        <FeatureOff name="The multilingual assistant" />
        <p className="mt-4 text-center text-sm text-muted-foreground">You can still describe a trip in plain words on the planner page.</p>
      </>
    )
  }

  const suggestions = SUGGESTIONS[lang] ?? SUGGESTIONS.en!

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader eyebrow="Assistant" title="Ask in your language" subtitle="Say it in Telugu, Hindi or English. Answers come from the real planner, matcher and your groups." />

      <Card className="flex min-h-[28rem] flex-col overflow-hidden">
        <div className="flex items-center gap-3 border-b border-border bg-navy-50/50 px-4 py-3">
          <span className="inline-flex size-9 items-center justify-center rounded-xl bg-navy-900 text-white">
            <Bot className="size-5" />
          </span>
          <div className="flex-1">
            <p className="text-sm font-bold text-navy-900">GharPahucho assistant</p>
            <p className="text-xs text-muted-foreground">Grounded in live planner results</p>
          </div>
          <Select value={lang} onChange={(e) => setLang(e.target.value)} className="h-9 w-32 text-[13px]" aria-label="Language">
            {languages.map((l) => (
              <option key={l.code} value={l.code}>
                {l.label}
              </option>
            ))}
          </Select>
        </div>

        <div className="flex-1 space-y-5 overflow-y-auto p-4 sm:p-5" role="log" aria-live="polite">
          {msgs.length === 0 && (
            <div className="py-6 text-center">
              <span className="mx-auto mb-3 inline-flex size-12 items-center justify-center rounded-2xl bg-brand-50 text-brand-600">
                <Sparkles className="size-6" />
              </span>
              <p className="font-bold text-navy-900">How can I help you get home?</p>
              <p className="mt-1 text-sm text-muted-foreground">Try one of these:</p>
              <div className="mt-4 flex flex-col items-center gap-2">
                {suggestions.map((s) => (
                  <button key={s} onClick={() => send(s)} className="max-w-full rounded-full border border-border bg-white px-4 py-2 text-left text-[13px] font-medium text-navy-700 transition-colors hover:border-brand-400 hover:bg-brand-50">
                    {s}
                  </button>
                ))}
              </div>
            </div>
          )}

          {msgs.map((m) => (
            <div key={m.id} className={cn('flex gap-3', m.role === 'user' && 'flex-row-reverse')}>
              <span className={cn('mt-0.5 inline-flex size-8 shrink-0 items-center justify-center rounded-full', m.role === 'user' ? 'bg-brand-100 text-brand-700' : 'bg-navy-900 text-white')}>
                {m.role === 'user' ? <UserRound className="size-4" /> : <Bot className="size-4" />}
              </span>
              <div className={cn('min-w-0 max-w-[88%] space-y-3', m.role === 'user' && 'text-right')}>
                <div className={cn('inline-block rounded-2xl px-4 py-2.5 text-left text-sm leading-relaxed', m.role === 'user' ? 'rounded-tr-md bg-navy-900 text-white' : 'rounded-tl-md bg-navy-50 text-navy-900')}>
                  <p className="whitespace-pre-wrap">{m.text}</p>
                </div>
                {m.via && <p className="text-[11px] text-muted-foreground">via {m.via}</p>}

                {m.meta && (
                  <div className="space-y-3 text-left">
                    <div className="flex flex-wrap gap-1.5">
                      <Badge tone={m.meta.replySource === 'SARVAM_AI' ? 'violet' : 'neutral'}>
                        {m.meta.replySource === 'SARVAM_AI' ? 'Reworded by Sarvam AI' : 'Template reply'}
                      </Badge>
                      <Badge tone={m.meta.extractionSource === 'SARVAM_AI' ? 'violet' : 'neutral'}>
                        {m.meta.extractionSource === 'SARVAM_AI' ? 'Understood by Sarvam AI' : 'Understood by rules'}
                      </Badge>
                    </div>
                    {m.meta.notices.map((n) => (
                      <Notice key={n} tone="sample">
                        {n}
                      </Notice>
                    ))}
                    {m.meta.options.slice(0, 3).map((o) => (
                      <OptionCard key={o.id} option={o} travelDate={m.meta!.draft.travelDate ?? addDays(todayIso(), 21)} compact />
                    ))}
                    {m.meta.matches.slice(0, 3).map((c) => (
                      <MatchCard key={c.tripId} match={c} />
                    ))}
                    {m.meta.actions.length > 0 && (
                      <div className="flex flex-wrap gap-2">
                        {m.meta.actions.map((a) => (
                          <Button key={a.type + a.label} size="sm" variant="softBrand" onClick={() => act(a, m.meta!.draft)}>
                            {a.label}
                          </Button>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          ))}
          {(chat.isPending || voice.isPending) && (
            <div className="flex items-center gap-3">
              <span className="inline-flex size-8 items-center justify-center rounded-full bg-navy-900 text-white">
                <Bot className="size-4" />
              </span>
              <span className="flex gap-1 rounded-2xl bg-navy-50 px-4 py-3" aria-label="Thinking">
                {[0, 150, 300].map((d) => (
                  <span key={d} className="size-2 animate-bounce rounded-full bg-navy-400" style={{ animationDelay: `${d}ms` }} />
                ))}
              </span>
            </div>
          )}
          <div ref={endRef} />
        </div>

        <form onSubmit={onSubmit} className="flex items-center gap-2 border-t border-border bg-white p-3">
          {voiceOn ? (
            <VoiceButton mode="server" languageTag={speech} busy={voice.isPending} onAudio={(b) => voice.mutate(b)} />
          ) : browserSpeechSupported() ? (
            <VoiceButton mode="browser" languageTag={speech} onText={(t) => send(t, 'browser speech recognition')} />
          ) : null}
          <Input value={input} onChange={(e) => setInput(e.target.value)} placeholder="Type your trip in any language…" maxLength={600} aria-label="Message" />
          <Button type="submit" variant="brand" size="icon" disabled={!input.trim()} loading={chat.isPending} aria-label="Send">
            {!chat.isPending && <Send />}
          </Button>
        </form>
      </Card>

      <p className="mt-3 text-center text-xs text-muted-foreground">
        {voiceOn ? 'Voice is transcribed by Sarvam AI.' : 'Voice AI is switched off; the mic (if shown) uses your browser’s own speech recognition.'} Transport data may be sample data. Check the labels.
      </p>
    </div>
  )
}
