import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Activity, AlertTriangle, CheckCircle2, CircleSlash, Flag, FlaskConical, MonitorSmartphone, RefreshCw, Server, Settings2, ShieldAlert, Siren, Zap } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ErrorState, Notice, PageHeader } from '@/components/common'
import { useAuth } from '@/lib/auth'
import { api, ApiError } from '@/lib/api'
import { useFeatures } from '@/lib/features'
import { relativeTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { IntegrationStatus } from '@/lib/types'

const statusTone = { UP: 'green', DEGRADED: 'amber', DOWN: 'red', NOT_CONFIGURED: 'neutral' } as const

export default function Features() {
  return (
    <>
      <PageHeader
        eyebrow="Platform"
        title="Feature flags & operations"
        subtitle="A live view of how Flagsmith controls this app for you, and how the app reports its own health to NudgeBee."
      />
      <Tabs defaultValue="flags">
        <TabsList>
          <TabsTrigger value="flags">
            <Flag className="size-4" /> Feature flags
          </TabsTrigger>
          <TabsTrigger value="ops">
            <Activity className="size-4" /> Operations
          </TabsTrigger>
        </TabsList>
        <TabsContent value="flags">
          <FlagsPanel />
        </TabsContent>
        <TabsContent value="ops">
          <OpsPanel />
        </TabsContent>
      </Tabs>
    </>
  )
}

function FlagsPanel() {
  const { features, loading, error, refresh, sdk } = useFeatures()
  if (loading) return <Skeleton className="h-96" />
  if (error || !features) return <ErrorState error={error} onRetry={refresh} />
  const fs = features.flagsmith
  const traits = Object.entries(features.traits)

  return (
    <div className="space-y-6">
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Server className="size-4.5 text-brand-500" /> Server evaluation
            </CardTitle>
            <CardDescription>The backend evaluates every flag and enforces it. The browser can’t bypass it.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2.5 text-sm">
            <Row label="Flagsmith">
              {fs.configured ? <Badge tone={fs.healthy ? 'green' : 'red'}>{fs.healthy ? 'Connected' : 'Unreachable'}</Badge> : <Badge tone="amber">Not configured</Badge>}
            </Row>
            <Row label="Mode">{fs.mode.replace('_', ' ').toLowerCase()}</Row>
            {fs.configured && <Row label="Refresh">every {fs.refreshIntervalSeconds}s</Row>}
            {fs.lastSuccess && <Row label="Last sync">{relativeTime(fs.lastSuccess)}</Row>}
            {fs.lastError && <Row label="Last error"><span className="text-destructive">{fs.lastError}</span></Row>}
            <Row label="Evaluated">{relativeTime(features.evaluatedAt)}</Row>
            <Row label="Identity">
              <code className="rounded bg-navy-50 px-1.5 py-0.5 text-xs">{features.identity ?? 'anonymous'}</code>
            </Row>
            <div>
              <p className="mb-1.5 text-[13px] text-muted-foreground">Traits sent for segmentation</p>
              <div className="flex flex-wrap gap-1.5">
                {traits.length === 0 ? <span className="text-xs text-muted-foreground">none (anonymous)</span> : traits.map(([k, v]) => <Badge key={k} tone="outline">{k}: {String(v)}</Badge>)}
              </div>
            </div>
            {!fs.configured && (
              <Notice tone="warn" className="mt-2">
                No Flagsmith server key is set, so flags use their <strong>safe defaults</strong>. Every row below says so. Add <code>FLAGSMITH_SERVER_KEY</code> to control them remotely.
              </Notice>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <MonitorSmartphone className="size-4.5 text-brand-500" /> Browser SDK
            </CardTitle>
            <CardDescription>The Flagsmith JavaScript SDK, with realtime updates, if a client key is configured.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2.5 text-sm">
            <Row label="Status">{sdk.enabled ? <Badge tone={sdk.loaded ? 'green' : 'amber'}>{sdk.loaded ? 'Connected' : 'Connecting…'}</Badge> : <Badge tone="neutral">Not configured</Badge>}</Row>
            <Row label="Realtime">{sdk.enabled ? 'on (server-sent events)' : '—'}</Row>
            <Row label="Fallback">UI re-reads the server every 8 s and on window focus.</Row>
            {!sdk.enabled && (
              <p className="pt-1 text-xs leading-relaxed text-muted-foreground">
                Set <code>VITE_FLAGSMITH_ENVIRONMENT_ID</code> (a client-side key; safe for browsers) to enable it. The server key is never sent to the browser.
              </p>
            )}
            <Button variant="outline" size="sm" onClick={refresh} className="mt-2">
              <RefreshCw /> Re-evaluate now
            </Button>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Flags</CardTitle>
          <CardDescription>Change one in your Flagsmith dashboard and watch the product respond, with no redeploy.</CardDescription>
        </CardHeader>
        <CardContent className="divide-y divide-border">
          {features.states.map((s) => {
            const client = sdk.enabled && sdk.loaded ? sdk.flags[s.key] : undefined
            const mismatch = client !== undefined && client !== s.enabled
            return (
              <div key={s.key} className="flex flex-col gap-2 py-4 first:pt-0 last:pb-0 sm:flex-row sm:items-start sm:gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-bold text-navy-900">{s.title}</p>
                    <code className="rounded bg-navy-50 px-1.5 py-0.5 text-[11px] text-navy-600">{s.key}</code>
                  </div>
                  <p className="mt-0.5 text-[13px] text-muted-foreground">{s.description}</p>
                  <p className="mt-1 text-[13px] text-navy-700">
                    <span className="font-semibold">When off:</span> {s.effect}
                  </p>
                </div>
                <div className="flex shrink-0 flex-wrap items-center gap-2 sm:flex-col sm:items-end">
                  <Badge tone={s.enabled ? 'green' : 'neutral'} className="px-3 py-1 text-[13px]">
                    {s.enabled ? <CheckCircle2 /> : <CircleSlash />} {s.enabled ? 'ON' : 'OFF'}
                  </Badge>
                  <Badge tone={s.source === 'FLAGSMITH' ? 'violet' : 'amber'}>{s.source === 'FLAGSMITH' ? 'From Flagsmith' : 'Safe default'}</Badge>
                  {client !== undefined && <Badge tone={mismatch ? 'red' : 'outline'}>Browser SDK: {client ? 'on' : 'off'}</Badge>}
                </div>
              </div>
            )
          })}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Settings2 className="size-4.5 text-brand-500" /> Remote configuration
          </CardTitle>
          <CardDescription>Tunable values, changed in Flagsmith and applied without a deploy.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[30rem] text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs font-bold uppercase tracking-wide text-muted-foreground">
                  <th className="pb-2 pr-4">Setting</th>
                  <th className="pb-2 pr-4">Value</th>
                  <th className="pb-2">Source</th>
                </tr>
              </thead>
              <tbody>
                {features.configStates.map((c) => (
                  <tr key={c.key} className="border-b border-border/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="font-semibold text-navy-900">{c.title}</p>
                      <p className="text-xs text-muted-foreground">{c.description}</p>
                      <code className="text-[11px] text-navy-500">{c.key}</code>
                    </td>
                    <td className="py-3 pr-4 align-top">
                      <code className="rounded bg-navy-50 px-2 py-1 text-xs font-semibold text-navy-900">{c.value}</code>
                    </td>
                    <td className="py-3 align-top">
                      <Badge tone={c.source === 'FLAGSMITH' ? 'violet' : 'amber'}>{c.source === 'FLAGSMITH' ? 'Flagsmith' : 'Default'}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Try it</CardTitle>
        </CardHeader>
        <CardContent>
          <ol className="list-decimal space-y-1.5 pl-5 text-sm text-navy-700">
            <li>In Flagsmith, turn off <code>travel_companion_matching</code> for this environment (or just for your identity).</li>
            <li>Come back here within a few seconds. The flag flips, and the Travellers tab disappears from the navigation.</li>
            <li>The API now answers <code>403 FEATURE_DISABLED</code> for that feature, so hiding the UI isn’t the only protection.</li>
            <li>Tick “beta tester” in your profile to join a segment that enables <code>experimental_matching_algorithm</code> for you alone.</li>
          </ol>
        </CardContent>
      </Card>
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <span className="text-[13px] text-muted-foreground">{label}</span>
      <span className="text-right font-medium text-navy-900">{children}</span>
    </div>
  )
}

function OpsPanel() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const ops = useQuery({ queryKey: ['ops'], queryFn: api.ops, refetchInterval: 10_000 })
  const demo = useQuery({ queryKey: ['demo-info'], queryFn: api.demoInfo, staleTime: 60_000 })
  const [lastTest, setLastTest] = useState<string | null>(null)

  const test = useMutation({
    mutationFn: (resolve: boolean) => api.nudgebeeTest(resolve),
    onSuccess: (r) => {
      setLastTest(r.message)
      if (r.sent) toast.success(r.message)
      else toast.warning(r.message)
      void qc.invalidateQueries({ queryKey: ['ops'] })
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Test failed.'),
  })

  const reset = useMutation({
    mutationFn: api.resetDemo,
    onSuccess: (r) => {
      toast.success(r.message)
      void qc.invalidateQueries()
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Reset failed.'),
  })

  if (ops.isLoading) return <Skeleton className="h-96" />
  if (ops.isError) return <ErrorState error={ops.error} onRetry={() => ops.refetch()} />
  const o = ops.data!
  const nb = o.nudgebee

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-3">
        <MiniStat label="Service" value={`${o.service} ${o.version}`} />
        <MiniStat label="Environment" value={o.environment} />
        <MiniStat label="Data mode" value={o.demoMode ? 'Demo (seeded + sample data)' : 'Live data'} tone={o.demoMode ? 'amber' : 'green'} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Integrations</CardTitle>
          <CardDescription>Status comes from real calls the app has made, not a ping that always says “up”.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-2">
          {o.integrations.map((i) => (
            <IntegrationCard key={i.name} i={i} />
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Siren className="size-4.5 text-brand-500" /> NudgeBee
          </CardTitle>
          <CardDescription>Dependency failures and server errors are sent as events to the NudgeBee webhook, and resolved when they recover.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3 text-sm sm:grid-cols-2">
            <Row label="Event webhook">{nb.webhookConfigured ? <Badge tone="green">Configured</Badge> : <Badge tone="amber">Not configured</Badge>}</Row>
            <Row label="Events sent">{nb.eventsSent}</Row>
            <Row label="Events failed">{nb.eventsFailed}</Row>
            <Row label="Last event">{nb.lastEventAt ? `${relativeTime(nb.lastEventAt)} · ${nb.lastEventResult ?? ''}` : '—'}</Row>
            <Row label="Open alerts">{nb.openFingerprints.length}</Row>
          </div>
          {nb.openFingerprints.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {nb.openFingerprints.map((f) => (
                <Badge key={f} tone="red">
                  <AlertTriangle /> {f}
                </Badge>
              ))}
            </div>
          )}
          {!nb.webhookConfigured && (
            <Notice tone="warn">
              Set <code>NUDGEBEE_WEBHOOK_URL</code> and <code>NUDGEBEE_WEBHOOK_TOKEN</code> to send events. Until then, the app still logs structured errors and nothing is claimed as sent.
            </Notice>
          )}
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => test.mutate(false)} loading={test.isPending && test.variables === false} disabled={!nb.webhookConfigured}>
              {!(test.isPending && test.variables === false) && <Zap />} Send test alert
            </Button>
            <Button variant="ghost" onClick={() => test.mutate(true)} loading={test.isPending && test.variables === true} disabled={!nb.webhookConfigured}>
              Resolve test alert
            </Button>
          </div>
          {lastTest && <p className="text-sm text-navy-700">{lastTest}</p>}
          <details className="text-sm">
            <summary className="cursor-pointer font-semibold text-navy-800">Endpoints for monitoring</summary>
            <ul className="mt-2 space-y-1 text-[13px]">
              {Object.entries(o.endpoints).map(([k, v]) => (
                <li key={k} className="flex flex-wrap gap-2">
                  <span className="w-28 text-muted-foreground">{k}</span>
                  <code className="rounded bg-navy-50 px-1.5 py-0.5 text-xs">{v}</code>
                </li>
              ))}
            </ul>
          </details>
        </CardContent>
      </Card>

      {demo.data?.resetEnabled && user?.demoAccount && (
        <Card className="border-amber-200">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <FlaskConical className="size-4.5 text-amber-600" /> Demo data
            </CardTitle>
            <CardDescription>{demo.data.note}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" onClick={() => reset.mutate()} loading={reset.isPending}>
              {!reset.isPending && <RefreshCw />} Reset demo data
            </Button>
            <p className="mt-2 text-xs text-muted-foreground">Removes demo accounts and re-seeds them. Your own real account is not touched.</p>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

function MiniStat({ label, value, tone }: { label: string; value: string; tone?: 'amber' | 'green' }) {
  return (
    <Card className="p-4">
      <p className="text-xs font-semibold text-muted-foreground">{label}</p>
      <p className={cn('mt-1 text-sm font-bold text-navy-900', tone === 'amber' && 'text-amber-700', tone === 'green' && 'text-emerald-700')}>{value}</p>
    </Card>
  )
}

function IntegrationCard({ i }: { i: IntegrationStatus }) {
  return (
    <div className="rounded-xl border border-border p-4">
      <div className="flex items-center justify-between gap-2">
        <p className="font-bold text-navy-900">{i.name}</p>
        <Badge tone={statusTone[i.status]}>{i.status === 'NOT_CONFIGURED' ? 'Not configured' : i.status}</Badge>
      </div>
      <p className="mt-1.5 text-[13px] text-muted-foreground">{i.detail}</p>
      {i.configured && (
        <p className="mt-2 text-xs text-navy-600">
          {i.totalCalls} call{i.totalCalls === 1 ? '' : 's'} · {i.failedCalls} failed
          {i.lastSuccess && ` · ok ${relativeTime(i.lastSuccess)}`}
        </p>
      )}
      {i.lastError && (
        <p className="mt-1.5 flex items-start gap-1.5 text-xs text-destructive">
          <ShieldAlert className="mt-0.5 size-3.5 shrink-0" /> {i.lastError}
        </p>
      )}
    </div>
  )
}
