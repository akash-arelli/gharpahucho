import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { FlaskConical, Flag, Home, LogOut, MessagesSquare, Route, Ticket, UserRound, Users, UsersRound, type LucideIcon } from 'lucide-react'
import { Avatar } from '@/components/ui/avatar'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { FullPageSpinner } from '@/components/common'
import { Logo } from '@/components/Logo'
import { PassPill } from '@/components/PassCta'
import { useAuth } from '@/lib/auth'
import { useFeatures } from '@/lib/features'
import { FLAG, type FlagKey } from '@/lib/types'
import { cn } from '@/lib/utils'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  flag?: FlagKey
  end?: boolean
}

const NAV: NavItem[] = [
  { to: '/app', label: 'Home', icon: Home, end: true },
  { to: '/app/plan', label: 'Plan', icon: Route, flag: FLAG.planner },
  { to: '/app/travellers', label: 'Travellers', icon: Users, flag: FLAG.companions },
  { to: '/app/groups', label: 'Groups', icon: UsersRound },
  { to: '/app/assistant', label: 'Assistant', icon: MessagesSquare, flag: FLAG.assistant },
]

export function RequireAuth() {
  const { user, loading } = useAuth()
  const loc = useLocation()
  if (loading) return <FullPageSpinner />
  if (!user) return <Navigate to="/login" replace state={{ from: loc.pathname + loc.search }} />
  if (!user.onboarded && loc.pathname !== '/onboarding') return <Navigate to="/onboarding" replace />
  return <AppShell />
}

function AppShell() {
  const { user, logout } = useAuth()
  const { isEnabled, features } = useFeatures()
  const navigate = useNavigate()
  if (!user) return null
  const items = NAV.filter((n) => !n.flag || isEnabled(n.flag))
  const demo = user.demoAccount

  return (
    <div className="flex min-h-dvh flex-col bg-muted/50">
      <header className="sticky top-0 z-40 border-b border-border bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-4 sm:px-6">
          <Logo to="/app" />
          <nav className="hidden items-center gap-1 md:flex" aria-label="Main">
            {items.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                end={n.end}
                className={({ isActive }) =>
                  cn('rounded-lg px-3.5 py-2 text-sm font-semibold transition-colors', isActive ? 'bg-navy-900 text-white' : 'text-navy-600 hover:bg-navy-50 hover:text-navy-900')
                }
              >
                {n.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <PassPill />
            {demo && (
              <span className="hidden items-center gap-1 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-bold text-amber-800 sm:inline-flex">
                <FlaskConical className="size-3" /> Demo account
              </span>
            )}
            <DropdownMenu>
              <DropdownMenuTrigger className="flex items-center gap-2 rounded-full p-1 pr-2 transition-colors hover:bg-navy-50" aria-label="Account menu">
                <Avatar name={user.displayName} className="size-9" />
                <span className="hidden max-w-32 truncate text-sm font-semibold text-navy-800 sm:block">{user.displayName.split(' ')[0]}</span>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuLabel>{user.email}</DropdownMenuLabel>
                <DropdownMenuItem onSelect={() => navigate('/app/profile')}>
                  <UserRound /> Profile &amp; preferences
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => navigate('/app/pass')}>
                  <Ticket /> Festival Pass
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => navigate('/app/features')}>
                  <Flag /> Feature flags &amp; ops
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onSelect={() => {
                    logout()
                    navigate('/')
                  }}
                >
                  <LogOut /> Sign out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 pb-28 pt-6 sm:px-6 md:pb-12 md:pt-8">
        {features && !features.flagsmith.configured && (
          <p className="mb-4 hidden text-xs text-muted-foreground lg:block">
            Feature flags are using safe defaults (Flagsmith not connected). <Link className="font-semibold text-brand-600 hover:underline" to="/app/features">Details</Link>
          </p>
        )}
        <Outlet />
      </main>

      {/* mobile bottom navigation */}
      <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden" aria-label="Main">
        <ul className="mx-auto flex max-w-md items-stretch justify-around">
          {items.map((n) => (
            <li key={n.to} className="flex-1">
              <NavLink
                to={n.to}
                end={n.end}
                className={({ isActive }) => cn('flex flex-col items-center gap-0.5 py-2.5 text-[11px] font-semibold transition-colors', isActive ? 'text-brand-600' : 'text-navy-500')}
              >
                <n.icon className="size-5" />
                {n.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  )
}
