"""AI Engine 1 - Performance risk analysis.

PROTOTYPE MODEL: a Logistic Regression trained on SYNTHETIC data generated from
plausible academic patterns. No real labelled dataset is used, so the reported
accuracy is a synthetic hold-out number only and says nothing about real-world
performance. Replace `generate_synthetic` with real labelled history before any
real use (see POST /model/retrain).
"""
import os
from collections import defaultdict
from typing import Dict, List, Optional

import joblib
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

FEATURES = ["avg_pct", "attendance_pct", "assignment_pct", "pending_ratio", "trend", "min_subject_pct"]
MODEL_VERSION = "prototype-logreg-synthetic-v1"
DISCLAIMER = ("Prototype model trained on synthetic data. Accuracy figures are measured on "
              "synthetic hold-out data only and are NOT real-world accuracy.")

FEATURE_TEXT = {
    "avg_pct": "Low overall marks",
    "attendance_pct": "Low attendance",
    "assignment_pct": "Weak assignment scores",
    "pending_ratio": "Pending or missing assignments",
    "trend": "Performance dropping compared to last semester",
    "min_subject_pct": "Very low marks in at least one subject",
}


def generate_synthetic(n: int = 3000, seed: int = 42) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    ability = rng.normal(0, 1, n)
    avg = np.clip(62 + 14 * ability + rng.normal(0, 6, n), 5, 100)
    att = np.clip(78 + 9 * ability + rng.normal(0, 9, n), 20, 100)
    asg = np.clip(65 + 15 * ability + rng.normal(0, 10, n), 0, 100)
    pend = np.clip(0.18 - 0.12 * ability + rng.normal(0, 0.1, n), 0, 1)
    trend = rng.normal(0, 7, n) + 2 * ability
    mn = np.clip(avg - np.abs(rng.normal(12, 6, n)), 0, 100)
    latent = (-0.09 * (avg - 55) - 0.07 * (att - 75) - 0.03 * (asg - 60) + 4 * (pend - 0.2)
              - 0.06 * trend - 0.04 * (mn - 40) + rng.normal(0, 0.8, n))
    y = (latent > 0).astype(int)
    return pd.DataFrame({"avg_pct": avg, "attendance_pct": att, "assignment_pct": asg, "pending_ratio": pend,
                         "trend": trend, "min_subject_pct": mn, "at_risk": y})


class RiskModel:
    def __init__(self, model_dir: str = "models"):
        self.model_dir = model_dir
        self.path = os.path.join(model_dir, "risk_model.joblib")
        self.pipeline: Optional[Pipeline] = None
        self.meta: Dict = {}
        self.status = "not_loaded"

    def load_or_train(self):
        try:
            if os.path.exists(self.path):
                bundle = joblib.load(self.path)
                self.pipeline, self.meta = bundle["pipeline"], bundle["meta"]
                self.status = "loaded"
                return
        except Exception:  # corrupt file -> retrain
            pass
        self.train()

    def train(self, labelled: Optional[List[dict]] = None) -> Dict:
        df = generate_synthetic()
        source = "synthetic"
        if labelled:
            real = pd.DataFrame(labelled)
            missing = [c for c in FEATURES + ["at_risk"] if c not in real.columns]
            if missing:
                raise ValueError(f"Labelled rows missing columns: {missing}")
            df = pd.concat([df, real[FEATURES + ["at_risk"]]], ignore_index=True)
            source = f"synthetic+{len(real)}_labelled_rows"
        X, y = df[FEATURES], df["at_risk"]
        Xtr, Xte, ytr, yte = train_test_split(X, y, test_size=0.2, random_state=7, stratify=y)
        pipe = Pipeline([("scale", StandardScaler()), ("clf", LogisticRegression(max_iter=500))])
        pipe.fit(Xtr, ytr)
        self.pipeline = pipe
        self.meta = {"model_version": MODEL_VERSION, "training_source": source, "rows": int(len(df)),
                     "synthetic_holdout_accuracy": round(float(pipe.score(Xte, yte)), 3), "disclaimer": DISCLAIMER}
        os.makedirs(self.model_dir, exist_ok=True)
        joblib.dump({"pipeline": pipe, "meta": self.meta}, self.path)
        self.status = "trained"
        return self.meta


