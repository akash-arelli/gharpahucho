---
title: GharPahucho
emoji: 🚆
colorFrom: orange
colorTo: red
sdk: docker
app_port: 7860
pinned: false
---

# GharPahucho

Festival travel planner: when to book, ride alerts, family trip links, and a demo Plus pass.

Single Docker image: React (Vite) UI served by the Spring Boot API on one origin.

```
docker build -t gharpahucho:demo .
docker run -p 7860:7860 -e SARVAM_API_KEY=... -e GP_ALLOWED_ORIGINS=http://localhost:7860 gharpahucho:demo
```

Runs on the `demo` profile (in-memory H2, resets on restart). Secrets come from environment variables only:
`SARVAM_API_KEY`, `NUDGEBEE_EVENT_WEBHOOK_URL`, `FLAGSMITH_SERVER_KEY`, `GP_JWT_SECRET`, `GP_ALLOWED_ORIGINS`.
