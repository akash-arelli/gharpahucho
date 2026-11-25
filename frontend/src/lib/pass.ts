import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, ApiError } from './api'
import { useAuth } from './auth'
import type { Me } from './types'

export const PASS_PATH = '/app/pass'

/** True when the signed-in traveller is on the Festival Pass (Plus). The server decides; this only mirrors it. */
export function useIsPlus() {
  return useAuth().user?.plan === 'plus'
}

export function usePlans() {
  return useQuery({ queryKey: ['plans'], queryFn: api.plans, staleTime: 30_000 })
}

/** Demo checkout and cancel. Both refresh the user and every plan-dependent query so locked cards open at once. */
export function usePlanChange() {
  const { setUser } = useAuth()
  const qc = useQueryClient()
  const apply = (me: Me) => {
    setUser(me)
    void qc.invalidateQueries()
  }
  const onError = (e: unknown) => toast.error(e instanceof ApiError ? e.message : 'Could not change your plan.')
  const upgrade = useMutation({ mutationFn: api.demoCheckout, onSuccess: apply, onError })
  const downgrade = useMutation({ mutationFn: api.demoCancel, onSuccess: apply, onError })
  return { upgrade, downgrade }
}
