# GharPahucho

Festival travel planner for India: **when to book**, ride alerts, family trip links, and a demo Plus pass.

**Live demo:** https://gharpahucho.onrender.com (click "Try the demo", no sign-up. Free tier: the first load after idle takes about a minute.)

## What it does
- **When-to-book plan** for festival routes, with links out to IRCTC and redBus (no personal data sent).
- **Smart trip planner**: train, bus, flight and cab options with alternative routes.
- **Companions and shared rides**: match people travelling the same way, same day.
- **Ride alerts**: get notified when a ride is posted on your route.
- **Family link**: an unguessable, expiring link so family can follow a trip (English, Hindi, Telugu), first name only.
- **Voice and multilingual assistant** (Sarvam): plan a trip by speaking or typing.
- **Festival Plus pass** (demo checkout, no real payment).

## Sponsor tools
- **Flagsmith**: every paid feature is a flag, enforced on the server (`@RequiresFeature` returns 403 `FEATURE_DISABLED`). A "Plus members" segment (trait `plan=plus`) turns them on; remote config sets the free alert limit and pass price. If Flagsmith is unreachable the app falls back to safe defaults.
- **NudgeBee**: the backend sends firing/resolved events to a NudgeBee Event Webhook, tagged with service, namespace and environment. `POST /api/ops/nudgebee/test?resolve=false|true` sends a test event.
- **Sarvam**: chat completions for the assistant and localised text, speech-to-text for voice trips, with a guard that rejects rewrites that invent place names.

## Run it (Docker, one command)
Needs Docker. This builds the React UI and the Spring Boot API into a single image served on one port.

```bash
docker build -t gharpahucho:demo .
docker run -p 7860:7860 \
  -e SARVAM_API_KEY=your_key \
  -e GP_ALLOWED_ORIGINS=http://localhost:7860 \
  gharpahucho:demo
```

Open http://localhost:7860 and click **Continue with the demo account**. Without `SARVAM_API_KEY` everything works except the Sarvam-powered assistant and voice.

The `demo` profile uses an in-memory H2 database seeded with demo data (it resets on restart).

## Run it for development
Needs Java 21, Maven and Node 20+.

```bash
# backend (http://localhost:8080), local profile = in-memory H2 + demo data
cd backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run

# frontend (http://localhost:5173, proxies /api to 8080)
cd frontend
npm install
npm run dev
```

Tests: `cd backend && mvn test`

### Optional: Flagsmith locally
```bash
docker compose -f docker-compose.flagsmith.yml up -d   # http://localhost:8000
node scripts/flagsmith-setup.mjs                       # creates flags, configs and the Plus segment
```
Then start the backend with `FLAGSMITH_SERVER_KEY=<server key> FLAGSMITH_API_URL=http://localhost:8000/api/v1/`.

## Configuration
Secrets are read from environment variables only and are never committed.

| Variable | Purpose |
|---|---|
| `SARVAM_API_KEY` | Sarvam chat and speech |
| `NUDGEBEE_EVENT_WEBHOOK_URL` | NudgeBee Event Webhook (contains the token, keep secret) |
| `FLAGSMITH_SERVER_KEY`, `FLAGSMITH_API_URL` | Flagsmith server-side key and optional self-hosted URL |
| `GP_JWT_SECRET` | JWT signing secret (32+ chars) |
| `GP_ALLOWED_ORIGINS` | Allowed CORS origins |
| `GP_DEMO_ONLY` | `true` disables e-mail/password sign-in; only the demo account works |
| `GP_ENVIRONMENT` | Label sent to NudgeBee (`demo`, `local`, ...) |

## Stack
Spring Boot 3.5 (Java 21), Spring Security + JWT, Flyway, Micrometer; React, Vite, TanStack Query, Tailwind. Deployed as one Docker image on Render (`render.yaml`).

---
<sub>Hugging Face Space metadata below is kept so the repo can also run as a Docker Space.</sub>

<!--
title: GharPahucho
sdk: docker
app_port: 7860
-->
