import { useState } from 'react'
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Sparkles } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Logo } from '@/components/Logo'
import { useAuth } from '@/lib/auth'
import { ApiError } from '@/lib/api'
import type { Me } from '@/lib/types'

/** Demo build: there is no sign-in or sign-up, just the one-click demo account. */
export default function AuthPage() {
  const { user, demoLogin } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const [busy, setBusy] = useState(false)

  const next = params.get('next') ?? (location.state as { from?: string } | null)?.from ?? '/app'

  if (user) return <Navigate to={user.onboarded ? next : '/onboarding'} replace />

  const after = (me: Me) => navigate(me.onboarded ? next : `/onboarding?next=${encodeURIComponent(next)}`, { replace: true })

  const demo = async () => {
    setBusy(true)
    try {
      after(await demoLogin())
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Demo login is unavailable.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="bg-hero flex min-h-dvh flex-col">
      <header className="mx-auto flex h-18 w-full max-w-6xl items-center px-4 sm:px-6">
        <Logo />
      </header>
      <div className="flex flex-1 items-center justify-center px-4 pb-16">
        <Card className="w-full max-w-md p-6 text-center shadow-pop sm:p-8">
          <h1 className="text-2xl font-extrabold text-navy-900">Try GharPahucho</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            This is a demo. No sign-up needed: jump in as Priya, with trips, festival plans and ride alerts already set up.
          </p>
          <Button type="button" variant="brand" size="lg" className="mt-6 w-full" onClick={demo} loading={busy}>
            {!busy && <Sparkles />} Continue with the demo account
          </Button>
          <p className="mt-4 text-xs text-muted-foreground">Demo data is shared and resets from time to time. Please don’t enter personal details.</p>
        </Card>
      </div>
    </div>
  )
}
