// Wire types: a 1:1 mirror of the backend DTOs. Keep in sync with backend/src/main/java/com/gharpahucho/dto.

export type TransportMode = 'ANY' | 'TRAIN' | 'BUS' | 'SHARED_CAR' | 'CAB'
export type Priority = 'CHEAPEST' | 'FASTEST' | 'COMFORTABLE'
export type DataSource = 'ROUTE_CALCULATION' | 'USER_TRIP' | 'SIMULATED_INVENTORY' | 'LIVE_PROVIDER'
export type Visibility = 'PRIVATE' | 'DISCOVERABLE'
export type TripStatus = 'PLANNED' | 'COMPLETED' | 'CANCELLED'
export type InterestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN'
export type GroupStatus = 'FORMING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED'
export type MemberStatus = 'INVITED' | 'JOINED' | 'DECLINED' | 'LEFT'
export type MemberRole = 'OWNER' | 'MEMBER'

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ---- users
export interface Preferences {
  preferredMode: TransportMode
  priority: Priority
  defaultMaxBudget?: number | null
  willingToShare: boolean
  firstNameOnly: boolean
  discoverable: boolean
  notes?: string | null
}
export interface Me {
  id: string
  email: string
  displayName: string
  homeCity?: string | null
  homeLocality?: string | null
  preferredLanguage: string
  onboarded: boolean
  betaTester: boolean
  demoAccount: boolean
  plan: 'free' | 'plus'
  preferences?: Preferences | null
  createdAt: string
}
export interface AuthResponse {
  token: string
  user: Me
}
export interface UpdateProfileRequest {
  displayName?: string
  homeCity?: string
  homeLocality?: string
  preferredLanguage?: string
  betaTester?: boolean
  onboarded?: boolean
  preferences?: Preferences
}
export interface PublicTrip {
  tripId: string
  sourceCity: string
  sourceArea: string
  destinationCity: string
  travelDate: string
  preferredMode: TransportMode
  seatsAvailable: number
  costPerSeat?: number | null
}
export interface PublicProfile {
  userId: string
  name: string
  homeArea: string
  preferredLanguage: string
  memberSince: string
  upcomingTrips: PublicTrip[]
}

// ---- trips
export interface TripRequest {
  sourceCity: string
  sourceLocality?: string
  destinationCity: string
  travelDate: string
  flexDays?: number
  returnDate?: string
  travellers?: number
  maxBudget?: number
  preferredMode?: TransportMode
  priority?: Priority
  arriveBy?: string
  departureTime?: string
  willingToShare?: boolean
  seatsAvailable?: number
  costPerSeat?: number
  notes?: string
  visibility?: Visibility
}
export interface Trip {
  id: string
  sourceCity: string
  sourceLocality?: string
  destinationCity: string
  travelDate: string
  flexDays: number
  returnDate?: string
  travellers: number
  maxBudget?: number
  preferredMode: TransportMode
  priority: Priority
  arriveBy?: string
  departureTime?: string
  willingToShare: boolean
  seatsAvailable: number
  costPerSeat?: number
  notes?: string
  visibility: Visibility
  status: TripStatus
  dataOrigin: 'USER_CREATED' | 'DEMO_SEED'
  createdAt: string
}
export interface TripPatch {
  visibility?: Visibility
  status?: TripStatus
  seatsAvailable?: number
  costPerSeat?: number
  willingToShare?: boolean
  departureTime?: string
  sourceLocality?: string
  notes?: string
}

