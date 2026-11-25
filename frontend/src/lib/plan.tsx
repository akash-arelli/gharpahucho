import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { PlanRequest, PlanResponse } from './types'

/**
 * The most recent plan, carried between the planner, results and comparison pages.
 * Held in sessionStorage so a refresh does not lose the journey being looked at.
 */
const KEY = 'gp_plan'

interface Stored {
  request: PlanRequest
  response: PlanResponse
  compare: string[]
  tripId?: string | null
}

interface PlanCtx {
  request: PlanRequest | null
  response: PlanResponse | null
  compare: string[]
  tripId: string | null
  setPlan: (request: PlanRequest, response: PlanResponse, tripId?: string | null) => void
  toggleCompare: (id: string) => void
  clearCompare: () => void
  clear: () => void
}

const Ctx = createContext<PlanCtx | null>(null)

function load(): Stored | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as Stored) : null
  } catch {
    return null
  }
}

export function PlanProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<Stored | null>(load)

  useEffect(() => {
    try {
      if (state) sessionStorage.setItem(KEY, JSON.stringify(state))
      else sessionStorage.removeItem(KEY)
    } catch {
      /* storage full or unavailable: the plan simply will not survive a refresh */
    }
  }, [state])

  const setPlan = useCallback((request: PlanRequest, response: PlanResponse, tripId?: string | null) => setState({ request, response, compare: [], tripId: tripId ?? null }), [])
  const toggleCompare = useCallback(
    (id: string) =>
      setState((s) => {
        if (!s) return s
        const has = s.compare.includes(id)
        const compare = has ? s.compare.filter((x) => x !== id) : [...s.compare, id].slice(-3)
        return { ...s, compare }
      }),
    [],
  )
  const clearCompare = useCallback(() => setState((s) => (s ? { ...s, compare: [] } : s)), [])
  const clear = useCallback(() => setState(null), [])

  const value = useMemo<PlanCtx>(
    () => ({ request: state?.request ?? null, response: state?.response ?? null, compare: state?.compare ?? [], tripId: state?.tripId ?? null, setPlan, toggleCompare, clearCompare, clear }),
    [state, setPlan, toggleCompare, clearCompare, clear],
  )
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function usePlan() {
  const c = useContext(Ctx)
  if (!c) throw new Error('usePlan must be used inside <PlanProvider>')
  return c
}
