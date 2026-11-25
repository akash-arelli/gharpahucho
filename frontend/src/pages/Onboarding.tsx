import { useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Field } from '@/components/ui/label'
import { Input, Select } from '@/components/ui/input'
import { Choice, ToggleRow, modeOptions, priorityOptions } from '@/components/choice'
import { CityInput } from '@/components/CityInput'
import { Logo } from '@/components/Logo'
import { useAuth } from '@/lib/auth'
import { api, ApiError } from '@/lib/api'
import { languages } from '@/lib/format'
import type { Preferences } from '@/lib/types'

export default function Onboarding() {
  const { user, setUser } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [busy, setBusy] = useState(false)
  const [homeCity, setHomeCity] = useState(user?.homeCity ?? '')
  const [locality, setLocality] = useState(user?.homeLocality ?? '')
  const [lang, setLang] = useState(user?.preferredLanguage ?? 'en')
  const [prefs, setPrefs] = useState<Preferences>(
    user?.preferences ?? { preferredMode: 'ANY', priority: 'CHEAPEST', willingToShare: true, firstNameOnly: true, discoverable: false, defaultMaxBudget: null },
  )

  if (!user) return <Navigate to="/login" replace />

  const save = async () => {
    setBusy(true)
    try {
      const me = await api.updateMe({
        homeCity: homeCity.trim() || undefined,
        homeLocality: locality.trim() || undefined,
        preferredLanguage: lang,
        onboarded: true,
        preferences: { ...prefs, defaultMaxBudget: prefs.defaultMaxBudget || null },
      })
      setUser(me)
      navigate(params.get('next') ?? '/app', { replace: true })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not save your preferences.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="bg-hero min-h-dvh pb-16">
      <header className="mx-auto flex h-18 max-w-3xl items-center px-4">
        <Logo />
      </header>
      <div className="mx-auto max-w-3xl px-4">
        <p className="text-xs font-bold uppercase tracking-wider text-brand-600">Welcome, {user.displayName.split(' ')[0]}</p>
        <h1 className="mt-1 text-3xl font-extrabold text-navy-900">Let’s tailor GharPahucho to you</h1>
        <p className="mt-1.5 text-muted-foreground">Takes under a minute. You can change all of this later in your profile.</p>

        <Card className="mt-7 space-y-7 p-5 sm:p-7">
          <section className="grid gap-4 sm:grid-cols-2">
            <Field label="Home city" hint="Where you travel home to, or from.">
              <CityInput value={homeCity} onChange={setHomeCity} placeholder="e.g. Hyderabad" />
            </Field>
            <Field label="Your neighbourhood" hint="Only the area is ever shown to others, never an exact address.">
              <Input value={locality} onChange={(e) => setLocality(e.target.value)} placeholder="e.g. Kukatpally" maxLength={80} />
            </Field>
            <Field label="Preferred language" className="sm:col-span-2">
              <Select value={lang} onChange={(e) => setLang(e.target.value)}>
                {languages.map((l) => (
                  <option key={l.code} value={l.code}>
                    {l.label}
                  </option>
                ))}
              </Select>
            </Field>
          </section>

          <section>
            <p className="mb-2 text-[13px] font-semibold text-navy-800">How do you usually travel?</p>
            <Choice value={prefs.preferredMode} options={modeOptions} onChange={(v) => setPrefs((p) => ({ ...p, preferredMode: v }))} />
          </section>

          <section>
            <p className="mb-2 text-[13px] font-semibold text-navy-800">What matters most?</p>
            <Choice value={prefs.priority} options={priorityOptions} onChange={(v) => setPrefs((p) => ({ ...p, priority: v }))} />
          </section>

          <section className="grid gap-4 sm:grid-cols-2">
            <Field label="Usual budget per person (₹)" hint="Optional. Used as a default in the planner.">
              <Input
                type="number"
                inputMode="numeric"
                min={0}
                step={100}
                value={prefs.defaultMaxBudget ?? ''}
                onChange={(e) => setPrefs((p) => ({ ...p, defaultMaxBudget: e.target.value ? Number(e.target.value) : null }))}
                placeholder="1500"
              />
            </Field>
          </section>

          <section className="space-y-4 rounded-xl bg-navy-50/70 p-4">
            <ToggleRow
              title="Open to sharing a ride"
              body="Include shared rides and cost-splitting in your options."
              checked={prefs.willingToShare}
              onChange={(v) => setPrefs((p) => ({ ...p, willingToShare: v }))}
            />
            <ToggleRow
              title="Let other travellers find me"
              body="Off by default. When on, others going your way see your first name, neighbourhood and trip. Never contact details."
              checked={prefs.discoverable}
              onChange={(v) => setPrefs((p) => ({ ...p, discoverable: v }))}
            />
          </section>

          <div className="flex justify-end">
            <Button variant="brand" size="lg" onClick={save} loading={busy}>
              Start planning <ArrowRight />
            </Button>
          </div>
        </Card>
      </div>
    </div>
  )
}
