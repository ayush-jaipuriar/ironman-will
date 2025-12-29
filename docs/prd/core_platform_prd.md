# Iron Will Core Platform PRD (Markdown)

Source: `Iron Will Core Platform PRD.pdf` (v1.1), converted to Markdown and updated with the latest decisions.

## 1) Executive Summary & Vision
- “Ruthless accountability” system where Spring Boot is the source of truth, FastAPI Agent is the unbiased judge, and Next.js is the client.
- MVP prioritizes auditability, timezone correctness, and synchronous REST (Frontend → Java → Python) for simplicity.

## 2) Architecture Overview
- Frontend: Next.js + Tailwind; hosted on Cloud Run (single-env prototype on GCP).
- Core Backend (The Body): Spring Boot 3.x (JDK 17/21), Spring Security 6, OAuth2 + email/password; Google Cloud Run.
- Agent Backend (The Brain): Python FastAPI (see agent PRD) invoked synchronously from Java.
- Database: Cloud SQL Postgres 15 (+ pgvector shared with Agent).
- Storage: GCS bucket (`iron-will-proofs`) with 60-day lifecycle deletion.
- Communication: REST; blocking call Java→Agent with 30s timeout. UI shows “Judgement in Progress”.
- Observability: Structured JSON logs, basic tracing hooks; rate limiting at API edge.

### Updated decisions
- Single environment first (all GCP); add dev/stage/prod later.
- Auth: support both email/password and Google Sign-In.
- Roles: `ROLE_USER`, `ROLE_ADMIN` (admin can manage users/contracts, view audits; refine later).
- Lockout: score <3 locks all goals for 24h; login still allows read-only status/countdown.
- Proof uploads: JPG/PNG <5MB via Spring Boot proxy (no direct-to-GCS yet).
- Notifications: in-app polling every 60s only for MVP.

## 3) Functional Modules
### Module A: Identity & Profile
- Google OAuth2 + email/password.
- Store timezone per user; frontend sends timezone on each login (`PUT /api/user/timezone`).
- Accountability score DECIMAL(4,2); HUD shows health bar (Green 7–10, Yellow 3–7, Red 0–3).

### Module B: Contract (Goal) Management
- Users sign “contracts” (not habits).
- Attributes: title, review_time (stored UTC, displayed local), frequency (DAILY; WEEKDAYS later), criteria_config (JSONB prompt for Agent), status ACTIVE/LOCKED/ARCHIVED, locked_until.
- State machine: ACTIVE → LOCKED (penalty box) → unlock after 24h; ARCHIVED.

### Module C: Audit Submission Loop
1) Frontend uploads screenshot (JPG/PNG <5MB) to Spring Boot.
2) Spring checks score >3 and goal ACTIVE; generates hashed filename `users/{uid}/{goal}/{date}_{hash}.jpg`; uploads to GCS.
3) Spring calls Agent with payload; waits up to 30s.
4) On PASS: save audit as VERIFIED, increment score. On FAIL: save REJECTED, decrement score.
5) Return structured result to frontend; show “Analyzing” state during wait.

### Module D: Scoring & Lockout
- Pass +0.5, Fail -0.2, Missed -1.0. Threshold check after any update: if score <3 → lock all ACTIVE goals, set locked_until = now +24h.
- Redemption: unlock after 24h; score remains low.

### Module E: Notifications (“Nag”)
- Scheduler every 15m finds users past review time with no audit; skips 3AM local-type windows if configured.
- Frontend polls `/api/notifications/unread` every 60s; shows badge + toast.

## 4) Data Model (Postgres)
- users: id (UUID), email, full_name, timezone, accountability_score DECIMAL(4,2), created_at, updated_at; indexes on timezone.
- goals: id, user_id FK, title, review_time (UTC), frequency_type (‘DAILY’), criteria_config JSONB, status, locked_until, timestamps; index on (user_id, status).
- audit_logs: id, goal_id FK, audit_date, proof_url, status (PENDING/VERIFIED/REJECTED/MISSED), agent_remarks, score_impact DECIMAL(4,2), submitted_at; unique (goal_id, audit_date); index on audit_date.
- notifications: id, user_id FK, message, is_read, created_at; index on (user_id, is_read).

## 5) Java → Agent API Contract (Unified)
- Endpoint: `POST /internal/agent/audit`
- Headers: `X-Internal-Secret`
- Request:
  - request_id (UUID), user_id, goal_id
  - goal_context: {title, description?, criteria: {metric, operator, target}}
  - proof_url (gs://…)
  - timezone, current_time_local
  - user_context_summary (optional)
- Response:
  - verdict: PASS | FAIL
  - remarks
  - extracted_metrics (e.g., primary_value, app_name, date_detected)
  - score_impact (float)
  - confidence? (optional), processing_time_ms
- Error handling: 500 → treat as technical difficulty (no penalty); 400 → reject immediately.

## 6) Implementation Tasks (MVP slice)
- Spring init (web, data-jpa, security, oauth2-client, gcp-storage, lombok).
- SecurityFilterChain: public /auth, secure /api/**; credential + Google login.
- Entities & services: User, Goal, AuditLog, Notification; GoalService, ScoreService, LockoutService.
- Timezone controller; GCS upload service with hashing.
- Agent client (WebClient/RestClient) with timeout; orchestrating audit controller `POST /api/goals/{id}/audit`.
- Scheduler `@Scheduled` for nag notifications.
- Observability: JSON logs, request IDs; rate limiting filter (e.g., Bucket4j).

## 7) Deployment (single env)
- Cloud Run for frontend, core, agent.
- Cloud SQL Postgres (pgvector enabled); GCS bucket `iron-will-proofs`.
- Service accounts: core-sa (Storage Admin, Cloud SQL Client, Secret Manager Accessor), agent-sa (Storage Object Viewer, Cloud SQL Client, Vertex AI User, Secret Manager).
- Secrets via Secret Manager; env vars injected to Cloud Run.