// ---- planning
export interface PlanRequest {
  sourceCity: string
  destinationCity: string
  travelDate: string
  returnDate?: string
  travellers?: number
  maxBudget?: number
  preferredMode?: TransportMode
  priority?: Priority
  arriveBy?: string
}
export interface CityDto {
  name: string
  state: string
  lat: number
  lon: number
}
export interface RouteDto {
  from: CityDto
  to: CityDto
  distanceKm: number
  durationMinutes: number
  engine: 'OSRM' | 'GOOGLE_ROUTES' | 'ESTIMATED_STRAIGHT_LINE' | string
  measured: boolean
  note: string
  geometry: [number, number][]
}
export interface Leg {
  mode: TransportMode
  from: string
  to: string
  departure: string
  arrival: string
  operator: string
  serviceName: string
}
export interface TravelOption {
  id: string
  mode: TransportMode
  modeLabel: string
  title: string
  operator: string
  legs: Leg[]
  departure: string
  arrival: string
  durationMinutes: number
  costPerPerson: number
  totalCost: number
  transfers: number
  stops?: string
  dataSource: DataSource
  dataSourceLabel: string
  dataSourceNote: string
  availability: string
  availabilityLabel: string
  meetsConstraints: boolean
  violations: string[]
  score: number
  overallScore: number
  tags: string[]
  reasons: string[]
  comfort: number
  seatsLeft?: number | null
  hostTripId?: string | null
  hostName?: string | null
  festiveNote?: string | null
}
export interface BudgetTip {
  type: string
  title: string
  detail: string
  savingPerPerson: number
  suggestedDate?: string | null
  optionId?: string | null
}
export interface FestiveInfo {
  name: string
  demandLevel: number
  note: string
}
export interface PlanResponse {
  request: PlanRequest
  route: RouteDto
  options: TravelOption[]
  budgetTips: BudgetTip[]
  appliedFlags: Record<string, boolean>
  notices: string[]
  totalCandidates: number
  withinConstraints: number
  festive?: FestiveInfo | null
  disclaimer: string
  generatedAt: string
}
export interface ExplainResponse {
  optionId: string
  explanation?: string | null
  source: 'SARVAM_AI' | 'UNAVAILABLE'
  available: boolean
  notice?: string | null
}
export interface SavedTrip {
  id: string
  tripId?: string | null
  title: string
  sourceCity: string
  destinationCity: string
  travelDate: string
  option: TravelOption
  createdAt: string
}

// ---- matching
export interface MatchCandidate {
  tripId: string
  userId: string
  name: string
  homeArea: string
  sourceCity: string
  sourceArea: string
  destinationCity: string
  travelDate: string
  flexDays: number
  preferredMode: TransportMode
  maxBudget?: number | null
  travellers: number
  seatsAvailable: number
  costPerSeat?: number | null
  willingToShare: boolean
  notes?: string | null
  score: number
  breakdown: Record<string, number>
  reasons: string[]
  algorithm: string
  interestStatus?: InterestStatus | null
  demoData: boolean
}
export interface MatchResponse {
  tripId?: string
  algorithm: string
  minScore: number
  radiusKm: number
  total: number
  matches: MatchCandidate[]
  weights: Record<string, number>
  notices: string[]
}
export interface Interest {
  id: string
  direction: 'INCOMING' | 'OUTGOING'
  status: InterestStatus
  otherUserId: string
  otherName: string
  sourceCity: string
  destinationCity: string
  travelDate: string
  message?: string | null
  createdAt: string
  groupId?: string | null
}
export interface InterestInbox {
  incoming: Interest[]
  outgoing: Interest[]
}

// ---- groups
export interface GroupMember {
  userId: string
  name: string
  homeArea: string
  role: MemberRole
  status: MemberStatus
}
export interface CostSharing {
  basis: string
  estimatedTotal?: number | null
  perPerson?: number | null
  memberCount: number
  dataSource: DataSource
  note: string
}
export interface Group {
  id: string
  name: string
  sourceCity: string
  destinationCity: string
  travelDate: string
  status: GroupStatus
  planMode: TransportMode
  planNotes?: string | null
  meetingArea?: string | null
  plannedDepartureTime?: string | null
  maxMembers: number
  ownerName: string
  owner: boolean
  myStatus?: MemberStatus | null
  members: GroupMember[]
  costSharing: CostSharing
  createdAt: string
}
export interface GroupSummary {
  id: string
  name: string
  sourceCity: string
  destinationCity: string
  travelDate: string
  status: GroupStatus
  joinedMembers: number
  myStatus?: MemberStatus | null
  ownerName: string
}
export interface CreateGroupRequest {
  name: string
  sourceCity: string
  destinationCity: string
  travelDate: string
  planMode?: TransportMode
  planNotes?: string
  meetingArea?: string
  plannedDepartureTime?: string
  maxMembers?: number
  inviteUserIds?: string[]
}
export interface GroupMessage {
  id: string
  senderId: string
  senderName: string
  body: string
  createdAt: string
  mine: boolean
}

