import { lazy, Suspense } from 'react'
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import { TooltipProvider } from '@/components/ui/tooltip'
import { FullPageSpinner } from '@/components/common'
import { RequireAuth } from '@/components/AppShell'
import { Button } from '@/components/ui/button'
import { AuthProvider } from '@/lib/auth'
import { FeatureProvider } from '@/lib/features'
import { PlanProvider } from '@/lib/plan'
import { ApiError } from '@/lib/api'
import Landing from '@/pages/Landing'
import AuthPage from '@/pages/AuthPage'

// The map (Leaflet) is only needed after planning, so it stays out of the landing bundle.
const Onboarding = lazy(() => import('@/pages/Onboarding'))
const Dashboard = lazy(() => import('@/pages/Dashboard'))
const Planner = lazy(() => import('@/pages/Planner'))
const Results = lazy(() => import('@/pages/Results'))
const Compare = lazy(() => import('@/pages/Compare'))
const Travellers = lazy(() => import('@/pages/Travellers'))
const TravellerProfile = lazy(() => import('@/pages/TravellerProfile'))
const Groups = lazy(() => import('@/pages/Groups'))
const GroupDetail = lazy(() => import('@/pages/GroupDetail'))
const Assistant = lazy(() => import('@/pages/Assistant'))
const Profile = lazy(() => import('@/pages/Profile'))
const Features = lazy(() => import('@/pages/Features'))
const Pass = lazy(() => import('@/pages/Pass'))
const FamilyPublic = lazy(() => import('@/pages/FamilyPublic'))

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: false,
      retry: (count, err) => !(err instanceof ApiError && err.status >= 400 && err.status < 500) && count < 2,
    },
  },
})

function NotFound() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3 px-4 text-center">
      <p className="font-display text-6xl font-extrabold text-brand-500">404</p>
      <h1 className="text-2xl font-bold text-navy-900">This road doesn’t lead anywhere</h1>
      <p className="text-muted-foreground">The page you’re looking for doesn’t exist.</p>
      <Button asChild variant="brand" className="mt-2">
        <Link to="/">Back to GharPahucho</Link>
      </Button>
    </div>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <FeatureProvider>
            <PlanProvider>
              <TooltipProvider delayDuration={150}>
                <Suspense fallback={<FullPageSpinner />}>
                  <Routes>
                    <Route path="/" element={<Landing />} />
                    <Route path="/login" element={<AuthPage mode="login" />} />
                    <Route path="/register" element={<AuthPage mode="register" />} />
                    <Route path="/onboarding" element={<Onboarding />} />
                    <Route path="/f/:token" element={<FamilyPublic />} />

                    <Route path="/app" element={<RequireAuth />}>
                      <Route index element={<Dashboard />} />
                      <Route path="plan" element={<Planner />} />
                      <Route path="results" element={<Results />} />
                      <Route path="compare" element={<Compare />} />
                      <Route path="travellers" element={<Travellers />} />
                      <Route path="travellers/:userId" element={<TravellerProfile />} />
                      <Route path="groups" element={<Groups />} />
                      <Route path="groups/:id" element={<GroupDetail />} />
                      <Route path="assistant" element={<Assistant />} />
                      <Route path="profile" element={<Profile />} />
                      <Route path="features" element={<Features />} />
                      <Route path="pass" element={<Pass />} />
                    </Route>

                    <Route path="*" element={<NotFound />} />
                  </Routes>
                </Suspense>
                <Toaster position="top-center" richColors closeButton toastOptions={{ className: 'font-sans' }} />
              </TooltipProvider>
            </PlanProvider>
          </FeatureProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
