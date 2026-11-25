import type { DataSource, TransportMode } from './types'

const inr = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 })
export const rupees = (n?: number | null) => (n === undefined || n === null ? '—' : `₹${inr.format(n)}`)

export function duration(minutes: number) {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  return m === 0 ? `${h}h` : `${h}h ${String(m).padStart(2, '0')}m`
}

export const timeOf = (iso: string) => iso.slice(11, 16)

export function dateOnly(iso: string) {
  // Parse as local calendar date: "2026-12-20" must never shift by timezone.
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d)
}

export const shortDate = (iso: string) =>
  dateOnly(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })

export const longDate = (iso: string) =>
  dateOnly(iso).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })

export function dayOffset(iso: string, base: string) {
  const ms = dateOnly(iso).getTime() - dateOnly(base).getTime()
  return Math.round(ms / 86_400_000)
}

export const isoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

export const todayIso = () => isoDate(new Date())

export function addDays(iso: string, days: number) {
  const d = dateOnly(iso)
  d.setDate(d.getDate() + days)
  return isoDate(d)
}

export function relativeTime(iso: string) {
  const diff = Date.now() - new Date(iso).getTime()
  const min = Math.round(diff / 60_000)
  if (min < 1) return 'just now'
  if (min < 60) return `${min} min ago`
  const h = Math.round(min / 60)
  if (h < 24) return `${h} h ago`
  return `${Math.round(h / 24)} d ago`
}

export const modeLabel: Record<TransportMode, string> = {
  ANY: 'Any mode',
  TRAIN: 'Train',
  BUS: 'Bus',
  SHARED_CAR: 'Shared ride',
  CAB: 'Cab',
}

export const sourceMeta: Record<DataSource, { label: string; short: string; tone: 'blue' | 'green' | 'amber' | 'violet'; help: string }> = {
  ROUTE_CALCULATION: {
    label: 'Route calculation',
    short: 'Calculated',
    tone: 'blue',
    help: 'Distance and driving time come from a real routing engine. The fare is an estimate from an assumed per-km rate.',
  },
  USER_TRIP: {
    label: 'Traveller-posted ride',
    short: 'Community',
    tone: 'green',
    help: 'Posted by another GharPahucho traveller. Seats and price are what they entered; nothing is guaranteed.',
  },
  SIMULATED_INVENTORY: {
    label: 'Sample data',
    short: 'Sample',
    tone: 'amber',
    help: 'Simulated train and bus inventory for demonstration. It is not real-time and not bookable.',
  },
  LIVE_PROVIDER: {
    label: 'Live provider',
    short: 'Live',
    tone: 'violet',
    help: 'Real-time inventory from an authorised provider.',
  },
}

export const languages = [
  { code: 'en', speech: 'en-IN', label: 'English' },
  { code: 'hi', speech: 'hi-IN', label: 'हिन्दी' },
  { code: 'te', speech: 'te-IN', label: 'తెలుగు' },
  { code: 'ta', speech: 'ta-IN', label: 'தமிழ்' },
  { code: 'kn', speech: 'kn-IN', label: 'ಕನ್ನಡ' },
  { code: 'ml', speech: 'ml-IN', label: 'മലയാളം' },
  { code: 'mr', speech: 'mr-IN', label: 'मराठी' },
  { code: 'bn', speech: 'bn-IN', label: 'বাংলা' },
]

export const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join('')
