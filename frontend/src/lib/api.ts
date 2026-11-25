import type {
  AlertsResponse,
  FamilyLink,
  FamilyView,
  Plans,
  RideAlert,
  BookingPlan,
  Radar,
  AssistantResponse,
  AuthResponse,
  CityDto,
  CreateGroupRequest,
  Dashboard,
  DemoInfo,
  ExplainResponse,
  Features,
  Group,
  GroupMessage,
  GroupSummary,
  Interest,
  InterestInbox,
  Me,
  MatchResponse,
  OpsStatus,
  Page,
  ParseResponse,
  PlanRequest,
  PlanResponse,
  PublicProfile,
  SavedTrip,
  TravelOption,
  Trip,
  TripDraft,
  TripPatch,
  TripRequest,
  UpdateProfileRequest,
  VoiceResponse,
} from './types'


const TOKEN_KEY = 'gp_token'
const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

/** Error envelope returned by every failing backend call. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: Record<string, unknown>
  readonly requestId?: string

  constructor(status: number, code: string, message: string, details: Record<string, unknown> = {}, requestId?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
    this.requestId = requestId
  }

  get isFeatureDisabled() {
    return this.code === 'FEATURE_DISABLED'
  }
}

let onUnauthorized: (() => void) | null = null
export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn
}

async function request<T>(path: string, init: { method?: string; body?: unknown; form?: FormData; signal?: AbortSignal } = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`
  let body: BodyInit | undefined
  if (init.form) {
    body = init.form
  } else if (init.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(init.body)
  }

  let res: Response
  try {
    res = await fetch(`${BASE}${path}`, { method: init.method ?? 'GET', headers, body, signal: init.signal })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the GharPahucho server. Check that the backend is running.')
  }

  if (res.status === 204) return undefined as T
  const text = await res.text()
  let data: unknown = undefined
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = undefined
    }
  }
  if (!res.ok) {
    const err = (data ?? {}) as { code?: string; message?: string; details?: Record<string, unknown>; requestId?: string }
    if (res.status === 401 && token && onUnauthorized) onUnauthorized()
    throw new ApiError(res.status, err.code ?? `HTTP_${res.status}`, err.message ?? `Request failed (${res.status})`, err.details ?? {}, err.requestId)
  }
  return data as T
}

const qs = (params: Record<string, string | number | boolean | undefined | null>) => {
  const p = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v))
  })
  const s = p.toString()
  return s ? `?${s}` : ''
}

export const api = {
  // auth
  register: (b: { email: string; password: string; displayName: string; homeCity?: string }) =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body: b }),
  login: (b: { email: string; password: string }) => request<AuthResponse>('/api/auth/login', { method: 'POST', body: b }),
  demoLogin: () => request<AuthResponse>('/api/auth/demo-login', { method: 'POST' }),
  demoInfo: () => request<DemoInfo>('/api/demo/info'),
  resetDemo: () => request<{ status: string; message: string }>('/api/demo/reset', { method: 'POST' }),

  // profile
  me: () => request<Me>('/api/users/me'),
  updateMe: (b: UpdateProfileRequest) => request<Me>('/api/users/me', { method: 'PUT', body: b }),
  publicProfile: (id: string) => request<PublicProfile>(`/api/users/${id}/profile`),

  // features and ops
  features: () => request<Features>('/api/features'),
  publicFeatures: () => request<Features>('/api/features/public'),
  ops: () => request<OpsStatus>('/api/ops/status'),
  nudgebeeTest: (resolve: boolean) =>
    request<{ sent: boolean; message: string }>(`/api/ops/nudgebee/test${qs({ resolve })}`, { method: 'POST' }),

  // maps
  cities: (q: string, signal?: AbortSignal) => request<CityDto[]>(`/api/maps/cities${qs({ q, limit: 8 })}`, { signal }),
  localities: () => request<Record<string, string[]>>('/api/maps/localities'),

  // planner and trips
  plan: (b: PlanRequest) => request<PlanResponse>('/api/trips/plan', { method: 'POST', body: b }),
  explain: (b: { plan: PlanRequest; optionId: string; language?: string }) =>
    request<ExplainResponse>('/api/trips/plan/explain', { method: 'POST', body: b }),
  createTrip: (b: TripRequest) => request<Trip>('/api/trips', { method: 'POST', body: b }),
  trips: (page = 0, size = 20) => request<Page<Trip>>(`/api/trips${qs({ page, size })}`),
  trip: (id: string) => request<Trip>(`/api/trips/${id}`),
  bookingPlan: (id: string) => request<BookingPlan>(`/api/trips/${id}/booking-plan`),
  bookingRadar: () => request<Radar>('/api/trips/booking-radar'),
  patchTrip: (id: string, b: TripPatch) => request<Trip>(`/api/trips/${id}`, { method: 'PATCH', body: b }),
  savedTrips: () => request<SavedTrip[]>('/api/trips/saved'),
  saveOption: (b: { tripId?: string; title: string; sourceCity: string; destinationCity: string; travelDate: string; option: TravelOption }) =>
    request<SavedTrip>('/api/trips/saved', { method: 'POST', body: b }),
  deleteSaved: (id: string) => request<void>(`/api/trips/saved/${id}`, { method: 'DELETE' }),

  // travellers
  matches: (tripId: string, page = 0, size = 12) => request<MatchResponse>(`/api/travellers/matches${qs({ tripId, page, size })}`),
  express: (b: { fromTripId: string; toTripId: string; message?: string }) =>
    request<Interest>('/api/travellers/interests', { method: 'POST', body: b }),
  inbox: () => request<InterestInbox>('/api/travellers/interests'),
  respond: (id: string, accept: boolean) =>
    request<Interest>(`/api/travellers/interests/${id}/respond`, { method: 'POST', body: { accept } }),
  withdraw: (id: string) => request<Interest>(`/api/travellers/interests/${id}/withdraw`, { method: 'POST' }),

  // groups
  groups: () => request<GroupSummary[]>('/api/groups'),
  groupInvitations: () => request<GroupSummary[]>('/api/groups/invitations'),
  group: (id: string) => request<Group>(`/api/groups/${id}`),
  createGroup: (b: CreateGroupRequest) => request<Group>('/api/groups', { method: 'POST', body: b }),
  updateGroup: (id: string, b: Partial<Pick<Group, 'name' | 'status' | 'planMode' | 'planNotes' | 'meetingArea' | 'plannedDepartureTime'>>) =>
    request<Group>(`/api/groups/${id}`, { method: 'PATCH', body: b }),
  inviteToGroup: (id: string, userId: string) => request<Group>(`/api/groups/${id}/invite`, { method: 'POST', body: { userId } }),
  respondGroup: (id: string, accept: boolean) => request<Group>(`/api/groups/${id}/respond`, { method: 'POST', body: { accept } }),
  leaveGroup: (id: string) => request<void>(`/api/groups/${id}/leave`, { method: 'POST' }),
  messages: (id: string) => request<GroupMessage[]>(`/api/groups/${id}/messages`),
  sendMessage: (id: string, body: string) => request<GroupMessage>(`/api/groups/${id}/messages`, { method: 'POST', body: { body } }),

  // assistant
  parse: (text: string, languageHint?: string) =>
    request<ParseResponse>('/api/assistant/parse', { method: 'POST', body: { text, languageHint } }),
  chat: (message: string, context?: TripDraft | null, languageHint?: string) =>
    request<AssistantResponse>('/api/assistant/chat', { method: 'POST', body: { message, context: context ?? undefined, languageHint } }),
  voice: (audio: Blob, languageHint?: string, context?: TripDraft | null) => {
    const form = new FormData()
    form.append('audio', audio, 'voice.webm')
    if (languageHint) form.append('languageHint', languageHint)
    if (context) form.append('context', JSON.stringify(context))
    return request<VoiceResponse>('/api/assistant/voice', { method: 'POST', form })
  },

  // Festival Pass (demo checkout: no payment is taken)
  plans: () => request<Plans>('/api/billing/plans'),
  demoCheckout: () => request<Me>('/api/billing/demo-checkout', { method: 'POST' }),
  demoCancel: () => request<Me>('/api/billing/demo-cancel', { method: 'POST' }),

  // ride alerts
  alerts: () => request<AlertsResponse>('/api/alerts'),
  createAlert: (tripId: string, flexDays = 1) => request<RideAlert>('/api/alerts', { method: 'POST', body: { tripId, flexDays } }),
  seenAlert: (id: string) => request<void>(`/api/alerts/${id}/seen`, { method: 'POST' }),
  deleteAlert: (id: string) => request<void>(`/api/alerts/${id}`, { method: 'DELETE' }),

  // family link
  familyLinks: (tripId: string) => request<FamilyLink[]>(`/api/trips/${tripId}/family-links`),
  createFamilyLink: (tripId: string, b: { language: string; familyName?: string; message?: string }) =>
    request<FamilyLink>(`/api/trips/${tripId}/family-links`, { method: 'POST', body: b }),
  setFamilyStatus: (id: string, status: string, message?: string) =>
    request<FamilyLink>(`/api/family-links/${id}`, { method: 'PATCH', body: { status, message } }),
  revokeFamilyLink: (id: string) => request<void>(`/api/family-links/${id}`, { method: 'DELETE' }),
  familyView: (token: string) => request<FamilyView>(`/api/family/${encodeURIComponent(token)}`),

  // dashboard
  dashboard: () => request<Dashboard>('/api/dashboard'),
}
