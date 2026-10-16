"""AI Engine 3 - Peer / mentor matching.

Score (0-100) = 45% need coverage + 15% reciprocity/expertise + 15% goal & interest
similarity (TF-IDF cosine) + 10% availability overlap + 15% trust (feedback ratings).
Only non-sensitive fields (name, type, relevant strengths) are returned.
"""
from typing import Dict, List

from .text import level_value, norm, similarity_matrix

W_NEED, W_RECIP, W_GOAL, W_AVAIL, W_TRUST = 0.45, 0.15, 0.15, 0.10, 0.15
DEFAULT_TRUST = 0.6


def _strengths(c: dict) -> Dict[str, int]:
    out = {}
    for s in c.get("skills") or []:
        lvl = 3 if c.get("type") == "MENTOR" else level_value(s.get("proficiency"))
        out[norm(s["name"])] = lvl
    return out


def match(student: dict, needs: List[dict], candidates: List[dict], low_rated_ids: List[int] | None = None, top_k: int = 6) -> List[Dict]:
    """needs: [{skill, priority_score}] from skill-gap analysis."""
    low_rated = set(low_rated_ids or [])
    if not candidates:
        return []
    need_w = {norm(n["skill"]): max(float(n.get("priority_score", 50)), 1.0) for n in needs}
    total_need = sum(need_w.values()) or 1.0
    my_strong = {norm(s["name"]): level_value(s.get("proficiency")) for s in student.get("skills") or []}
    my_profile = " ".join([student.get("career_goal") or ""] + (student.get("interests") or []))
    profiles = [" ".join([c.get("career_goal") or ""] + (c.get("interests") or []) + [s["name"] for s in c.get("skills") or []]) for c in candidates]
    goal_sim = similarity_matrix([my_profile], profiles, "word")[0] if my_profile.strip() else [0] * len(candidates)
    my_av = {norm(a) for a in student.get("availability") or []}

    results = []
    for idx, c in enumerate(candidates):
        if c.get("user_id") == student.get("user_id"):
            continue
        st = _strengths(c)
        helped = [n for n in need_w if st.get(n, 0) >= 2]
        need_cov = sum(need_w[n] for n in helped) / total_need
        if c.get("type") == "MENTOR":
            recip = min(len(helped) / 3, 1.0)
            recip_reason = None
        else:
            their_needs = {norm(n) for n in c.get("goal_skills") or []} - set(st)
            teach = [n for n in their_needs if my_strong.get(n, 0) >= 2]
            recip = min(len(teach) / 2, 1.0)
            recip_reason = teach
        av = {norm(a) for a in c.get("availability") or []}
        avail = len(my_av & av) / len(my_av | av) if (my_av or av) else 0.0
        trust = c.get("trust_score")
        trust = DEFAULT_TRUST if trust is None else max(0.0, min(float(trust), 1.0))
        if c.get("user_id") in low_rated:
            trust *= 0.5
        score = 100 * (W_NEED * need_cov + W_RECIP * recip + W_GOAL * float(goal_sim[idx]) + W_AVAIL * avail + W_TRUST * trust)
        if not helped:
            score *= 0.4  # cannot help with any current need
        name_of = {norm(s["name"]): s["name"] for s in c.get("skills") or []}
        reasons = []
        for n in sorted(helped, key=lambda n: -need_w[n])[:3]:
            lvl = "mentor expertise" if c.get("type") == "MENTOR" else ("strong" if st[n] == 3 else "intermediate") + " proficiency"
            reasons.append(f"Has {lvl} in {name_of.get(n, n)}, which you need")
        if recip_reason:
            reasons.append(f"You can help them with {', '.join(sorted(recip_reason))[:60]} - a two-way match")
        if goal_sim[idx] > 0.2:
            reasons.append("Similar career goal or interests")
        if avail > 0:
            common = [a for a in c.get("availability") or [] if norm(a) in my_av]
            reasons.append(f"Shared availability: {', '.join(common)}")
        if c.get("trust_score") is not None and c["trust_score"] >= 0.8:
            reasons.append(f"Highly rated in past sessions ({c['trust_score'] * 5:.1f}/5)")
        results.append({"user_id": c.get("user_id"), "name": c.get("name"), "type": c.get("type", "PEER"),
                        "score": round(score, 1),
                        "can_help_with": [name_of.get(n, n) for n in helped],
                        "reasons": reasons or ["General profile similarity"],
                        "components": {"need_coverage": round(need_cov, 3), "reciprocity": round(recip, 3),
                                       "goal_similarity": round(float(goal_sim[idx]), 3), "availability": round(avail, 3),
                                       "trust": round(trust, 3)}})
    results.sort(key=lambda r: -r["score"])
    return results[:top_k]
