# Iron Will

Working notes:
- PRD extractions live in `docs/prd_text/` (core platform, UI framework, agent).
- Markdown PRDs (kept up to date): `docs/prd/core_platform_prd.md`, `docs/prd/ui_framework_prd.md`, `docs/prd/agent_prd.md`.
- Consolidated summary and prototype mapping: `docs/prd_summary.md`.

Project layout (scaffolds)
- `apps/core`  — Spring Boot system-of-record (auth, goals, audits, notifications). See `apps/core/README.md` and `apps/core/env.example`.
- `apps/agent` — FastAPI + LangGraph “Iron Judge” (vision + logic + persona). See `apps/agent/README.md` and `apps/agent/env.example`.
- `apps/web`   — Next.js HUD client (login, dashboard, contract, auditor, lockout). See `apps/web/README.md` and `apps/web/env.local.example`.