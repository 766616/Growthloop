# GrowthLoop

AI-assisted student growth & mentoring platform.
**React (Vite + Tailwind) · Spring Boot 3 (Java 17, JWT, JPA) · MySQL · Python FastAPI (scikit-learn, NetworkX, TF-IDF)**

```
frontend/     React app            -> http://localhost:5173
backend/      Spring Boot REST API -> http://localhost:8080/api
ai-service/   FastAPI AI engines   -> http://localhost:8000
database/     schema.sql, seed JSON, sample_academics.csv
scripts/      e2e_test.py (17-step scenario against the running stack)
```

## Prerequisites
Java 17+, Maven 3.9+, Node 18+, Python 3.10+, MySQL 8 (or Docker Compose for MySQL only).

## Run it (VS Code — 3 terminals)

**1. Database** – configure and start MySQL. The DB `growthloop` is auto-created; tables are
created by JPA (`database/schema.sql` is the same schema if you prefer to create it manually).
For the Compose database, from the project root:
```bash
cp .env.example .env
```
Set `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` in `.env` to different strong passwords, then run:
```bash
docker compose up -d
```
The app database credentials in root `.env` must match `DB_USERNAME` and `DB_PASSWORD` in
`backend/.env`. The Compose database port is bound to localhost only; do not expose port 3306
to LAN clients. MySQL applies these credentials only when it initializes a new data volume; if
you already have a Compose volume from an earlier setup, create the `growthloop_app` database user
with the configured `DB_PASSWORD` in that existing MySQL instance rather than deleting a volume
that contains data.

**2. AI service**
```bash
cd ai-service
python -m venv .venv && .venv\Scripts\activate      # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --port 8000 --reload
```
First start trains the prototype risk model (~2 s) and saves it to `models/`.

**3. Backend**
```bash
cd backend
cp .env.example .env        # match DB credentials; set a stable JWT_SECRET (32+ chars)
mvn spring-boot:run
```
`backend/.env` is loaded automatically. On an empty DB it seeds demo data (skills, goals,
resources, 3 teachers, 10 fictional students, marks, sessions, feedback) and, if the AI service
is up, pre-computes every student's analysis.

**4. Frontend**
```bash
cd frontend
cp .env.example .env        # VITE_API_URL=http://localhost:8080/api
npm install
npm run dev
```
VS Code: `.vscode/launch.json` has launch configs for all three services.

### Share the app with clients on your LAN

Run one backend process and one MySQL instance on the host computer; browsers on other devices
connect to that same backend. Keep the local setup above for localhost-only development. To serve
the app to LAN clients:

1. Give the host a stable LAN IP (or DNS name). Start Compose MySQL as above; it remains bound to
   `127.0.0.1:3306`, so only the host's backend can reach it.
2. In `backend/.env`, set `SERVER_ADDRESS=0.0.0.0` and include each exact frontend origin in
   `CORS_ALLOWED_ORIGINS`, comma-separated. For example:
   `CORS_ALLOWED_ORIGINS=http://localhost:5173,http://192.168.1.20:5173`.
   Do not use `*`; use the actual host IP/name and frontend port that clients will open.
3. In `frontend/.env`, set `VITE_API_URL=http://192.168.1.20:8080/api` (replace the example IP
   with the host's LAN IP/name), then restart Vite. Vite listens on all interfaces at port 5173;
   clients open `http://192.168.1.20:5173`.
4. Start or restart the backend (`mvn spring-boot:run` from `backend`) and Vite (`npm run dev`
   from `frontend`). Keep both processes running on the host.
5. Remote browsers need access only to the host's frontend on TCP 5173 and Spring API on TCP
   8080. Allow those inbound ports from the trusted private LAN only in the host firewall.
   MySQL 3306 is for the colocated backend only: keep its Compose loopback binding and do not
   expose it to LAN clients or the internet. Ensure the network allows device-to-device
   connections; never expose this development setup directly to the public internet.

`SERVER_ADDRESS=127.0.0.1` and the localhost Vite API URL remain suitable for local development.
`JWT_SECRET` must be a randomly generated secret (for example, `openssl rand -base64 48`), at
least 32 characters, and must stay unchanged between backend restarts: changing it invalidates
existing login tokens. Keep `backend/.env` and the project-root `.env` private; never commit either.

## Demo logins
| Role | Email | Password |
|---|---|---|
| Admin | admin@growthloop.dev | Admin@123 |
| Teacher | meera.iyer@growthloop.dev (also rahul.verma, sana.qureshi) | Teacher@123 |
| Student | aarav.shah@growthloop.dev (also diya.patel, kabir.nair, …) | Student@123 |

Change via `DEMO_ADMIN_PASSWORD`, `DEMO_TEACHER_PASSWORD`, `DEMO_STUDENT_PASSWORD`; disable seeding with `SEED_DEMO_DATA=false`.

## Tests
```bash
cd ai-service && pytest                 # AI engines (17 tests)
cd backend && mvn test                  # H2 + MockMvc: auth, RBAC, CSV import, sessions/feedback, dashboards
cd frontend && npm test                 # component/helper tests (Vitest)
python scripts/e2e_test.py              # full 17-step scenario, needs all 3 services running
```

## CSV import (Admin → Academic records)
```
student_id,subject,marks,max_marks,attendance,assignment_score
GL24001,Operating Systems,71,100,86,78
```
`student_id` = roll number. Optional columns: `semester, academic_year, assignment_status`.
Rows are validated individually (unknown student, marks > max, out-of-range attendance, duplicates…)
and a per-row report is returned. See `database/sample_academics.csv` (contains 2 intentionally bad rows).

## How it fits together
- **Risk engine** – logistic regression on avg %, attendance, assignment score, pending ratio, trend,
  weakest subject → LOW/MEDIUM/HIGH + indicators + actions. Falls back to transparent rules if the model is missing.
- **Skill-gap engine** – TF-IDF cosine similarity between student skills and goal requirements → existing / partial / missing, prioritised.
- **Dependency graph** – NetworkX DAG of prerequisites; missing skills are topologically ordered; blocked items are flagged.
- **Matching** – mentors/peers scored on gap coverage, reciprocity, goal/interest similarity, availability overlap and trust score (from feedback), with human-readable reasons.
- **Recommendation engine** – merges all of the above into a versioned roadmap (NOT_STARTED/IN_PROGRESS/COMPLETED), resource, practice, mentor and academic recommendations.
- **Feedback loop** – after a COMPLETED session both sides rate each other; trust scores update and the learner's recommendations regenerate. ML retraining is separate (Admin → "Retrain risk model").
- **Security** – JWT (stateless), BCrypt, role rules (`/api/admin/**` ADMIN, `/api/teacher/**` TEACHER/ADMIN, `/api/students/me/**` STUDENT); students can only read their own data; teachers only their assigned / session-linked students.

## Honest notes
- The risk model is a **prototype trained on synthetic data** (holdout accuracy ~0.94 on synthetic labels only). It is an early-support signal, not a validated predictor; retrain on real labelled data before any real use.
- Demo people are fictional. Academic data is admin-entered; university portal integration is future work.
- If the AI service is down, the app keeps working with the last saved analysis and shows a clear message.
