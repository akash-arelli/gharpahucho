// Bootstraps a local Flagsmith: admin user, organisation, project, the app's feature flags/config values and a
// server-side key. Idempotent. Usage: node scripts/flagsmith-setup.mjs
const BASE = process.env.FLAGSMITH_URL ?? 'http://localhost:8000'
const EMAIL = process.env.FLAGSMITH_ADMIN_EMAIL ?? 'admin@gharpahucho.local'
const PASSWORD = process.env.FLAGSMITH_ADMIN_PASSWORD ?? 'Tr4vel!Home-2026-xQ'
const ENV_NAME = 'Development'

const FLAGS = {
  smart_trip_planner: true,
  ai_voice_trip_creation: true,
  sarvam_multilingual_assistant: true,
  travel_companion_matching: true,
  shared_ride_matching: true,
  alternative_route_recommendations: true,
  budget_optimization: true,
  travel_group_creation: true,
  experimental_matching_algorithm: false,
  ai_trip_explanations: true,
  // Paid capability: OFF for everyone by default, switched ON for the "Plus members" segment below.
  festival_booking_plan: false,
  unlimited_ride_alerts: false,
  family_link: false,
  // Everyone gets (a limited number of) ride alerts; this is the kill switch.
  ride_alerts: true,
  // Demo checkout: the Festival Pass page upgrades the account without taking payment. Turn OFF before a real launch.
  demo_checkout: true,
}
// Flags above that are paid: OFF by default, ON for the "Plus members" segment (trait plan = plus).
const PLUS_FEATURES = ['festival_booking_plan', 'unlimited_ride_alerts', 'family_link']
const CONFIGS = {
  matching_radius_km: '25',
  date_flexibility_days: '2',
  max_budget_deviation_pct: '20',
  min_compatibility_score: '40',
  max_recommendations: '12',
  matching_weights: '{"destination":35,"date":25,"start":20,"budget":10,"transport":10}',
  rail_booking_window_days: '60',
  free_radar_trips: '1',
  free_ride_alerts: '1',
  festival_pass_price_inr: '299',
}

async function call(method, path, body, token) {
  const res = await fetch(`${BASE}/api/v1${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Token ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let data
  try { data = text ? JSON.parse(text) : undefined } catch { data = text }
  return { ok: res.ok, status: res.status, data }
}
const must = (r, what) => {
  if (!r.ok) throw new Error(`${what} failed: ${r.status} ${JSON.stringify(r.data)}`)
  return r.data
}

// 1. user: log in, or register on first run
let login = await call('POST', '/auth/login/', { email: EMAIL, password: PASSWORD })
if (!login.ok) {
  must(await call('POST', '/auth/users/', { email: EMAIL, password: PASSWORD, re_password: PASSWORD, first_name: 'Admin', last_name: 'User' }), 'register')
  login = await call('POST', '/auth/login/', { email: EMAIL, password: PASSWORD })
}
const token = must(login, 'login').key

// 2. organisation
let orgs = must(await call('GET', '/organisations/', null, token), 'list orgs').results
let org = orgs.find((o) => o.name === 'GharPahucho')
if (!org) org = must(await call('POST', '/organisations/', { name: 'GharPahucho' }, token), 'create org')

// 3. project (creates Development + Production environments)
let projects = must(await call('GET', `/projects/?organisation=${org.id}`, null, token), 'list projects')
projects = Array.isArray(projects) ? projects : projects.results
let project = projects.find((p) => p.name === 'gharpahucho')
if (!project) project = must(await call('POST', '/projects/', { name: 'gharpahucho', organisation: org.id }, token), 'create project')

// 4. features
const existing = must(await call('GET', `/projects/${project.id}/features/?page_size=200`, null, token), 'list features').results
const have = new Set(existing.map((f) => f.name))
for (const [name, enabled] of Object.entries(FLAGS)) {
  if (!have.has(name)) must(await call('POST', `/projects/${project.id}/features/`, { name, default_enabled: enabled, type: 'STANDARD' }, token), `feature ${name}`)
}
for (const [name, value] of Object.entries(CONFIGS)) {
  if (!have.has(name)) must(await call('POST', `/projects/${project.id}/features/`, { name, default_enabled: true, initial_value: value, type: 'STANDARD' }, token), `config ${name}`)
}

// 4b. "Plus members" segment (trait plan = plus) and the override that turns the paid flag on for it
async function ensurePlusEntitlement(project, env) {
  const segs = must(await call('GET', `/projects/${project.id}/segments/?page_size=100`, null, token), 'list segments')
  let seg = (segs.results ?? segs).find((s) => s.name === 'Plus members')
  if (!seg) {
    seg = must(await call('POST', `/projects/${project.id}/segments/`, {
      name: 'Plus members',
      project: project.id,
      rules: [{ type: 'ALL', conditions: [], rules: [{ type: 'ANY', conditions: [{ operator: 'EQUAL', property: 'plan', value: 'plus' }], rules: [] }] }],
    }, token), 'create segment')
  }
  const feats = must(await call('GET', `/projects/${project.id}/features/?page_size=200`, null, token), 'list features').results
  for (const name of PLUS_FEATURES) {
    const feature = feats.find((f) => f.name === name)
    let fs = must(await call('GET', `/features/feature-segments/?environment=${env.id}&feature=${feature.id}`, null, token), 'list feature segments')
    fs = (fs.results ?? fs).find((x) => x.segment === seg.id)
    if (!fs) fs = must(await call('POST', '/features/feature-segments/', { feature: feature.id, segment: seg.id, environment: env.id, priority: 0 }, token), 'create feature segment')
    let states = must(await call('GET', `/features/featurestates/?environment=${env.id}&feature=${feature.id}&feature_segment=${fs.id}`, null, token), 'list segment states')
    states = states.results ?? states
    const state = states.find((s) => s.feature_segment === fs.id)
    if (state?.enabled) continue
    if (state) must(await call('PATCH', `/features/featurestates/${state.id}/`, { enabled: true }, token), `enable ${name} for segment`)
    else must(await call('POST', '/features/featurestates/', { feature: feature.id, environment: env.id, feature_segment: fs.id, enabled: true }, token), `create segment state for ${name}`)
  }
}

// 5. server-side key for the Development environment
const envs = must(await call('GET', `/environments/?project=${project.id}`, null, token), 'list envs')
const envList = Array.isArray(envs) ? envs : envs.results
let env = envList.find((e) => e.name === ENV_NAME)
if (!env) env = must(await call('POST', '/environments/', { name: ENV_NAME, project: project.id }, token), 'create environment')
await ensurePlusEntitlement(project, env)
const keys = must(await call('GET', `/environments/${env.api_key}/api-keys/`, null, token), 'list keys')
let serverKey = (Array.isArray(keys) ? keys : keys.results ?? []).find((k) => k.name === 'gharpahucho-backend' && k.active)?.key
if (!serverKey) serverKey = must(await call('POST', `/environments/${env.api_key}/api-keys/`, { name: 'gharpahucho-backend' }, token), 'create key').key

console.log(JSON.stringify({ dashboard: BASE, adminEmail: EMAIL, adminPassword: PASSWORD, serverKey, apiUrl: `${BASE}/api/v1/` }, null, 2))
