import pytest

from app.engines.graph import SkillGraph
from app.main import API_KEY, SEED


def test_graph_orders_prerequisites_first():
    g = SkillGraph(SEED["skills"])
    order = g.learning_order(["Spring Boot"], {"Programming Basics", "Java"}, {})
    assert order.index("SQL") < order.index("JDBC") < order.index("Spring Boot")
    assert order.index("OOP") < order.index("Spring Boot")


def test_graph_survives_cycle():
    cat = [{"id": 1, "name": "A", "prerequisite_ids": [2]}, {"id": 2, "name": "B", "prerequisite_ids": [1]}]
    g = SkillGraph(cat)
    assert len(g.learning_order(["A", "B"], set(), {})) == 2


def test_matching_ranks_helpful_candidates(client, java_student):
    m = client.post("/match", json=java_student).json()["matches"]
    names = [x["name"] for x in m]
    assert names.index("Unrelated U") == len(names) - 1
    top = m[0]
    assert top["reasons"] and 0 <= top["score"] <= 100
    assert "email" not in top


def test_full_recommendation(client, java_student):
    r = client.post("/recommend", json=java_student).json()
    road = [x["skill"] for x in r["roadmap"]]
    assert road.index("SQL") < road.index("Spring Boot")
    sb = next(x for x in r["roadmap"] if x["skill"] == "Spring Boot")
    assert sb["ready_now"] is False and sb["blocked_by"]
    types = {x["type"] for x in r["recommendations"]}
    assert "SKILL" in types and "MENTOR" in types
    assert r["next_action"]


def test_progress_changes_recommendation(client, java_student):
    before = client.post("/recommend", json=java_student).json()
    java_student["student"]["skills"] += [{"name": "SQL", "proficiency": "INTERMEDIATE"}, {"name": "OOP", "proficiency": "INTERMEDIATE"}]
    after = client.post("/recommend", json=java_student).json()
    assert after["skill_gap"]["coverage_percent"] > before["skill_gap"]["coverage_percent"]
    assert "SQL" not in [x["skill"] for x in after["roadmap"]]


def test_retrain_endpoint(client, monkeypatch):
    from app.main import MODEL

    trained = []

    def fake_train(labelled=None):
        trained.append(labelled)
        return {"disclaimer": "Synthetic model used for this test"}

    monkeypatch.setattr(MODEL, "train", fake_train)
    r = client.post("/model/retrain", json={}).json()
    assert r["retrained"] and "disclaimer" in r["model"]
    assert trained == [None]


@pytest.mark.skipif(not API_KEY, reason="AI_SERVICE_KEY is not configured")
def test_retrain_rejects_invalid_key(client):
    response = client.post(
        "/model/retrain",
        headers={"X-AI-KEY": f"{API_KEY}-invalid"},
        json={},
    )
    assert response.status_code == 401