def build_features(academics: dict) -> Optional[Dict]:
    records = academics.get("records") or []
    attendance = academics.get("attendance") or []
    assignments = academics.get("assignments") or []
    if not records and not attendance and not assignments:
        return None
    pcts = [r["percentage"] for r in records if r.get("percentage") is not None]
    by_sem = defaultdict(list)
    for r in records:
        if r.get("percentage") is not None:
            by_sem[(r.get("academic_year") or "", r.get("semester") or 0)].append(r["percentage"])
    sems = sorted(by_sem)
    trend = 0.0
    if len(sems) >= 2:
        trend = float(np.mean(by_sem[sems[-1]]) - np.mean(by_sem[sems[-2]]))
    latest = by_sem[sems[-1]] if sems else pcts
    att = [a["percentage"] for a in attendance if a.get("percentage") is not None]
    scored = [a["score_pct"] for a in assignments if a.get("score_pct") is not None and (a.get("status") or "") in ("SUBMITTED", "LATE")]
    pending = [a for a in assignments if (a.get("status") or "") in ("PENDING", "MISSING")]
    return {
        "avg_pct": float(np.mean(latest)) if latest else 60.0,
        "attendance_pct": float(np.mean(att)) if att else 75.0,
        "assignment_pct": float(np.mean(scored)) if scored else (0.0 if pending else 60.0),
        "pending_ratio": len(pending) / len(assignments) if assignments else 0.0,
        "trend": trend,
        "min_subject_pct": float(min(latest)) if latest else 60.0,
    }


def subject_indicators(academics: dict) -> List[str]:
    out = []
    subj = defaultdict(list)
    for r in academics.get("records") or []:
        if r.get("percentage") is not None:
            subj[r["subject"]].append(r["percentage"])
    for s, v in sorted(subj.items()):
        if v[-1] < 50:
            out.append(f"Low performance in {s} ({v[-1]:.0f}%)")
    for a in academics.get("attendance") or []:
        if a.get("percentage") is not None and a["percentage"] < 75:
            out.append(f"Attendance below 75% in {a['subject']} ({a['percentage']:.0f}%)")
    pend = [a for a in academics.get("assignments") or [] if (a.get("status") or "") in ("PENDING", "MISSING")]
    if pend:
        out.append(f"{len(pend)} pending/missing assignment(s)")
    return out


def suggested_actions(level: str, feats: Dict, academics: dict) -> List[str]:
    acts = []
    weak = sorted({r["subject"] for r in academics.get("records") or [] if (r.get("percentage") or 100) < 50})
    for s in weak[:3]:
        acts.append(f"Additional practice in {s}")
    if feats["attendance_pct"] < 75:
        acts.append("Improve attendance to at least 75%")
    if feats["pending_ratio"] > 0:
        acts.append("Clear pending assignments this week")
    if level in ("MEDIUM", "HIGH"):
        acts.append("Book a mentor session for academic support")
    if level == "HIGH":
        acts.append("Teacher follow-up recommended")
    if not acts:
        acts.append("Keep up the current pace - no academic action needed")
    return acts


def analyse(model: RiskModel, academics: dict) -> Dict:
    feats = build_features(academics)
    info = {**model.meta, "status": model.status}
    if feats is None:
        return {"risk_level": "UNKNOWN", "probability": None, "indicators": [],
                "suggested_actions": ["Academic records are needed before risk can be assessed"],
                "features": None, "model": info}
    if model.pipeline is None:
        # Model unavailable -> transparent rule-based fallback
        score = 0.0
        score += 0.35 if feats["avg_pct"] < 50 else 0.15 if feats["avg_pct"] < 60 else 0
        score += 0.3 if feats["attendance_pct"] < 75 else 0
        score += 0.2 if feats["pending_ratio"] > 0.25 else 0
        score += 0.15 if feats["min_subject_pct"] < 40 else 0
        prob = min(score, 0.99)
        info = {"status": "fallback_rules", "disclaimer": "Model unavailable - rule-based estimate."}
        contribs = {}
    else:
        x = pd.DataFrame([feats])[FEATURES]
        prob = float(model.pipeline.predict_proba(x)[0][1])
        scaler, clf = model.pipeline.named_steps["scale"], model.pipeline.named_steps["clf"]
        z = scaler.transform(x)[0]
        contribs = {f: float(c * v) for f, c, v in zip(FEATURES, clf.coef_[0], z)}
    level = "LOW" if prob < 0.35 else "MEDIUM" if prob < 0.65 else "HIGH"
    indicators = [FEATURE_TEXT[f] for f, c in sorted(contribs.items(), key=lambda kv: -kv[1]) if c > 0.25]
    indicators += subject_indicators(academics)
    seen, uniq = set(), []
    for i in indicators:
        if i not in seen:
            seen.add(i); uniq.append(i)
    return {"risk_level": level, "probability": round(prob, 3), "indicators": uniq,
            "suggested_actions": suggested_actions(level, feats, academics),
            "features": {k: round(v, 2) for k, v in feats.items()},
            "contributions": {k: round(v, 3) for k, v in contribs.items()}, "model": info}
