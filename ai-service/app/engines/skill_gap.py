"""AI Engine 2 - Skill gap analysis (TF-IDF + cosine similarity + dependency graph)."""
from typing import Dict, List

from .graph import SkillGraph
from .text import LEVEL_NAMES, blended_similarity, level_value, norm

RELATED_THRESHOLD = 0.33  # tuned so MySQL~SQL and Core Java~Java match, Java!~JavaScript


def _text(skill: dict, catalog_by_name: Dict[str, dict]) -> str:
    c = catalog_by_name.get(norm(skill["name"]), {})
    return " ".join([skill["name"], c.get("category", ""), c.get("description", "")])


def analyse(student_skills: List[dict], required: List[dict], catalog: List[dict], graph: SkillGraph | None = None) -> Dict:
    if not required:
        raise ValueError("Career goal has no required skills")
    graph = graph or SkillGraph(catalog)
    cat = {norm(c["name"]): c for c in catalog}
    have = {norm(s["name"]): s for s in student_skills}
    have_names = [s["name"] for s in student_skills]
    implied = graph.implied_known([graph.canonical(n) or n for n in have_names])

    sim = blended_similarity([r["name"] for r in required], [_text(r, cat) for r in required],
                             have_names, [_text(s, cat) for s in student_skills]) if student_skills else None

    existing, partial, missing = [], [], []
    for i, r in enumerate(required):
        req_lvl = level_value(r.get("required_level"))
        base = {"skill_id": r.get("skill_id"), "skill": r["name"], "importance": r.get("importance", 3),
                "required_level": LEVEL_NAMES[req_lvl]}
        own = have.get(norm(r["name"]))
        if own:
            lvl = level_value(own.get("proficiency"))
            item = {**base, "current_level": LEVEL_NAMES[lvl], "similarity": 1.0}
            if lvl >= req_lvl:
                existing.append({**item, "match": "exact"})
            else:
                partial.append({**item, "gap_type": "PARTIAL", "match": "below_required_level",
                                "reason": f"You have {r['name']} at {LEVEL_NAMES[lvl].title()}, goal needs {LEVEL_NAMES[req_lvl].title()}"})
            continue
        if graph.canonical(r["name"]) in implied:
            existing.append({**base, "current_level": "BEGINNER", "similarity": 1.0, "match": "implied_by_graph",
                             "reason": "Implied because you know a skill that depends on it"})
            continue
        if sim is not None and sim.shape[1]:
            j = int(sim[i].argmax())
            score = float(sim[i][j])
            if score >= RELATED_THRESHOLD:
                partial.append({**base, "gap_type": "PARTIAL", "current_level": "BEGINNER", "similarity": round(score, 3), "match": "related",
                                "related_to": have_names[j],
                                "reason": f"Related to your {have_names[j]} skill (similarity {score:.2f})"})
                continue
        missing.append({**base, "gap_type": "MISSING", "current_level": None, "similarity": round(float(sim[i].max()), 3) if sim is not None and sim.shape[1] else 0.0,
                        "match": "none"})

    known = {graph.canonical(x["skill"]) or x["skill"] for x in existing} | implied
    known |= {graph.canonical(n) or n for n in have_names}
    gaps = partial + missing
    for g in gaps:
        name = graph.canonical(g["skill"]) or g["skill"]
        ready = graph.is_ready(name, known)
        severity = 1.0 if g["gap_type"] == "MISSING" else 0.5
        g["prerequisites"] = graph.prerequisites(name)
        g["missing_prerequisites"] = [p for p in g["prerequisites"] if p not in known]
        g["ready"] = ready
        g["priority_score"] = round(g["importance"] * 15 * severity + (20 if ready else 0) + (10 if g["gap_type"] == "PARTIAL" else 0), 1)
        g["priority"] = "HIGH" if g["priority_score"] >= 60 else "MEDIUM" if g["priority_score"] >= 35 else "LOW"
    gaps.sort(key=lambda g: -g["priority_score"])
    total = len(required)
    coverage = round(100 * (len(existing) + 0.5 * len(partial)) / total, 1)
    next_skills = [g["skill"] for g in gaps if g["ready"]][:3]
    return {"existing": existing, "partial": partial, "missing": missing, "gaps_ranked": gaps,
            "coverage_percent": coverage, "recommended_next": next_skills}
