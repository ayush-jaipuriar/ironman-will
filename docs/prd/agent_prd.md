# Iron Will Agent PRD (Markdown)

Source: `Agent PRD Document Iron Will.pdf` (v2.0), converted to Markdown and updated with current decisions.

## 1) Executive Summary
- Agent = “Iron Judge”: deterministic logic + LLM perception/persona. Stateless REST, state-aware via pgvector history.
- Runs on Cloud Run (Python 3.11, FastAPI, LangGraph). Uses the same Cloud SQL Postgres (pgvector) and GCS as the core.
- LLM: Gemini 2.5 Flash (vision + text) with forced tool schema; safety BLOCK_NONE to preserve hostile persona.

## 2) Stack & Infrastructure
- FastAPI + Uvicorn; async/await.
- google-genai or langchain-google-vertexai for Gemini.
- langgraph for orchestration (StateGraph).
- Cloud SQL Postgres + pgvector in same instance as core; sqlmodel/sqlalchemy async engine.
- GCS for proof images; download helper to base64.
- Secrets: via Secret Manager; service account needs Vertex AI User, Storage Object Viewer, Cloud SQL Client, Secret Manager Accessor.
- Observability: JSON logs; simple tracing hooks; optional rate limit per route (token bucket).

## 3) API Contract (Java → Agent)
- Endpoint: `POST /internal/judge/audit`
- Headers: `X-Internal-Secret`
- Request:
  - request_id, user_id, goal_id
  - timezone
  - proof_url (gs://…)
  - criteria: {metric, operator, target} (single-criterion MVP)
  - goal_context: {title, description?}
  - user_context_summary (optional)
- Response:
  - verdict: PASS | FAIL
  - extracted_metrics: {primary_value, app_name?, date_detected?, secondary_text?, is_fraudulent?}
  - remarks (persona voice)
  - score_impact (float)
  - confidence? , processing_time_ms
- Error handling: 500 → Java treats as technical difficulty (no penalty); 400 → immediate reject.

## 4) Cognitive Graph (LangGraph)
- Nodes:
  - VisualCortex: Gemini Vision forced tool → ExtractMetricsSchema; no judgement here.
  - LogicGate: pure Python compare actual vs target (>, <, >=, <=, ==); parse times/percents; set verdict.
  - MemoryRecall: pgvector fetch last ~3 interactions for context; latency <200ms.
  - VoiceSynthesizer: Gemini Text with hostile/tactical persona prompt; uses verdict, actual, target, history.
- Flow: Vision → Logic → Memory → Voice (linear DAG).

## 5) Roadmap (MVP)
- Infra: poetry/pip project; deps: fastapi, uvicorn, google-genai, langgraph, langchain-google-vertexai, psycopg[binary], pgvector, pydantic.
- GCS utility: `download_blob_as_base64(gcs_url)` with error handling.
- Pydantic schemas: ExtractMetricsSchema (primary_value: float, secondary_text: str, app_name: str, date_detected: str, is_fraudulent: bool|None).
- Gemini client: force tool choice; model `gemini-2.5-flash-lite-preview`.
- Logic helpers: parse_value (times, percents), evaluate_metrics(actual, operator, target), default FAIL on unreadable evidence.
- Memory: LangChain PostgresVectorStore table `agent_memories` (id, user_id, content, embedding, metadata); functions fetch_history, save_interaction.
- Persona prompt: “Iron Judge” tone; safety BLOCK_NONE.
- Orchestration: define AgentState (payload, vision_output, logic_output, memory_context, final_response); wire nodes and edges; compile app.
- API: FastAPI `POST /audit` -> ainvoke graph -> return formatted JSON.
- Deploy: Dockerfile (python:3.11-slim, libpq-dev); Cloud Run with concurrency ~80, memory >=1GiB.
- Testing: mock Gemini for unit tests; sample assets folder for vision sanity; fuzz logic inputs (times/percents).

## 6) Updated Decisions (from alignment)
- Single environment first; share Cloud SQL + GCS with core.
- Safety BLOCK_NONE accepted; vision+text on Gemini 2.5 Flash with forced tools.
- Rate limit basic per-route; structured logs and request IDs.
- Embeddings stay in same DB instance (pgvector).


