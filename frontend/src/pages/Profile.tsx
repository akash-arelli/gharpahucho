import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { FlaskConical, Lock } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field } from '@/components/ui/label'
import { Input, Select, Textarea } from '@/components/ui/input'
import { Choice, ToggleRow, modeOptions, priorityOptions } from '@/components/choice'
import { CityInput } from '@/components/CityInput'
import { PageHeader } from '@/components/common'
import { useAuth } from '@/lib/auth'
import { api, ApiError } from '@/lib/api'
import { languages } from '@/lib/format'
import type { Preferences } from '@/lib/types'

const defaults: Preferences = { preferredMode: 'ANY', priority: 'CHEAPEST', willingToShare: true, firstNameOnly: true, discoverable: false, defaultMaxBudget: null }

export default function Profile() {
  const { user, setUser } = useAuth()
  const qc = useQueryClient()
  const [name, setName] = useState(user?.displayName ?? '')
  const [city, setCity] = useState(user?.homeCity ?? '')
  const [area, setArea] = useState(user?.homeLocality ?? '')
  const [lang, setLang] = useState(user?.preferredLanguage ?? 'en')
  const [beta, setBeta] = useState(user?.betaTester ?? false)
  const [prefs, setPrefs] = useState<Preferences>(user?.preferences ?? defaults)

  const save = useMutation({
    mutationFn: () =>
      api.updateMe({
        displayName: name.trim(),
        homeCity: city.trim() || undefined,
        homeLocality: area.trim() || undefined,
        preferredLanguage: lang,
        betaTester: beta,
        preferences: { ...prefs, defaultMaxBudget: prefs.defaultMaxBudget || null },
      }),
    onSuccess: (me) => {
      setUser(me)
      toast.success('Profile saved')
      // Traits such as beta tester feed Flagsmith segments, so flags are re-evaluated.
      void qc.invalidateQueries({ queryKey: ['features'] })
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not save your profile.'),
  })

  if (!user) return null
  const setPref = <K extends keyof Preferences>(k: K, v: Preferences[K]) => setPrefs((p) => ({ ...p, [k]: v }))

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader eyebrow="Account" title="Profile & preferences" subtitle={user.email} />
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>About you</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <Field label="Name">
              <Input value={name} onChange={(e) => setName(e.target.value)} maxLength={80} />
            </Field>
            <Field label="Language">
              <Select value={lang} onChange={(e) => setLang(e.target.value)}>
                {languages.map((l) => (
                  <option key={l.code} value={l.code}>
                    {l.label}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Home city">
              <CityInput value={city} onChange={setCity} />
            </Field>
            <Field label="Neighbourhood" hint="Shown to matches instead of an address.">
              <Input value={area} onChange={(e) => setArea(e.target.value)} maxLength={80} />
            </Field>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Travel preferences</CardTitle>
            <CardDescription>Used as defaults in the planner and in matching.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div>
              <p className="mb-2 text-[13px] font-semibold text-navy-800">Preferred transport</p>
              <Choice value={prefs.preferredMode} options={modeOptions} onChange={(v) => setPref('preferredMode', v)} />
            </div>
            <div>
              <p className="mb-2 text-[13px] font-semibold text-navy-800">Priority</p>
              <Choice value={prefs.priority} options={priorityOptions} onChange={(v) => setPref('priority', v)} />
            </div>
            <Field label="Usual budget per person (₹)" className="sm:max-w-xs">
              <Input type="number" min={0} step={100} value={prefs.defaultMaxBudget ?? ''} onChange={(e) => setPref('defaultMaxBudget', e.target.value ? Number(e.target.value) : null)} />
            </Field>
            <Field label="Notes for travel companions (optional)">
              <Textarea value={prefs.notes ?? ''} onChange={(e) => setPref('notes', e.target.value)} maxLength={300} placeholder="e.g. I prefer early starts and a quiet ride." />
            </Field>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Lock className="size-4.5 text-navy-400" /> Privacy
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <ToggleRow title="Open to sharing a ride" body="Include shared rides and cost-splitting in your options." checked={prefs.willingToShare} onChange={(v) => setPref('willingToShare', v)} />
            <ToggleRow
              title="Let travellers find me"
              body="Journeys you mark visible can appear in other people’s matches, showing your first name and neighbourhood only."
              checked={prefs.discoverable}
              onChange={(v) => setPref('discoverable', v)}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <FlaskConical className="size-4.5 text-violet-500" /> Early access
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ToggleRow
              title="I’m a beta tester"
              body="Sent to Flagsmith as a user trait. Segments can use it to switch on experimental features (like the route-aware matcher) for you only."
              checked={beta}
              onChange={setBeta}
            />
          </CardContent>
        </Card>

        <div className="flex justify-end">
          <Button variant="brand" size="lg" onClick={() => save.mutate()} loading={save.isPending} disabled={!name.trim()}>
            Save changes
          </Button>
        </div>
      </div>
    </div>
  )
}
