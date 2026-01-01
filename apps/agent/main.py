import os
from functools import lru_cache
from typing import Optional

import uvicorn
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel


class Settings(BaseModel):
    app_port: int = int(os.getenv("APP_PORT", "8081"))
    gcp_project: str = os.getenv("GCP_PROJECT", "local-project")
    gcs_bucket: str = os.getenv("GCS_BUCKET", "iron-will-proofs")
    agent_internal_secret: str = os.getenv("AGENT_INTERNAL_SECRET", "dev-secret")
    gemini_project: Optional[str] = os.getenv("GEMINI_PROJECT")
    gemini_location: str = os.getenv("GEMINI_LOCATION", "us-central1")
    gemini_model: str = os.getenv("GEMINI_MODEL", "gemini-2.5-flash-lite-preview")
    db_url: str = os.getenv("DB_URL", "postgresql+psycopg://postgres:postgres@localhost:5432/iron_will")


@lru_cache
def get_settings() -> Settings:
    return Settings()


class Criteria(BaseModel):
    metric: str
    operator: str
    target: float | int | str


class GoalContext(BaseModel):
    title: str
    description: Optional[str] = None


class AuditRequest(BaseModel):
    request_id: str
    user_id: str
    goal_id: str
    timezone: str
    proof_url: str
    criteria: Criteria
    goal_context: GoalContext
    user_context_summary: Optional[str] = None


class ExtractedMetrics(BaseModel):
    primary_value: Optional[float] = None
    app_name: Optional[str] = None
    date_detected: Optional[str] = None
    secondary_text: Optional[str] = None
    is_fraudulent: Optional[bool] = None


class AuditResponse(BaseModel):
    verdict: str
    remarks: str
    extracted_metrics: ExtractedMetrics
    score_impact: float
    confidence: Optional[float] = None
    processing_time_ms: Optional[int] = None


app = FastAPI(title="Iron Will Agent", version="0.1.0")


def verify_internal_secret(x_internal_secret: str = Header(None), settings: Settings = Depends(get_settings)):
    if x_internal_secret != settings.agent_internal_secret:
        raise HTTPException(status_code=401, detail="Unauthorized")


@app.post("/internal/judge/audit", response_model=AuditResponse, dependencies=[Depends(verify_internal_secret)])
async def judge(req: AuditRequest, settings: Settings = Depends(get_settings)):
    # Placeholder: real pipeline will perform vision -> logic -> memory -> persona.
    # For now, echo a deterministic PASS with neutral remarks.
    return AuditResponse(
        verdict="PASS",
        remarks="Placeholder judgement (pipeline not yet wired).",
        extracted_metrics=ExtractedMetrics(primary_value=None),
        score_impact=0.0,
        confidence=1.0,
        processing_time_ms=0,
    )


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    settings = get_settings()
    uvicorn.run(app, host="0.0.0.0", port=settings.app_port)

