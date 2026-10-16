"""GrowthLoop AI service (FastAPI). Called only by the Spring Boot backend."""
import json
import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .engines import matching, recommender, risk, skill_gap
from .engines.graph import SkillGraph
from .engines.text import norm, similarity_matrix
from .schemas import AnalysisRequest, RetrainRequest

load_dotenv()
log = logging.getLogger("growthloop.ai")
logging.basicConfig(level=logging.INFO)

BASE = Path(__file__).resolve().parent
SEED = json.loads((BASE / "data" / "seed-data.json").read_text(encoding="utf-8"))
MODEL = risk.RiskModel(os.getenv("MODEL_DIR", str(BASE.parent / "models")))
API_KEY = os.getenv("AI_SERVICE_KEY", "")

@asynccontextmanager
async def lifespan(_app):
    try:
        MODEL.load_or_train()
        log.info("Risk model ready: %s", MODEL.meta)
    except Exception as e:  # service still runs; risk falls back to rules
        log.exception("Risk model failed to load: %s", e)
    yield


app = FastAPI(title="GrowthLoop AI Service", version="1.0.0", lifespan=lifespan,
              description="Risk analysis, skill-gap analysis, peer matching and recommendations.")


def check_key(x_ai_key: str | None = Header(default=None)):
    if API_KEY and x_ai_key != API_KEY:
        raise HTTPException(status_code=401, detail="Invalid or missing X-AI-KEY header")


@app.exception_handler(RequestValidationError)
async def _validation(_: Request, exc: RequestValidationError):
    errs = [f"{'.'.join(str(p) for p in e['loc'][1:])}: {e['msg']}" for e in exc.errors()]
    return JSONResponse(status_code=422, content={"detail": "Invalid input", "errors": errs})


@app.exception_handler(ValueError)
async def _value(_: Request, exc: ValueError):
    return JSONResponse(status_code=400, content={"detail": str(exc)})


def _payload(req: AnalysisRequest) -> dict:
    p = req.model_dump()
    if not p["skill_catalog"]:  # standalone use: fall back to bundled dataset
        p["skill_catalog"] = SEED["skills"]
    goal = p["student"].get("career_goal")
    if goal and not goal.get("required_skills"):
        goal["required_skills"] = _career_skills_by_name(goal["name"])
    return p


def _career_skills_by_name(name: str):
    """Match a free-text career goal to the bundled career dataset with TF-IDF cosine similarity."""
    names = [c["name"] for c in SEED["career_goals"]]
    sims = similarity_matrix([name], names, "char_wb")[0]
    best = int(sims.argmax())
    if sims[best] < 0.3:
        raise ValueError(f"Unknown career goal '{name}' and no required skills supplied")
    return [{"skill_id": None, "name": s["skill"], "importance": s["importance"], "required_level": s["required_level"]}
            for s in SEED["career_goals"][best]["skills"]]


@app.get("/health")
def health():
    return {"status": "ok", "risk_model": {**MODEL.meta, "status": MODEL.status}}


@app.post("/risk", dependencies=[Depends(check_key)])
def risk_endpoint(req: AnalysisRequest):
    return risk.analyse(MODEL, req.academics.model_dump())


@app.post("/skill-gap", dependencies=[Depends(check_key)])
def skill_gap_endpoint(req: AnalysisRequest):
    p = _payload(req)
    goal = p["student"].get("career_goal")
    if not goal:
        raise ValueError("Student has no career goal - set one first")
    return skill_gap.analyse(p["student"]["skills"], goal["required_skills"], p["skill_catalog"])


@app.post("/match", dependencies=[Depends(check_key)])
def match_endpoint(req: AnalysisRequest):
    p = _payload(req)
    goal = p["student"].get("career_goal")
    needs = []
    if goal:
        needs = skill_gap.analyse(p["student"]["skills"], goal["required_skills"], p["skill_catalog"])["gaps_ranked"]
    student = {**p["student"], "career_goal": goal["name"] if goal else ""}
    return {"matches": matching.match(student, needs, p["candidates"], p["feedback"]["low_rated_user_ids"])}


@app.post("/recommend", dependencies=[Depends(check_key)])
def recommend_endpoint(req: AnalysisRequest):
    return recommender.recommend(_payload(req), MODEL)


@app.post("/graph/order", dependencies=[Depends(check_key)])
def graph_order(req: AnalysisRequest):
    p = _payload(req)
    g = SkillGraph(p["skill_catalog"])
    known = {g.canonical(s["name"]) or s["name"] for s in p["student"]["skills"]}
    goal = p["student"].get("career_goal") or {"required_skills": []}
    targets = [g.canonical(r["name"]) or r["name"] for r in goal["required_skills"]]
    return {"order": g.learning_order(targets, known | g.implied_known(known), {}), "graph": g.to_dict()}


@app.post("/model/retrain", dependencies=[Depends(check_key)])
def retrain(req: RetrainRequest):
    """Explicit, admin-triggered retraining. Never called automatically after feedback."""
    return {"retrained": True, "model": MODEL.train(req.labelled_rows or None)}
