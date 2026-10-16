"""Personalized recommendation engine - the merge point of all three engines."""
from typing import Dict, List

from . import matching, risk, skill_gap
from .graph import SkillGraph
from .text import level_value, norm

DIFF = {"BEGINNER": 1, "INTERMEDIATE": 2, "ADVANCED": 3}


def _pick_resources(skill_id, skill_name, resources, level: int, limit=3):
    rs = [r for r in resources if (skill_id is not None and r.get("skill_id") == skill_id) or norm(r.get("skill", "")) == norm(skill_name)]
    rs.sort(key=lambda r: (abs(DIFF.get((r.get("difficulty") or "BEGINNER").upper(), 1) - level), r.get("estimated_minutes") or 0))
    return rs[:limit]


def recommend(payload: dict, risk_model) -> Dict:
    student = payload["student"]
    catalog = payload.get("skill_catalog") or []
    resources = payload.get("resources") or []
    state = payload.get("roadmap_state") or {}
    feedback = payload.get("feedback") or {}
    goal = student.get("career_goal") or {}
    warnings: List[str] = []

    graph = SkillGraph(catalog)
    risk_res = risk.analyse(risk_model, payload.get("academics") or {})
    required = goal.get("required_skills") or []
    if not required:
        raise ValueError("Student has no career goal with required skills - set a career goal first")
    if not student.get("skills"):
        warnings.append("No current skills listed - every required skill is treated as missing")
    gap = skill_gap.analyse(student.get("skills") or [], required, catalog, graph)
    matches = matching.match({**student, "career_goal": goal.get("name")}, gap["gaps_ranked"],
                             payload.get("candidates") or [], feedback.get("low_rated_user_ids"))

    by_name = {g["skill"]: g for g in gap["gaps_ranked"]}
    known = {graph.canonical(e["skill"]) or e["skill"] for e in gap["existing"]}
    known |= {graph.canonical(s["name"]) or s["name"] for s in student.get("skills") or []}
    known |= graph.implied_known(list(known))
    # Partially known skills stay on the roadmap but count as known for unlocking.
    targets = [graph.canonical(g["skill"]) or g["skill"] for g in gap["gaps_ranked"]]
    prio = {graph.canonical(g["skill"]) or g["skill"]: g["priority_score"] for g in gap["gaps_ranked"]}
    order = graph.learning_order(targets, known - set(targets), prio)
    in_progress = set(state.get("in_progress_skill_ids") or [])

    slow = 1.2 if risk_res["risk_level"] == "HIGH" else 1.0
    roadmap = []
    for i, name in enumerate(order, 1):
        node = graph.g.nodes.get(name, {})
        g = by_name.get(name) or by_name.get(next((k for k in by_name if norm(k) == norm(name)), ""), {})
        partial = g.get("gap_type") == "PARTIAL"
        hours = float(node.get("estimated_hours") or 20) * (0.5 if partial else 1.0) * slow
        sid = node.get("id") or g.get("skill_id")
        lvl = level_value(g.get("current_level")) if partial else 1
        res = _pick_resources(sid, name, resources, lvl)
        prereqs = graph.prerequisites(name)
        ready = all(p in known for p in prereqs)  # ready with what the student knows today
        reason = ("Required for your goal" if g else "Prerequisite for a goal skill")
        if g.get("gap_type") == "PARTIAL":
            reason = g.get("reason") or "Level up an existing skill"
        roadmap.append({"order": i, "skill_id": sid, "skill": name,
                        "priority": g.get("priority", "MEDIUM" if not g else "LOW"),
                        "prerequisites": prereqs, "ready_now": ready, "blocked_by": [p for p in prereqs if p not in known],
                        "status": "IN_PROGRESS" if sid in in_progress else "NOT_STARTED",
                        "practice_task": node.get("practice_task") or f"Build a small project that uses {name}.",
                        "estimated_hours": round(hours, 1), "resource_ids": [r.get("id") for r in res if r.get("id") is not None],
                        "resources": res, "reason": reason})

    recs = []
    first = next((r for r in roadmap if r["ready_now"]), roadmap[0] if roadmap else None)
    for n, item in enumerate([r for r in roadmap if r["ready_now"]][:3], 1):
        recs.append({"type": "SKILL", "priority": n, "title": f"Learn {item['skill']}",
                     "description": f"{item['reason']}. Estimated {item['estimated_hours']:.0f} hours. Practice: {item['practice_task']}",
                     "payload": {"skill_id": item["skill_id"], "skill": item["skill"]}})
    if first:
        for r in first["resources"][:2]:
            recs.append({"type": "RESOURCE", "priority": 4, "title": r["title"],
                         "description": f"{r.get('type', 'Resource')} for {first['skill']}", "payload": r})
        recs.append({"type": "PRACTICE", "priority": 4, "title": f"Practice task: {first['skill']}",
                     "description": first["practice_task"], "payload": {"skill": first["skill"]}})
    mentor = next((m for m in matches if m["type"] == "MENTOR"), None)
    peer = next((m for m in matches if m["type"] == "PEER"), None)
    if mentor:
        recs.append({"type": "MENTOR", "priority": 3, "title": f"Book a session with {mentor['name']}",
                     "description": "; ".join(mentor["reasons"][:2]), "payload": {"user_id": mentor["user_id"], "score": mentor["score"]}})
    if peer:
        recs.append({"type": "PEER", "priority": 5, "title": f"Study with {peer['name']}",
                     "description": "; ".join(peer["reasons"][:2]), "payload": {"user_id": peer["user_id"], "score": peer["score"]}})
    if risk_res["risk_level"] in ("MEDIUM", "HIGH"):
        recs.append({"type": "ACADEMIC", "priority": 1 if risk_res["risk_level"] == "HIGH" else 2,
                     "title": "Academic support plan",
                     "description": "; ".join(risk_res["suggested_actions"][:3]),
                     "payload": {"risk_level": risk_res["risk_level"], "indicators": risk_res["indicators"]}})
    for note in (feedback.get("recent_progress_notes") or [])[:1]:
        recs.append({"type": "FEEDBACK", "priority": 6, "title": "Build on your last session",
                     "description": f"Your last session noted: \"{note[:140]}\". Continue with the next roadmap item.",
                     "payload": {}})
    recs.sort(key=lambda r: r["priority"])

    if risk_res["risk_level"] == "HIGH":
        next_action = "Focus on academics first: " + (risk_res["suggested_actions"][0] if risk_res["suggested_actions"] else "book a mentor session")
    elif first:
        next_action = f"Start {first['skill']}: {first['practice_task']}"
    else:
        next_action = "All goal skills covered - build a capstone project and mentor a peer."
    return {"risk": risk_res, "skill_gap": gap, "matches": matches, "roadmap": roadmap,
            "recommendations": recs, "next_action": next_action,
            "graph": {"nodes": len(graph.g.nodes), "edges": len(graph.g.edges)}, "warnings": warnings}
