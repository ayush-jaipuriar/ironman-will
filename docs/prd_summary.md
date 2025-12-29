# Iron Will PRD Synthesis

Sources: `Iron Will Core Platform PRD.pdf`, `Iron Will UI Framework PRD.pdf`, `Agent PRD Document Iron Will.pdf` (extracted via `docs/prd_text/*.txt`).

## Core Platform (Spring Boot backend + FastAPI agent)
- Vision: “Ruthless accountability” system; Java Spring Boot as system-of-record, FastAPI agent as unbiased judge; synchronous REST pipeline for MVP (frontend → Java → Python).
- Stack/infra: Next.js + Tailwind frontend; Spring Boot 3.x (OAuth2 Google, Security 6), Postgres 15 (Cloud SQL), GCS for proofs (lifecycle delete >60 days), Cloud Run for services.
- Key modules: Identity (Google OAuth2 only, timezone pushed from client every login), Contract/Goal as “signed contract” with JSONB criteria and state machine (ACTIVE/LOCKED/ARCHIVED), Audit Submission loop (upload → GCS → agent call → verdict), Scoring (+0.5 pass, -0.2 fail, -1.0 missed; lockout if score <3 for 24h), Notifications (“Nag” scheduler every 15m; frontend polls unread).
- Data model: Tables for users (timezone, accountability_score), goals (review_time UTC, criteria_config JSONB, locked_until), audit_logs (one per goal per day, status PENDING/VERIFIED/REJECTED/MISSED, score_impact), notifications (is_read).
- Agent contract: POST `/internal/agent/audit` with user/goal context, proof_url, timezone; expects PASS/FAIL, remarks, extracted_data, score_impact; Java treats 500 as retry-safe, 400 as immediate reject.
- Roadmap highlights: Spring project init, OAuth/timezone controller, goal/score/lockout services, GCS upload + hashed filenames, orchestration controller for audit, scheduler for nagging.

## UI Framework (Next.js App Router)
- Design language: Command-interface vibe (utilitarian, intimidating); deep charcoal backgrounds, neon green energy, crimson failure; monospace for metrics; global scanlines, glassmorphism HUD, glow on interaction.
- Page specs: 
  - Gateway (Login): centered stack, hex mesh, glitch “IRON WILL”, pill Google sign-in, status footer.
  - Dashboard (HUD): header + dominant score bar + avatar sphere reacting to score; contract cards with statuses; drawer/modal for audit upload.
  - Contract (Create Habit): mission-style config panel; time dial wheel; criteria rendered as code blocks; sharp “INITIATE CONTRACT” button.
  - Auditor (Upload & Verify): dropzone → analyzing laser scan with typewriter status → verdict (green APPROVED stamp / red glitch REJECTED).
  - Lockout: full-screen crimson glitch overlay, countdown timer, disabled controls.
- Architecture guidance: Next.js 14 (app dir), Tailwind + clsx + tailwind-merge; Zustand for auth/score global store; TanStack Query for polling (notifications) and server state; Framer Motion for heavy “mechanical” motion; React Three Fiber/Canvas for avatar.
- Suggested file tree: `/app/login`, `/app/dashboard`, `/app/contract`, `/app/lockout`; components/ui (BioButton, GlassCard), features (AuditScanner, ScoreBar, TimeDial), canvas (AgentSphere), lib/store, hooks (useInterval/useLockout), globals.css for scanlines/glitch keyframes.

## Agent Service (FastAPI + LangGraph)
- Purpose: Stateless REST but state-aware via vector DB; deterministic logic over LLM; LLM used for perception (vision) and persona output.
- Stack: Python 3.11, FastAPI+Uvicorn; Vertex AI Gemini 2.5 Flash (vision/text) via google-genai/langchain-google-vertexai; LangGraph for DAG; Cloud SQL Postgres + pgvector; GCS for proofs; Harm blocks disabled to keep hostile persona.
- API: POST `/internal/judge/audit` with request_id, user_id, timezone, proof_url, criteria {metric/operator/target}, user_context_summary; returns verdict, extracted_metrics, remarks in persona tone, confidence, processing_time_ms.
- Graph nodes: VisualCortex (Gemini vision -> structured schema), LogicGate (pure Python compare), MemoryRecall (pgvector retrieval of last interactions), VoiceSynthesizer (Gemini text with “Iron Judge” prompt).
- Roadmap: project skeleton + deps; GCP SA and env; GCS download utility; Pydantic schema for metrics; forced-tool Gemini call; parsing/comparison helpers; pgvector store; persona prompt and safety config; LangGraph state wiring; FastAPI route; Docker + Cloud Run; testing with mocked Gemini and sample assets.

## Prototype Mapping (`stitch_accountability_health_tracker/*/code.html`)
- `iron_will_sign-in`: Matches Gateway/login spec (hex/mesh background, glitch logo, Google sign-in pill, status footer).
- `accountability_health_tracker_2`: Matches Dashboard HUD (online status, neon score bar, avatar sphere, upload CTA). 
- `accountability_health_tracker_1`: Lockout/punishment state (CRITICAL, SYSTEM LOCKED, countdown, access denied).
- `biohacker_analysis_1/2/3`: Variants of Auditor scan experience (scanner card, laser line, AI eye, progress, verdict-ready UI).
- `habit_contract_configuration`: Contract creation/config panel (protocol name, execution window dial, success logic toggles, initiate contract).
- Gap note: Prototypes are static; need wiring to backend flows (auth, goals list, upload + analyzing state, lockout redirect).

## Immediate Implementation Slices (cross-PRD)
- Backend: Spring Boot auth (Google OAuth2), timezone endpoint, core entities/services, audit upload endpoint calling agent, GCS upload with hashed names, score/lockout enforcement, notifications polling API stub.
- Frontend: Next.js app shell with routes (login, dashboard, contract, lockout), Tailwind theme per palette, ScoreBar + GoalCard + AuditScanner components, React Query hooks for audits/notifications, Zustand store for auth/score/lockout flag, loading/analyzing skeletons.
- Agent: FastAPI `/audit` implementing vision->logic->memory->voice pipeline with deterministic comparison and persona response; pgvector memory stub; config via env/Secret Manager; mockable Gemini client for tests.

## Next Steps
- Confirm GCP project/bucket/DB names and secrets handling approach for local vs Cloud Run.
- Decide Vercel vs Cloud Run for frontend hosting (PRD allows both).
- Align data contracts between Spring Boot `/internal/agent/audit` and agent `/internal/judge/audit` (field naming, status values) before client integration.
- Define minimal sample goals/criteria to seed dev/test environments (e.g., sleep score >=85).


