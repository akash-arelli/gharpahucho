import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, Check, Plus, UsersRound, X } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field } from '@/components/ui/label'
import { Input, Select } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { CityInput } from '@/components/CityInput'
import { EmptyState, ErrorState, PageHeader } from '@/components/common'
import { api, ApiError } from '@/lib/api'
import { useFeatures } from '@/lib/features'
import { addDays, modeLabel, shortDate, todayIso } from '@/lib/format'
import { FLAG, type GroupStatus, type GroupSummary, type TransportMode } from '@/lib/types'

export const groupStatusTone: Record<GroupStatus, 'amber' | 'green' | 'neutral' | 'red'> = { FORMING: 'amber', CONFIRMED: 'green', COMPLETED: 'neutral', CANCELLED: 'red' }

export default function Groups() {
  const { isEnabled } = useFeatures()
  const canCreate = isEnabled(FLAG.groups)
  const qc = useQueryClient()
  const navigate = useNavigate()
  const groups = useQuery({ queryKey: ['groups'], queryFn: api.groups })
  const invites = useQuery({ queryKey: ['group-invites'], queryFn: api.groupInvitations })
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ name: '', from: '', to: '', date: addDays(todayIso(), 21), mode: 'SHARED_CAR' as TransportMode, area: '', max: 4 })

  const create = useMutation({
    mutationFn: () =>
      api.createGroup({
        name: form.name.trim(),
        sourceCity: form.from.trim(),
        destinationCity: form.to.trim(),
        travelDate: form.date,
        planMode: form.mode,
        meetingArea: form.area.trim() || undefined,
        maxMembers: form.max,
      }),
    onSuccess: (g) => {
      toast.success('Group created')
      void qc.invalidateQueries({ queryKey: ['groups'] })
      navigate(`/app/groups/${g.id}`)
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not create the group.'),
  })

  const respond = useMutation({
    mutationFn: (v: { id: string; accept: boolean }) => api.respondGroup(v.id, v.accept),
    onSuccess: (g, v) => {
      toast.success(v.accept ? `You joined ${g.name}` : 'Invitation declined')
      void qc.invalidateQueries({ queryKey: ['groups'] })
      void qc.invalidateQueries({ queryKey: ['group-invites'] })
      void qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not respond.'),
  })

  const valid = form.name.trim() && form.from.trim() && form.to.trim() && form.date >= todayIso()

  return (
    <>
      <PageHeader
        eyebrow="Travel groups"
        title="Travel together"
        subtitle="Plan the ride, share the cost and keep everyone on the same page."
        actions={
          canCreate ? (
            <Button variant="brand" onClick={() => setOpen(true)}>
              <Plus /> New group
            </Button>
          ) : undefined
        }
      />

      {(invites.data?.length ?? 0) > 0 && (
        <section className="mb-8">
          <h2 className="mb-3 text-base font-bold text-navy-900">Invitations</h2>
          <div className="grid gap-3 md:grid-cols-2">
            {invites.data!.map((g) => (
              <Card key={g.id} className="border-brand-200 bg-brand-50/40 p-4">
                <GroupRow g={g} />
                <div className="mt-3 flex gap-2">
                  <Button size="sm" variant="brand" onClick={() => respond.mutate({ id: g.id, accept: true })} disabled={respond.isPending}>
                    <Check /> Join
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => respond.mutate({ id: g.id, accept: false })} disabled={respond.isPending}>
                    <X /> Decline
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        </section>
      )}

      <section>
        <h2 className="mb-3 text-base font-bold text-navy-900">Your groups</h2>
        {groups.isLoading ? (
          <div className="grid gap-3 md:grid-cols-2">
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
          </div>
        ) : groups.isError ? (
          <ErrorState error={groups.error} onRetry={() => groups.refetch()} />
        ) : groups.data!.length === 0 ? (
          <EmptyState
            icon={UsersRound}
            title="No groups yet"
            body="Create a group for a journey, or match with a traveller and a group is started for you."
            action={
              canCreate ? (
                <Button variant="brand" onClick={() => setOpen(true)}>
                  <Plus /> Create a group
                </Button>
              ) : (
                <Button asChild variant="outline">
                  <Link to="/app/travellers">Find companions</Link>
                </Button>
              )
            }
          />
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {groups.data!.map((g) => (
              <Link key={g.id} to={`/app/groups/${g.id}`}>
                <Card className="p-4 transition-shadow hover:shadow-pop">
                  <GroupRow g={g} />
                </Card>
              </Link>
            ))}
          </div>
        )}
      </section>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create a travel group</DialogTitle>
            <DialogDescription>You can invite matched travellers once it’s created.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Group name" className="sm:col-span-2">
              <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Diwali ride home" maxLength={80} />
            </Field>
            <Field label="From">
              <CityInput value={form.from} onChange={(v) => setForm({ ...form, from: v })} placeholder="Bengaluru" />
            </Field>
            <Field label="To">
              <CityInput value={form.to} onChange={(v) => setForm({ ...form, to: v })} placeholder="Hyderabad" />
            </Field>
            <Field label="Date">
              <Input type="date" min={todayIso()} value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} />
            </Field>
            <Field label="Plan to travel by">
              <Select value={form.mode} onChange={(e) => setForm({ ...form, mode: e.target.value as TransportMode })}>
                {(['SHARED_CAR', 'CAB', 'BUS', 'TRAIN', 'ANY'] as TransportMode[]).map((m) => (
                  <option key={m} value={m}>
                    {modeLabel[m]}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Meeting area (optional)" hint="A neighbourhood or landmark, not a home address.">
              <Input value={form.area} onChange={(e) => setForm({ ...form, area: e.target.value })} placeholder="Silk Board junction" maxLength={80} />
            </Field>
            <Field label="Max members">
              <Select value={form.max} onChange={(e) => setForm({ ...form, max: Number(e.target.value) })}>
                {[2, 3, 4, 5, 6, 8].map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button variant="brand" disabled={!valid} loading={create.isPending} onClick={() => create.mutate()}>
              Create group
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

function GroupRow({ g }: { g: GroupSummary }) {
  return (
    <div>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-bold text-navy-900">{g.name}</p>
          <p className="mt-0.5 flex items-center gap-1.5 text-sm text-navy-700">
            {g.sourceCity} <ArrowRight className="size-3.5 text-navy-400" /> {g.destinationCity} · {shortDate(g.travelDate)}
          </p>
        </div>
        <Badge tone={groupStatusTone[g.status]}>{g.status.charAt(0) + g.status.slice(1).toLowerCase()}</Badge>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">
        {g.joinedMembers} joined · organised by {g.ownerName}
      </p>
    </div>
  )
}
