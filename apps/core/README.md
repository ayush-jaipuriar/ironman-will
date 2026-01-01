# Iron Will Core (Spring Boot)

Purpose: System of record (users, goals, audits, notifications), proof upload, scoring/lockout, and Agent orchestration.

Stack
- Java 17
- Spring Boot 3.2.5 (Web, Security 6, OAuth2 Client, Data JPA, Validation)
- Gradle (Kotlin DSL) build (`build.gradle.kts`)
- Postgres (Cloud SQL), GCS for proofs
- JSON logging; optional rate limiting; request ID filter

Endpoints (MVP)
- Auth: `/auth/login` (email/password), `/auth/google/callback`, `/auth/me`
- User: `PUT /api/user/timezone`
- Goals: `GET/POST/PUT /api/goals`, status-filtered
- Audit: `POST /api/goals/{id}/audit` (multipart upload -> GCS -> Agent call)
- Notifications: `GET /api/notifications/unread`

Services
- StorageService: validates JPG/PNG <5MB, hashed filenames to GCS
- AgentClient: REST call with `X-Internal-Secret`, 30s timeout
- ScoreService/LockoutService: +0.5 / -0.2 / -1.0, lock <3 for 24h (read-only UI)
- Scheduler: nag every 15m to insert notifications (frontend polls 60s)

Run (local)
1) Copy `env.example` to `.env` and fill values.
2) Provide Postgres and GCS credentials (e.g., via Application Default Credentials).
3) `./gradlew bootRun` (or `./gradlew build`)

Deploy (single env first)
- Cloud Run; attach service account with Storage Admin, Cloud SQL Client, Secret Manager Accessor.
- Env vars from Secret Manager; connect to Cloud SQL Postgres (pgvector enabled).

Env vars (see `.env.example`)
- DB_URL, DB_USERNAME, DB_PASSWORD
- GCS_BUCKET, GCP_PROJECT, GCP_REGION
- AGENT_BASE_URL, AGENT_INTERNAL_SECRET
- JWT_SECRET (or session signing key)
- OAUTH_GOOGLE_CLIENT_ID / SECRET


