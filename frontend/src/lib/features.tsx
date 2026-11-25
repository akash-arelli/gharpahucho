import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import flagsmith from 'flagsmith'
import { FlagsmithProvider, useFlags, useFlagsmithLoading } from 'flagsmith/react'
import { api } from './api'
import { useAuth } from './auth'
import { FLAG, type Features, type FlagKey } from './types'

/**
 * Feature flags in the UI.
 *
 * The backend evaluates every flag for the signed-in identity (traits, segments, rollouts) and also enforces them on
 * the server, so its answer is the single source of truth. When a Flagsmith client-side environment ID is configured,
 * the official Flagsmith JS SDK additionally connects with the same identity and listens for realtime updates: a
 * toggle in the Flagsmith dashboard reaches the browser within moments and triggers an immediate re-evaluation here.
 */

const ENV_ID = import.meta.env.VITE_FLAGSMITH_ENVIRONMENT_ID as string | undefined
const API_URL = import.meta.env.VITE_FLAGSMITH_API_URL as string | undefined
const ALL_KEYS = Object.values(FLAG) as FlagKey[]

export interface ClientSdkState {
  enabled: boolean
  loaded: boolean
  flags: Record<string, boolean>
  lastChange?: number
}

interface FeatureCtx {
  features: Features | undefined
  loading: boolean
  error: unknown
  isEnabled: (key: FlagKey) => boolean
  config: (key: string) => string | undefined
  refresh: () => void
  sdk: ClientSdkState
}

const Ctx = createContext<FeatureCtx | null>(null)

export function FeatureProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const qc = useQueryClient()
  const [sdk, setSdk] = useState<ClientSdkState>({ enabled: Boolean(ENV_ID), loaded: false, flags: {} })

  const query = useQuery({
    queryKey: ['features', user?.id ?? 'public'],
    queryFn: () => (user ? api.features() : api.publicFeatures()),
    refetchInterval: 8000,
    refetchOnWindowFocus: true,
    staleTime: 1500,
    retry: 1,
  })

  const refresh = useCallback(() => {
    void qc.invalidateQueries({ queryKey: ['features'] })
  }, [qc])

  const value = useMemo<FeatureCtx>(
    () => ({
      features: query.data,
      loading: query.isLoading,
      error: query.error,
      // Until the first evaluation arrives nothing is claimed to be enabled.
      isEnabled: (key) => query.data?.flags[key] ?? false,
      config: (key) => query.data?.config[key],
      refresh,
      sdk,
    }),
    [query.data, query.isLoading, query.error, refresh, sdk],
  )

  const identity = query.data?.identity ?? undefined
  const traits = useMemo(() => {
    const out: Record<string, string | number | boolean | null> = {}
    Object.entries(query.data?.traits ?? {}).forEach(([k, v]) => {
      if (v === null || ['string', 'number', 'boolean'].includes(typeof v)) out[k] = v as string | number | boolean | null
    })
    return out
  }, [query.data?.traits])

  const inner = <Ctx.Provider value={value}>{children}</Ctx.Provider>

  // Mount the browser SDK only once the identity is known; re-mount if the identity changes (sign in / out).
  if (!ENV_ID || !identity) return inner
  return (
    <FlagsmithProvider
      key={identity}
      flagsmith={flagsmith}
      options={{
        environmentID: ENV_ID,
        ...(API_URL ? { api: API_URL } : {}),
        identity,
        traits,
        realtime: true,
        onChange: () => refresh(),
      }}
    >
      <SdkProbe onState={setSdk} />
      {inner}
    </FlagsmithProvider>
  )
}

/** Reads the browser SDK's own evaluation so the demo screen can show it next to the backend's. */
function SdkProbe({ onState }: { onState: (s: ClientSdkState) => void }) {
  const flags = useFlags(ALL_KEYS as unknown as string[])
  const loadingState = useFlagsmithLoading()
  const loaded = Boolean(loadingState?.isFetching === false && !loadingState?.error)
  const snapshot = JSON.stringify(Object.fromEntries(ALL_KEYS.map((k) => [k, Boolean((flags as Record<string, { enabled?: boolean }>)[k]?.enabled)])))
  useEffect(() => {
    onState({ enabled: true, loaded, flags: JSON.parse(snapshot) as Record<string, boolean>, lastChange: Date.now() })
  }, [snapshot, loaded, onState])
  return null
}

export function useFeatures() {
  const ctx = useContext(Ctx)
  if (!ctx) throw new Error('useFeatures must be used inside <FeatureProvider>')
  return ctx
}

export function useFlag(key: FlagKey) {
  return useFeatures().isEnabled(key)
}