// ---- assistant
export interface TripDraft {
  sourceCity?: string | null
  destinationCity?: string | null
  travelDate?: string | null
  returnDate?: string | null
  travellers?: number | null
  maxBudget?: number | null
  preferredMode?: TransportMode | null
  priority?: Priority | null
  arriveBy?: string | null
  willingToShare?: boolean | null
}
export interface ParseResponse {
  draft: TripDraft
  extractionSource: 'SARVAM_AI' | 'RULE_BASED_FALLBACK'
  detectedLanguage?: string
  missing: string[]
  notices: string[]
}
export interface AssistantAction {
  type: string
  label: string
  payload: Record<string, unknown>
}
export interface AssistantResponse {
  reply: string
  replyLanguage?: string
  intent: string
  draft: TripDraft
  extractionSource: 'SARVAM_AI' | 'RULE_BASED_FALLBACK'
  replySource: 'SARVAM_AI' | 'TEMPLATE'
  aiAvailable: boolean
  notices: string[]
  options: TravelOption[]
  matches: MatchCandidate[]
  actions: AssistantAction[]
}
export interface VoiceResponse {
  transcript: string
  transcriptLanguage?: string
  transcriptionSource: string
  assistant: AssistantResponse
}

// ---- dashboard
export interface Activity {
  type: string
  message: string
  createdAt: string
}
export interface Dashboard {
  greetingName: string
  upcomingJourneys: Trip[]
  savedTrips: SavedTrip[]
  recommendedPlan?: PlanResponse | null
  compatibleTravellers: MatchCandidate[]
  groupInvitations: GroupSummary[]
  myGroups: GroupSummary[]
  pendingRequests: number
  recentActivity: Activity[]
}

// ---- features and ops
export interface FeatureState {
  key: string
  enabled: boolean
  source: 'FLAGSMITH' | 'FALLBACK_DEFAULT'
  title: string
  description: string
  effect: string
}
export interface ConfigValue {
  key: string
  value: string
  source: 'FLAGSMITH' | 'FALLBACK_DEFAULT'
  title: string
  description: string
}
export interface FlagsmithStatus {
  configured: boolean
  mode: 'LOCAL_EVALUATION' | 'REMOTE_EVALUATION' | 'NOT_CONFIGURED'
  apiUrl: string
  healthy: boolean
  lastSuccess?: string | null
  lastFailure?: string | null
  lastError?: string | null
  refreshIntervalSeconds: number
}
export interface Features {
  flags: Record<string, boolean>
  config: Record<string, string>
  states: FeatureState[]
  configStates: ConfigValue[]
  traits: Record<string, unknown>
  identity?: string | null
  flagsmith: FlagsmithStatus
  evaluatedAt: string
}
export interface IntegrationStatus {
  name: string
  configured: boolean
  status: 'UP' | 'DEGRADED' | 'DOWN' | 'NOT_CONFIGURED'
  totalCalls: number
  failedCalls: number
  consecutiveFailures: number
  lastSuccess?: string | null
  lastFailure?: string | null
  lastError?: string | null
  detail: string
}
export interface NudgeBeeStatus {
  webhookConfigured: boolean
  graphqlConfigured: boolean
  eventsSent: number
  eventsFailed: number
  lastEventAt?: string | null
  lastEventResult?: string | null
  openFingerprints: string[]
  docs: string
}
export interface OpsStatus {
  service: string
  version: string
  environment: string
  demoMode: boolean
  integrations: IntegrationStatus[]
  nudgebee: NudgeBeeStatus
  endpoints: Record<string, string>
  checkedAt: string
}
export interface DemoInfo {
  demoMode: boolean
  resetEnabled: boolean
  demoLogin: boolean
  demoEmail: string
  betaEmail: string
  note: string
}

