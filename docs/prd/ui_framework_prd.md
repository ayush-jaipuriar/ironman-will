# Iron Will UI Framework PRD (Markdown)

Source: `Iron Will UI Framework PRD.pdf` (v1.1), converted to Markdown and updated with current decisions.

## 1) Design System & Visual Language
- Command-interface aesthetic: utilitarian, high-stakes; deep charcoal backgrounds, neon green energy, crimson failure accents.
- Typography: Sans (Inter/Geist) for UI; Mono (JetBrains/Geist) for metrics; inactive labels in ghost gray.
- Effects: global scanlines overlay; glassmorphism HUD (backdrop-blur, glass cards); glow on active elements; glitch accents for errors.
- Color semantics: bg-void #050505; bg-card #0A0A0A; neon-green for active; crimson for lockout/glitch.

### Updated decisions
- Single-env on GCP; frontend on Cloud Run.
- Auth: email/password + Google Sign-In; roles USER/ADMIN.
- Lockout: show read-only countdown/status (no actions).
- Proof upload: routed via Spring Boot; enforce JPG/PNG <5MB.
- Polling: notifications every 60s.

## 2) Page Specifications
### Gateway (Login)
- Centered stack, hex/mesh background, glitch “IRON WILL”; pill Google sign-in; status footer “SYSTEM STATUS: ONLINE_”.
- Supports credential login and Google button (calls Spring auth endpoints).

### Dashboard (HUD)
- Header (logo, settings); dominant score bar (liquid neon); avatar sphere that reacts to score state.
- Contract cards list with status badges (PENDING/DONE/FAILED).
- Action opens audit drawer/modal (upload flow).

### Contract (Create Habit)
- Mission-style config panel.
- Inputs: protocol name, time dial wheel (safe-dial style), criteria rendered as code blocks (metric/operator/target).
- Button: “INITIATE CONTRACT” (sharp rectangle, neon).

### Auditor (Upload & Verify)
- State 1: Dropzone with grid, “DROP EVIDENCE HERE.”
- State 2: Analyzing: laser scan over image, typewriter status text (“EXTRACTING…”, “VERIFYING…”, “COMPARING…”).
- State 3: Verdict: PASS → green APPROVED stamp; FAIL → red glitch REJECTED and avatar turns jagged/red.

### Lockout (Game Over)
- Full-screen crimson glitch overlay; shake animation; countdown timer; “PROTOCOL FAILED”.
- All controls disabled (grayscale, not-allowed cursor); read-only view allowed.

## 3) Frontend Architecture (Next.js 14, App Router)
- Styling: Tailwind + clsx + tailwind-merge; custom theme per palette above.
- State: Zustand for auth/score/lockout; TanStack Query for server data + polling (notifications) and uploads.
- Animation: Framer Motion for heavy/mechanical motion; optional reduced-motion toggle if easy.
- 3D/Canvas: React Three Fiber (Agent sphere) or Canvas.
- Icons: Lucide React.

### Suggested structure
```
/app/login        # Gateway
/app/dashboard    # HUD (protected)
/app/contract     # Contract creation
/app/lockout      # Lockout screen
/components/ui    # BioButton, GlassCard, NeonBadge, inputs
/components/features # AuditScanner, ScoreBar, TimeDial, GoalCard
/components/canvas   # AgentSphere
/lib/store        # Zustand stores (auth, game)
/hooks            # useInterval, useLockout, useUpload
/lib/utils        # formatters, class merge
/styles/globals.css # scanlines, glitch keyframes, fonts
```

## 4) API Integration (with Core)
- Auth: credential login + Google OAuth via Spring endpoints; store session via httpOnly cookie/JWT.
- Goals: list/create/update; review time shown in local timezone, stored UTC.
- Audit upload: multipart to Spring; show analyzing skeleton while Java→Agent call runs.
- Notifications: poll unread every 60s; badge + toast.
- Lockout: if score <3, redirect to /lockout with countdown and read-only HUD.

## 5) Accessibility & UX Notes
- Keyboard focusable buttons/inputs; visible focus ring.
- Avoid essential info conveyed by color alone; text labels on statuses.
- Provide reduced motion toggle if time permits; keep animations performant.


