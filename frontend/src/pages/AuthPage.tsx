import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Sparkles } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Field } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Logo } from '@/components/Logo'
import { CityInput } from '@/components/CityInput'
import { useAuth } from '@/lib/auth'
import { ApiError } from '@/lib/api'
import type { Me } from '@/lib/types'

function fieldErrors(e: unknown): Record<string, string> {
  if (e instanceof ApiError && e.details && typeof e.details.fields === 'object' && e.details.fields) return e.details.fields as Record<string, string>
  return {}
}

export default function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { user, login, register, demoLogin } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const [busy, setBusy] = useState<'form' | 'demo' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [form, setForm] = useState({ displayName: '', email: '', password: '', homeCity: '' })
  const isRegister = mode === 'register'

  const next = params.get('next') ?? (location.state as { from?: string } | null)?.from ?? '/app'
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [k]: e.target.value }))

  if (user) return <Navigate to={user.onboarded ? next : '/onboarding'} replace />

  const after = (me: Me) => navigate(me.onboarded ? next : `/onboarding?next=${encodeURIComponent(next)}`, { replace: true })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy('form')
    setError(null)
    setErrors({})
    try {
      const me = isRegister
        ? await register({ email: form.email.trim(), password: form.password, displayName: form.displayName.trim(), homeCity: form.homeCity.trim() || undefined })
        : await login(form.email.trim(), form.password)
      after(me)
    } catch (err) {
      setErrors(fieldErrors(err))
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setBusy(null)
    }
  }

  const demo = async () => {
    setBusy('demo')
    setError(null)
    try {
      after(await demoLogin())
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Demo login is unavailable.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="bg-hero flex min-h-dvh flex-col">
      <header className="mx-auto flex h-18 w-full max-w-6xl items-center px-4 sm:px-6">
        <Logo />
      </header>
      <div className="flex flex-1 items-center justify-center px-4 pb-16">
        <Card className="w-full max-w-md p-6 shadow-pop sm:p-8">
          <h1 className="text-2xl font-extrabold text-navy-900">{isRegister ? 'Create your account' : 'Welcome back'}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{isRegister ? 'Plan journeys and find people travelling your way.' : 'Sign in to continue planning your journey.'}</p>

          <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
            {isRegister && (
              <Field label="Your name" htmlFor="name" error={errors.displayName}>
                <Input id="name" autoComplete="name" required value={form.displayName} onChange={set('displayName')} placeholder="Priya Sharma" aria-invalid={Boolean(errors.displayName)} />
              </Field>
            )}
            <Field label="Email" htmlFor="email" error={errors.email}>
              <Input id="email" type="email" autoComplete="email" required value={form.email} onChange={set('email')} placeholder="you@example.com" aria-invalid={Boolean(errors.email)} />
            </Field>
            <Field label="Password" htmlFor="password" error={errors.password} hint={isRegister ? 'At least 8 characters.' : undefined}>
              <Input
                id="password"
                type="password"
                autoComplete={isRegister ? 'new-password' : 'current-password'}
                required
                value={form.password}
                onChange={set('password')}
                aria-invalid={Boolean(errors.password)}
              />
            </Field>
            {isRegister && (
              <Field label="Home city (optional)" hint="Where you usually travel to for festivals.">
                <CityInput value={form.homeCity} onChange={(v) => setForm((f) => ({ ...f, homeCity: v }))} placeholder="Hyderabad" />
              </Field>
            )}
            {error && (
              <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm font-medium text-destructive">
                {error}
              </p>
            )}
            <Button type="submit" variant="brand" className="w-full" loading={busy === 'form'}>
              {isRegister ? 'Create account' : 'Sign in'}
            </Button>
          </form>

          <div className="my-5 flex items-center gap-3 text-xs font-semibold text-muted-foreground">
            <span className="h-px flex-1 bg-border" /> or <span className="h-px flex-1 bg-border" />
          </div>
          <Button type="button" variant="outline" className="w-full" onClick={demo} loading={busy === 'demo'}>
            {busy !== 'demo' && <Sparkles />} Continue with the demo account
          </Button>

          <p className="mt-6 text-center text-sm text-muted-foreground">
            {isRegister ? 'Already have an account?' : 'New to GharPahucho?'}{' '}
            <Link className="font-semibold text-brand-600 hover:underline" to={isRegister ? '/login' : '/register'}>
              {isRegister ? 'Sign in' : 'Create an account'}
            </Link>
          </p>
        </Card>
      </div>
    </div>
  )
}
