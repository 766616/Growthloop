from app.engines import risk


def test_model_trains_with_disclaimer(tmp_path):
    m = risk.RiskModel(str(tmp_path))
    meta = m.train()
    assert "synthetic" in meta["disclaimer"].lower()
    assert 0 < meta["synthetic_holdout_accuracy"] <= 1


def test_strong_student_low_risk(client, java_student):
    r = client.post("/risk", json=java_student).json()
    assert r["risk_level"] == "LOW"


def test_weak_student_high_risk_with_indicators(client, java_student):
    java_student["academics"] = {
        "records": [{"subject": "Java", "percentage": 35, "semester": 5}, {"subject": "DBMS", "percentage": 40, "semester": 5}],
        "attendance": [{"subject": "Java", "percentage": 55}],
        "assignments": [{"subject": "Java", "status": "PENDING"}, {"subject": "DBMS", "status": "MISSING"}]}
    r = client.post("/risk", json=java_student).json()
    assert r["risk_level"] == "HIGH"
    assert any("Java" in i for i in r["indicators"])
    assert any("attendance" in a.lower() for a in r["suggested_actions"])


def test_no_academics_is_unknown(client, java_student):
    java_student["academics"] = {}
    assert client.post("/risk", json=java_student).json()["risk_level"] == "UNKNOWN"


def test_fallback_when_model_missing(java_student):
    m = risk.RiskModel("unused")
    out = risk.analyse(m, {"records": [{"subject": "X", "percentage": 30}], "attendance": [{"subject": "X", "percentage": 50}]})
    assert out["model"]["status"] == "fallback_rules"
    assert out["risk_level"] in ("MEDIUM", "HIGH")


def test_invalid_input_rejected(client, java_student):
    java_student["academics"] = {"records": [{"subject": "Java", "percentage": 140}]}
    r = client.post("/risk", json=java_student)
    assert r.status_code == 422
