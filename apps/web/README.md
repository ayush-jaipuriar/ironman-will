# Iron Will Web (Next.js 14)

Purpose: HUD-style client implementing the stitched designs: login/gateway, dashboard with score bar + avatar, contract config, auditor flow, and lockout screen.

Stack
- Next.js 14 (App Router), TypeScript
- Tailwind CSS + clsx/tailwind-merge for theming
- Zustand for auth/score/lockout state
- TanStack Query for server data + polling (notifications 60s)
- Framer Motion for heavy motion; React Three Fiber optional for avatar

Routes (MVP)
- `/login` (credentials + Google button; calls Spring auth)
- `/dashboard` (protected): goals list, score bar, upload CTA, notifications badge
- `/contract` : create goal/contract (time dial, criteria blocks)
- `/lockout` : read-only countdown/status when score <3

Components
- UI: BioButton, GlassCard, NeonBadge, inputs with focus rings
- Features: ScoreBar, GoalCard, AuditScanner (upload/analyze states), TimeDial
- Canvas: AgentSphere (optional)

Data flows
- Auth: credential/Google via Spring; use httpOnly cookie/JWT session
- Goals: list/create/update via core API; review time displayed local
- Audit upload: multipart to core; show analyzing skeleton while Agent verdict pending
- Notifications: poll unread every 60s; badge + toast
- Lockout: redirect to `/lockout` when score <3; allow read-only view

Run (local)
1) Copy `env.local.example` to `.env.local` and fill API base URL and public vars.
2) `npm install` (or `pnpm i`), then `npm run dev`.

Deploy (single env first)
- Containerized Next.js on Cloud Run; set `NEXT_PUBLIC_API_BASE_URL` to core URL.

Env vars (see `env.local.example`)
- NEXT_PUBLIC_API_BASE_URL=https://core.example.com
- NEXT_PUBLIC_POLL_INTERVAL_MS=60000


