# Iron Will Agent (FastAPI + LangGraph)

Purpose: Vision + logic + persona “Iron Judge” that returns PASS/FAIL with remarks; stateless REST, state-aware via pgvector history.

Stack
- Python 3.11, FastAPI, Uvicorn
- LangGraph for pipeline; google-genai or langchain-google-vertexai for Gemini 2.5 Flash (forced tool schema)
- Postgres + pgvector (shared Cloud SQL); GCS for proofs
- JSON logging; per-route token-bucket rate limit (optional)

API
- `POST /internal/judge/audit` with `X-Internal-Secret`
  - request_id, user_id, goal_id, timezone, proof_url
  - criteria {metric, operator, target}
  - goal_context {title, description?}
  - user_context_summary (optional)
- Response: verdict PASS/FAIL, extracted_metrics, remarks (persona), score_impact, confidence?, processing_time_ms
- Errors: 500 → caller treats as technical difficulty (no penalty); 400 → immediate reject

Pipeline (LangGraph)
- VisualCortex: download GCS image, Gemini Vision (forced tool) → ExtractMetricsSchema
- LogicGate: pure Python compare (>, <, >=, <=, ==); parse times/percents; default FAIL on unreadable
- MemoryRecall: pgvector fetch last ~3 interactions
- VoiceSynthesizer: Gemini text, hostile persona, safety BLOCK_NONE
- Flow: Vision → Logic → Memory → Voice

Run (local)
1) Copy `env.example` to `.env` and fill values.
2) Ensure Postgres + pgvector and GCS access (ADC or key).
3) `uvicorn main:app --reload --port 8081`

Deploy (single env first)
- Cloud Run; service account with Vertex AI User, Storage Object Viewer, Cloud SQL Client, Secret Manager Accessor.
- Env vars from Secret Manager; connect to Cloud SQL; share bucket with core.

Env vars (see `env.example`)
- APP_PORT, GCP_PROJECT, GCP_REGION
- DB_URL, DB_USERNAME, DB_PASSWORD
- GCS_BUCKET
- AGENT_INTERNAL_SECRET
- GEMINI_PROJECT (or use GCP_PROJECT), GEMINI_LOCATION, GEMINI_MODEL


