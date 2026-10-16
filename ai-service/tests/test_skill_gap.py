from app.engines import skill_gap
from app.main import SEED


def test_java_backend_gap(client, java_student):
    r = client.post("/skill-gap", json=java_student).json()
    existing = {e["skill"] for e in r["existing"]}
    missing = {e["skill"] for e in r["missing"]}
    assert "Java" in existing
    assert {"SQL", "Spring Boot", "REST APIs"} <= missing
    # Spring Boot needs JDBC/OOP first, so it must not be an immediate next skill
    assert "Spring Boot" not in r["recommended_next"]
    assert 0 <= r["coverage_percent"] <= 100


def test_proficiency_below_required_is_partial():
    req = [{"name": "Java", "importance": 5, "required_level": "ADVANCED"}]
    r = skill_gap.analyse([{"name": "Java", "proficiency": "BEGINNER"}], req, SEED["skills"])
    assert r["partial"][0]["match"] == "below_required_level"


def test_similar_name_is_partial_not_hardcoded():
    req = [{"name": "SQL", "importance": 5, "required_level": "INTERMEDIATE"}]
    r = skill_gap.analyse([{"name": "MySQL", "proficiency": "INTERMEDIATE"}], req, SEED["skills"])
    assert r["partial"] and r["partial"][0]["related_to"] == "MySQL"


def test_empty_skill_list_all_missing(client, java_student):
    java_student["student"]["skills"] = []
    r = client.post("/skill-gap", json=java_student).json()
    assert r["existing"] == [] and len(r["missing"]) >= 8


def test_no_career_goal_is_400(client, java_student):
    java_student["student"]["career_goal"] = None
    assert client.post("/skill-gap", json=java_student).status_code == 400