export const FLAG = {
  planner: 'smart_trip_planner',
  voice: 'ai_voice_trip_creation',
  assistant: 'sarvam_multilingual_assistant',
  companions: 'travel_companion_matching',
  sharedRides: 'shared_ride_matching',
  alternatives: 'alternative_route_recommendations',
  budget: 'budget_optimization',
  groups: 'travel_group_creation',
  experimental: 'experimental_matching_algorithm',
  explanations: 'ai_trip_explanations',
  bookingPlan: 'festival_booking_plan',
  rideAlerts: 'ride_alerts',
  unlimitedAlerts: 'unlimited_ride_alerts',
  familyLink: 'family_link',
} as const

// ---------------------------------------------------------------- Festival Pass, ride alerts, family link

export interface Perk {
  title: string
  detail: string
  free: string
  plus: string
}
export interface Plans {
  currentPlan: 'free' | 'plus'
  priceInr: number
  period: string
  perks: Perk[]
  demoCheckoutAvailable: boolean
}
export interface RideMatch {
  tripId: string
  hostId: string
  host: string
  travelDate: string
  departureTime?: string | null
  startsNear?: string | null
  seats: number
  costPerSeat?: number | null
  fresh: boolean
}
export interface RideAlert {
  id: string
  route: string
  travelDate: string
  flexDays: number
  rides: RideMatch[]
  newRides: number
  createdAt: string
}
export interface AlertsResponse {
  alerts: RideAlert[]
  /** -1 when unlimited */
  limit: number
  unlimited: boolean
  upgradeMessage?: string | null
}
export type FamilyStatus = 'PLANNED' | 'ON_THE_WAY' | 'DELAYED' | 'REACHED'
export interface FamilyLink {
  id: string
  token: string
  path: string
  language: 'en' | 'hi' | 'te'
  familyName?: string | null
  status: FamilyStatus
  statusUpdatedAt?: string | null
  message?: string | null
  views: number
  lastViewedAt?: string | null
  createdAt: string
}
export interface FamilyView {
  travellerName: string
  familyName?: string | null
  sourceCity: string
  destinationCity: string
  travelDate: string
  departureTime?: string | null
  meetingArea?: string | null
  status: FamilyStatus
  statusLabel: string
  headline: string
  message?: string | null
  updatedAt: string
  language: 'en' | 'hi' | 'te'
  footer: string
}

export interface BookingWindow {
  status: 'UPCOMING' | 'OPEN' | 'CLOSED'
  windowDays: number
  opensOn: string
  opensAt: string
  daysUntilOpen: number
  headline: string
}
export interface BookingStep {
  order: number
  title: string
  detail?: string | null
  when?: string | null
  locked: boolean
}
export interface DayPressure {
  date: string
  weekday: string
  pressure: number
  level: 'LOW' | 'MODERATE' | 'HIGH' | 'PEAK'
  vsChosenPct: number
  chosen: boolean
  calmer: boolean
}
export interface BookingPlan {
  tripId: string
  sourceCity: string
  destinationCity: string
  travelDate: string
  today: string
  fullAccess: boolean
  festival?: { name: string; start: string; peak: string; end: string; travelDuringFestival: boolean; daysToStart: number } | null
  railWindow: BookingWindow
  days: DayPressure[]
  steps: BookingStep[]
  lock?: { requiredPlan: string; message: string } | null
  notes: string[]
}
export interface RadarItem {
  tripId: string
  route: string
  travelDate: string
  status: BookingWindow['status']
  opensOn: string
  daysUntilOpen: number
  headline: string
  urgent: boolean
}
export interface Radar {
  items: RadarItem[]
  hiddenByPlan: number
  fullAccess: boolean
  upgradeMessage?: string | null
}
export type FlagKey = (typeof FLAG)[keyof typeof FLAG]
