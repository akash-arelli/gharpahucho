import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, Check, Crown, LogOut, MapPin, Pencil, Send, UserPlus, Wallet, X } from 'lucide-react'
import { toast } from 'sonner'
import { Avatar } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field } from '@/components/ui/label'
import { Input, Select, Textarea } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { ErrorState, FeatureOff, Notice, SourceBadge } from '@/components/common'
import { groupStatusTone } from '@/pages/Groups'
import { api, ApiError } from '@/lib/api'
import { useFeatures } from '@/lib/features'
import { modeLabel, relativeTime, rupees, shortDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import { FLAG, type Group, type GroupStatus, type TransportMode } from '@/lib/types'

const nextStatus: Partial<Record<GroupStatus, { to: GroupStatus; label: string }[]>> = {
  FORMING: [
    { to: 'CONFIRMED', label: 'Confirm the plan' },
    { to: 'CANCELLED', label: 'Cancel group' },
  ],
  CONFIRMED: [
    { to: 'COMPLETED', label: 'Mark completed' },
    { to: 'FORMING', label: 'Reopen' },
    { to: 'CANCELLED', label: 'Cancel group' },
  ],
}

export default function GroupDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { isEnabled } = useFeatures()
  const [editing, setEditing] = useState(false)
  const [inviteOpen, setInviteOpen] = useState(false)

  const g = useQuery({ queryKey: ['group', id], queryFn: () => api.group(id!), enabled: Boolean(id), refetchInterval: 10_000 })
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['group', id] })
    void qc.invalidateQueries({ queryKey: ['groups'] })
    void qc.invalidateQueries({ queryKey: ['group-invites'] })
    void qc.invalidateQueries({ queryKey: ['dashboard'] })
  }

  const respond = useMutation({
    mutationFn: (accept: boolean) => api.respondGroup(id!, accept),
    onSuccess: (_, accept) => {
      toast.success(accept ? 'You’ve joined the group' : 'Invitation declined')
      refresh()
      if (!accept) navigate('/app/groups')
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not respond.'),
  })
  const leave = useMutation({
    mutationFn: () => api.leaveGroup(id!),
    onSuccess: () => {
      toast.success('You left the group')
      refresh()
      navigate('/app/groups')
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not leave.'),
  })
  const setStatus = useMutation({
    mutationFn: (s: GroupStatus) => api.updateGroup(id!, { status: s }),
    onSuccess: () => {
      toast.success('Group updated')
      refresh()
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not update.'),
  })

  if (g.isLoading) return <Skeleton className="h-96" />
  if (g.isError) return <ErrorState error={g.error} onRetry={() => g.refetch()} title="Couldn’t open this group" />
  const group = g.data!
  const joined = group.myStatus === 'JOINED'
  const invited = group.myStatus === 'INVITED'
  const active = group.status === 'FORMING' || group.status === 'CONFIRMED'
  const joinedMembers = group.members.filter((m) => m.status === 'JOINED')

  return (
    <>
      <Button asChild variant="ghost" size="sm" className="-ml-2 mb-3">
        <Link to="/app/groups">
          <ArrowLeft /> All groups
        </Link>
      </Button>

      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-2xl font-extrabold text-navy-900 sm:text-3xl">{group.name}</h1>
            <Badge tone={groupStatusTone[group.status]}>{group.status.charAt(0) + group.status.slice(1).toLowerCase()}</Badge>
          </div>
          <p className="mt-1.5 flex flex-wrap items-center gap-2 text-navy-700">
            {group.sourceCity} <ArrowRight className="size-4 text-navy-400" /> {group.destinationCity} · {shortDate(group.travelDate)}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {group.owner && active && (
            <>
              <Button variant="outline" onClick={() => setInviteOpen(true)} disabled={!isEnabled(FLAG.groups)}>
                <UserPlus /> Invite
              </Button>
              {nextStatus[group.status]?.map((n) => (
                <Button key={n.to} variant={n.to === 'CONFIRMED' ? 'brand' : n.to === 'CANCELLED' ? 'ghost' : 'soft'} onClick={() => setStatus.mutate(n.to)} loading={setStatus.isPending && setStatus.variables === n.to}>
                  {n.label}
                </Button>
              ))}
            </>
          )}
          {joined && !group.owner && active && (
            <Button variant="ghost" onClick={() => leave.mutate()} loading={leave.isPending}>
              <LogOut /> Leave
            </Button>
          )}
        </div>
      </div>

      {invited && (
        <Card className="mb-6 flex flex-col gap-3 border-brand-200 bg-brand-50/50 p-4 sm:flex-row sm:items-center">
          <p className="flex-1 text-sm font-semibold text-navy-900">{group.ownerName} invited you to join this group.</p>
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => respond.mutate(false)} disabled={respond.isPending}>
              <X /> Decline
            </Button>
            <Button variant="brand" onClick={() => respond.mutate(true)} loading={respond.isPending}>
              <Check /> Join group
            </Button>
          </div>
        </Card>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="min-w-0 space-y-6">
          <Card>
            <CardHeader className="flex-row items-center justify-between">
              <CardTitle>The plan</CardTitle>
              {group.owner && active && (
                <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>
                  <Pencil /> Edit
                </Button>
              )}
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <Fact label="Travelling by" value={modeLabel[group.planMode]} />
              <Fact label="Planned departure" value={group.plannedDepartureTime ? group.plannedDepartureTime.slice(0, 5) : 'Not set yet'} />
              <Fact
                label="Meeting area"
                value={
                  group.meetingArea ? (
                    <span className="inline-flex items-center gap-1">
                      <MapPin className="size-3.5 text-brand-500" /> {group.meetingArea}
                    </span>
                  ) : (
                    'Not set yet'
                  )
                }
              />
              <Fact label="Group size" value={`${joinedMembers.length} of ${group.maxMembers}`} />
              {group.planNotes && (
                <div className="sm:col-span-2">
                  <p className="text-xs font-semibold text-muted-foreground">Notes</p>
                  <p className="mt-1 rounded-xl bg-navy-50 px-3.5 py-2.5 text-sm text-navy-800">{group.planNotes}</p>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Wallet className="size-4.5 text-brand-500" /> Cost sharing
              </CardTitle>
            </CardHeader>
            <CardContent>
              {group.costSharing.perPerson != null ? (
                <div className="flex flex-wrap items-end gap-x-8 gap-y-3">
                  <div>
                    <p className="font-display text-3xl font-extrabold text-navy-900">{rupees(group.costSharing.perPerson)}</p>
                    <p className="text-xs text-muted-foreground">per person · {group.costSharing.memberCount} sharing</p>
                  </div>
                  <div>
                    <p className="font-display text-xl font-bold text-navy-800">{rupees(group.costSharing.estimatedTotal)}</p>
                    <p className="text-xs text-muted-foreground">estimated total</p>
                  </div>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No estimate available for this route yet.</p>
              )}
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <SourceBadge source={group.costSharing.dataSource} />
                <span className="text-xs text-muted-foreground">{group.costSharing.basis}</span>
              </div>
              <p className="mt-2 text-xs leading-relaxed text-muted-foreground">{group.costSharing.note}</p>
            </CardContent>
          </Card>

          <Chat groupId={group.id} canPost={joined && active} joined={joined} />
        </div>

        <aside>
          <Card>
            <CardHeader>
              <CardTitle>Members</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {group.members.map((m) => (
                <div key={m.userId} className="flex items-center gap-3">
                  <Avatar name={m.name} className="size-10" />
                  <div className="min-w-0 flex-1">
                    <Link to={`/app/travellers/${m.userId}`} className="flex items-center gap-1.5 truncate text-sm font-bold text-navy-900 hover:text-brand-600">
                      {m.name} {m.role === 'OWNER' && <Crown className="size-3.5 text-brand-500" aria-label="Organiser" />}
                    </Link>
                    <p className="truncate text-xs text-muted-foreground">{m.homeArea || 'Area not shared'}</p>
                  </div>
                  {m.status !== 'JOINED' && <Badge tone={m.status === 'INVITED' ? 'amber' : 'neutral'}>{m.status.charAt(0) + m.status.slice(1).toLowerCase()}</Badge>}
                </div>
              ))}
            </CardContent>
          </Card>
        </aside>
      </div>

      {editing && <EditPlan group={group} onClose={() => setEditing(false)} onSaved={refresh} />}
      {inviteOpen && <InviteDialog group={group} onClose={() => setInviteOpen(false)} onSaved={refresh} />}
    </>
  )
}

function Fact({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs font-semibold text-muted-foreground">{label}</p>
      <p className="mt-0.5 text-sm font-semibold text-navy-900">{value}</p>
    </div>
  )
}

function Chat({ groupId, canPost, joined }: { groupId: string; canPost: boolean; joined: boolean }) {
  const qc = useQueryClient()
  const [body, setBody] = useState('')
  const endRef = useRef<HTMLDivElement>(null)
  const q = useQuery({ queryKey: ['messages', groupId], queryFn: () => api.messages(groupId), enabled: joined, refetchInterval: 4000 })
  const send = useMutation({
    mutationFn: (b: string) => api.sendMessage(groupId, b),
    onSuccess: () => {
      setBody('')
      void qc.invalidateQueries({ queryKey: ['messages', groupId] })
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Message not sent.'),
  })

  const count = q.data?.length ?? 0
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [count])

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (body.trim()) send.mutate(body.trim())
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Group chat</CardTitle>
      </CardHeader>
      <CardContent>
        {!joined ? (
          <p className="rounded-xl bg-navy-50 px-4 py-6 text-center text-sm text-muted-foreground">Join the group to read and send messages.</p>
        ) : (
          <>
            <div className="max-h-96 min-h-40 space-y-3 overflow-y-auto rounded-xl bg-navy-50/60 p-3.5" role="log" aria-live="polite">
              {q.isLoading && <Skeleton className="h-10" />}
              {q.data?.length === 0 && <p className="py-8 text-center text-sm text-muted-foreground">No messages yet. Say hello 👋</p>}
              {q.data?.map((m) => (
                <div key={m.id} className={cn('flex flex-col', m.mine ? 'items-end' : 'items-start')}>
                  <div className={cn('max-w-[85%] rounded-2xl px-3.5 py-2 text-sm', m.mine ? 'rounded-br-md bg-navy-900 text-white' : 'rounded-bl-md bg-white text-navy-900 shadow-sm')}>
                    {!m.mine && <p className="mb-0.5 text-[11px] font-bold text-brand-600">{m.senderName}</p>}
                    <p className="whitespace-pre-wrap break-words">{m.body}</p>
                  </div>
                  <span className="mt-0.5 px-1 text-[10px] text-muted-foreground">{relativeTime(m.createdAt)}</span>
                </div>
              ))}
              <div ref={endRef} />
            </div>
            {canPost ? (
              <form onSubmit={submit} className="mt-3 flex gap-2">
                <Input value={body} onChange={(e) => setBody(e.target.value)} placeholder="Write a message…" maxLength={1000} aria-label="Message" />
                <Button type="submit" variant="brand" size="icon" loading={send.isPending} disabled={!body.trim()} aria-label="Send">
                  {!send.isPending && <Send />}
                </Button>
              </form>
            ) : (
              <p className="mt-3 text-center text-xs text-muted-foreground">This group is closed, so messages are read-only.</p>
            )}
          </>
        )}
        <Notice tone="info" className="mt-3">
          Messages are visible to joined members only. Don’t share phone numbers or exact addresses until you’re comfortable.
        </Notice>
      </CardContent>
    </Card>
  )
}

function EditPlan({ group, onClose, onSaved }: { group: Group; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState({
    mode: group.planMode,
    area: group.meetingArea ?? '',
    time: group.plannedDepartureTime?.slice(0, 5) ?? '',
    notes: group.planNotes ?? '',
  })
  const save = useMutation({
    mutationFn: () =>
      api.updateGroup(group.id, {
        planMode: f.mode,
        meetingArea: f.area.trim() || undefined,
        plannedDepartureTime: f.time || undefined,
        planNotes: f.notes.trim() || undefined,
      }),
    onSuccess: () => {
      toast.success('Plan updated')
      onSaved()
      onClose()
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not save.'),
  })
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit the plan</DialogTitle>
          <DialogDescription>Everyone in the group sees these details.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Travelling by">
            <Select value={f.mode} onChange={(e) => setF({ ...f, mode: e.target.value as TransportMode })}>
              {(['SHARED_CAR', 'CAB', 'BUS', 'TRAIN', 'ANY'] as TransportMode[]).map((m) => (
                <option key={m} value={m}>
                  {modeLabel[m]}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Departure time">
            <Input type="time" value={f.time} onChange={(e) => setF({ ...f, time: e.target.value })} />
          </Field>
          <Field label="Meeting area" className="sm:col-span-2">
            <Input value={f.area} onChange={(e) => setF({ ...f, area: e.target.value })} maxLength={80} placeholder="Silk Board junction" />
          </Field>
          <Field label="Notes" className="sm:col-span-2">
            <Textarea value={f.notes} onChange={(e) => setF({ ...f, notes: e.target.value })} maxLength={500} placeholder="Luggage, stops, who’s driving…" />
          </Field>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="brand" onClick={() => save.mutate()} loading={save.isPending}>
            Save plan
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Invite from people you've already matched with, so strangers can't be added by ID. */
function InviteDialog({ group, onClose, onSaved }: { group: Group; onClose: () => void; onSaved: () => void }) {
  const inbox = useQuery({ queryKey: ['inbox'], queryFn: api.inbox })
  const present = new Set(group.members.filter((m) => m.status === 'JOINED' || m.status === 'INVITED').map((m) => m.userId))
  const partners = new Map<string, string>()
  ;[...(inbox.data?.incoming ?? []), ...(inbox.data?.outgoing ?? [])]
    .filter((i) => i.status === 'ACCEPTED' && !present.has(i.otherUserId))
    .forEach((i) => partners.set(i.otherUserId, i.otherName))

  const invite = useMutation({
    mutationFn: (userId: string) => api.inviteToGroup(group.id, userId),
    onSuccess: () => {
      toast.success('Invitation sent')
      onSaved()
    },
    onError: (e) => {
      if (e instanceof ApiError && e.isFeatureDisabled) toast.error(e.message)
      else toast.error(e instanceof ApiError ? e.message : 'Could not invite.')
    },
  })

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Invite a matched traveller</DialogTitle>
          <DialogDescription>Only people you’ve matched with can be invited.</DialogDescription>
        </DialogHeader>
        {inbox.isLoading ? (
          <Skeleton className="h-20" />
        ) : partners.size === 0 ? (
          <div className="space-y-3">
            <p className="rounded-xl bg-navy-50 px-4 py-5 text-center text-sm text-muted-foreground">No matched travellers to invite yet.</p>
            <FeatureOffHint />
            <Button asChild variant="outline" className="w-full">
              <Link to="/app/travellers">Find companions</Link>
            </Button>
          </div>
        ) : (
          <div className="space-y-2">
            {[...partners].map(([uid, name]) => (
              <div key={uid} className="flex items-center gap-3 rounded-xl border border-border p-3">
                <Avatar name={name} />
                <span className="flex-1 font-semibold text-navy-900">{name}</span>
                <Button size="sm" variant="brand" onClick={() => invite.mutate(uid)} loading={invite.isPending && invite.variables === uid}>
                  Invite
                </Button>
              </div>
            ))}
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

function FeatureOffHint() {
  const { isEnabled } = useFeatures()
  return isEnabled(FLAG.groups) ? null : <FeatureOff name="Group invitations" />
}
