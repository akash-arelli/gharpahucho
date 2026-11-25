import type { TransportMode } from './types'

/**
 * Links out to the real booking sites. GharPahucho does not sell tickets, and train and bus results here are sample data,
 * so these send the traveller to the source of truth for live fares and seats.
 *
 * IRCTC offers no public way to prefill a journey, so that link opens its search page. redBus has a stable route page per
 * city pair. Nothing about the traveller is sent to either site.
 */

export interface BookingLink {
  href: string
  label: string
  /** What the traveller should expect after clicking. */
  hint: string
}

/** How redBus spells cities in its route URLs, where that differs from the names we show. */
const REDBUS_SLUG: Record<string, string> = {
  bengaluru: 'bangalore',
  mysuru: 'mysore',
  mangaluru: 'mangalore',
  kochi: 'cochin',
  thiruvananthapuram: 'trivandrum',
  kozhikode: 'calicut',
  visakhapatnam: 'vizag',
  vijayawada: 'vijayawada',
  prayagraj: 'allahabad',
  varanasi: 'varanasi',
  gurugram: 'gurgaon',
  pune: 'pune',
  mumbai: 'mumbai',
  chennai: 'chennai',
  hyderabad: 'hyderabad',
  kolkata: 'kolkata',
  'new delhi': 'delhi',
}

const slug = (city: string) => {
  const key = city.trim().toLowerCase()
  return (REDBUS_SLUG[key] ?? key).replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
}

export function bookingLink(mode: TransportMode, from?: string, to?: string): BookingLink | null {
  if (mode === 'TRAIN') {
    return {
      href: 'https://www.irctc.co.in/nget/train-search',
      label: 'Check on IRCTC',
      hint: 'Opens IRCTC in a new tab. Enter your route and date there to see live trains and seats. We don’t book tickets.',
    }
  }
  if (mode === 'BUS' && from && to) {
    return {
      href: `https://www.redbus.in/bus-tickets/${slug(from)}-to-${slug(to)}`,
      label: 'Check on redBus',
      hint: 'Opens redBus in a new tab with buses for this route. Pick your date there. We don’t book tickets.',
    }
  }
  return null
}
