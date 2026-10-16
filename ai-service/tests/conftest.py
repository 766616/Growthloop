import pytest
from fastapi.testclient import TestClient

from app.main import API_KEY, app


@pytest.fixture(scope="session")
def client():
    headers = {"X-AI-KEY": API_KEY} if API_KEY else {}
    with TestClient(app, headers=headers) as c:
        yield c


@pytest.fixture
def java_student():
    return {
        "student": {"user_id": 100, "name": "Test Student", "career_goal": {"name": "Java Backend Developer"},
                    "interests": ["Backend Development", "AI"], "availability": ["Mon evening"],
                    "skills": [{"name": "Java", "proficiency": "INTERMEDIATE"}, {"name": "Python", "proficiency": "INTERMEDIATE"},
                               {"name": "HTML", "proficiency": "ADVANCED"}, {"name": "CSS", "proficiency": "INTERMEDIATE"}]},
        "academics": {"records": [{"subject": "Java", "percentage": 82, "semester": 5, "academic_year": "2025-26"},
                                  {"subject": "DBMS", "percentage": 76, "semester": 5, "academic_year": "2025-26"}],
                      "attendance": [{"subject": "Java", "percentage": 91}],
                      "assignments": [{"subject": "Java", "status": "SUBMITTED", "score_pct": 88}]},
        "candidates": [
            {"user_id": 1, "name": "Mentor M", "type": "MENTOR", "skills": [{"name": "SQL"}, {"name": "Spring Boot"}],
             "availability": ["Mon evening"], "trust_score": 0.9},
            {"user_id": 2, "name": "Peer P", "type": "PEER", "career_goal": "Java Backend Developer",
             "skills": [{"name": "SQL", "proficiency": "ADVANCED"}], "goal_skills": ["Java", "SQL"]},
            {"user_id": 3, "name": "Unrelated U", "type": "PEER", "skills": [{"name": "Deep Learning", "proficiency": "ADVANCED"}]},
        ],
    }
