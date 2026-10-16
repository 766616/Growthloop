"""
End-to-end scenario against a RUNNING stack (MySQL + backend :8080 + AI service :8000).
Usage:  python scripts/e2e_test.py [--api http://localhost:8080/api]
Uses only the Python standard library.
"""
import argparse, json, sys, time, urllib.request, urllib.error
from datetime import date, timedelta

p = argparse.ArgumentParser()
p.add_argument("--api", default="http://localhost:8080/api")
API = p.parse_args().api.rstrip("/")


def call(method, path, token=None, body=None, expect=(200, 201)):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            status, raw = r.status, r.read()
    except urllib.error.HTTPError as e:
        status, raw = e.code, e.read()
    payload = json.loads(raw or b"{}")
    if status not in expect:
        sys.exit(f"FAILED {method} {path}: {status} {payload.get('message')} {payload.get('errors') or ''}")
    return payload.get("data")


def login(email, pw):
    return call("POST", "/auth/login", body={"email": email, "password": pw})["token"]


step = 0
def ok(msg):
    global step
    step += 1
    print(f"[{step:02d}] {msg}")


admin = login("admin@growthloop.dev", "Admin@123")
suffix = int(time.time())
email = f"e2e.student.{suffix}@example.com"
skills = {s["name"]: s["id"] for s in call("GET", "/skills", admin)}
goals = {g["name"]: g for g in call("GET", "/career-goals", admin)}
teacher_profile = next(t for t in call("GET", "/admin/teachers", admin) if t["email"] == "meera.iyer@growthloop.dev")

created = call("POST", "/admin/users", admin, {"fullName": "E2E Student", "email": email, "password": "Student@123",
                                               "role": "STUDENT", "branch": "Computer Engineering", "semester": 5,
                                               "assignedTeacherId": teacher_profile["id"]})
sid = created["student"]["id"]
ok(f"Admin created student {created['student']['rollNumber']}")

student = login(email, "Student@123")
call("PUT", "/students/me", student, {"careerGoalId": goals["Java Backend Developer"]["id"], "interests": ["backend", "APIs"],
                                      "availability": ["Mon evening", "Sat morning"]})
call("PUT", "/students/me/skills", student, {"skills": [{"skillId": skills[n], "proficiency": "INTERMEDIATE"} for n in ["Java", "Python", "HTML", "CSS"] if n in skills]})
ok("Student set goal Java Backend Developer with Java, Python, HTML, CSS")

for subj, marks, att in [("Data Structures", 48, 72), ("DBMS", 61, 80), ("Operating Systems", 39, 68)]:
    call("POST", "/admin/academics", admin, {"studentId": sid, "subject": subj, "marks": marks, "maxMarks": 100, "semester": 5,
                                             "attendancePercent": att, "assignmentTitle": f"{subj} lab", "assignmentStatus": "PENDING"})
ok("Admin added academic records")

risk = call("POST", "/ai/risk-analysis", student)
ok(f"Risk analysis: {risk['riskLevel']} (p={risk['probability']})")
gap = call("POST", "/ai/skill-gap", student)
ok(f"Skill gap: coverage {gap['coveragePercent']}%, missing {[m['skill'] for m in gap['missing']][:5]}")
graph = call("GET", "/skills/graph", student)
ok(f"Dependency graph loaded: {len(graph['nodes'])} skills, {len(graph['edges'])} prerequisite edges")

full = call("POST", "/ai/recommendations", student)
items = full["roadmap"]["items"]
ok(f"Roadmap v{full['roadmap']['version']} generated: {[i['skill']['name'] for i in items[:5]]}")
matches = full["matches"]
mentor = next((m for m in matches if m["type"] == "MENTOR"), None)
assert mentor, "expected at least one mentor match"
ok(f"Matched mentor {mentor['user']['fullName']} (score {mentor['score']}): {mentor['reasons'][0]}")

skill_for_session = next((skills[i["skill"]["name"]] for i in items if i["status"] != "COMPLETED"), None)
sess = call("POST", "/sessions", student, {"mentorUserId": mentor["user"]["id"], "topic": "Roadmap kick-off",
                                           "skillId": skill_for_session, "date": str(date.today() + timedelta(days=2)), "time": "17:00"})
ok(f"Session requested (status {sess['status']})")

mentor_email = {"Meera Iyer": "meera.iyer@growthloop.dev", "Rahul Verma": "rahul.verma@growthloop.dev",
                "Sana Qureshi": "sana.qureshi@growthloop.dev"}.get(mentor["user"]["fullName"])
assert mentor_email, "mentor should be a seeded teacher"
teacher = login(mentor_email, "Teacher@123")
call("PATCH", f"/sessions/{sess['id']}/status", teacher, {"status": "ACCEPTED"})
ok("Mentor accepted")
call("PATCH", f"/sessions/{sess['id']}/status", teacher, {"status": "COMPLETED"})
ok("Session completed")

before = call("GET", "/students/me/progress", student)
fb1 = call("POST", "/feedback", student, {"sessionId": sess["id"], "rating": 5, "helpfulness": 5, "comment": "Clear plan", "progressUpdate": "Know what to learn next"})
ok(f"Student feedback saved (recommendations regenerated: {fb1['recommendationsRegenerated']})")
fb2 = call("POST", "/feedback", teacher, {"sessionId": sess["id"], "rating": 4, "comment": "Motivated learner",
                                          "progressUpdate": "Ready to start", "skillProgress": {"skillId": skill_for_session, "proficiency": "BEGINNER"}})
ok(f"Mentor feedback saved (skill updated: {fb2['skillUpdated']})")

first = next(i for i in call("GET", "/roadmaps/me", student)["items"] if i["status"] != "COMPLETED")
call("PATCH", f"/roadmaps/items/{first['id']}", student, {"status": "IN_PROGRESS"})
rm = call("PATCH", f"/roadmaps/items/{first['id']}", student, {"status": "COMPLETED"})
after = call("GET", "/students/me/progress", student)
ok(f"Progress updated: {before['progressPercent']}% -> {after['progressPercent']}% (completed {first['skill']['name']})")

regen = call("POST", "/ai/recommendations", student)
ok(f"Recommendations regenerated: roadmap v{regen['roadmap']['version']}, {len(regen['recommendations'])} recommendations")

dash = call("GET", "/students/me/dashboard", student)
tdash = call("GET", "/teacher/dashboard", teacher)
ok(f"Dashboards updated: student progress {dash['progressPercent']}%, risk {dash['risk']['riskLevel']}; teacher sees {tdash['totalStudents']} students")
print("\nE2E scenario passed.")
