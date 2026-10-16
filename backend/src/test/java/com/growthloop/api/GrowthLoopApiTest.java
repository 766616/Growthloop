package com.growthloop.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthloop.exception.ApiException;
import com.growthloop.service.AiClient;
import com.growthloop.support.FakeAi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * API / integration tests against the full Spring context (H2 in MySQL mode, demo seed loaded).
 * The Python AI service is replaced by {@link FakeAi}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GrowthLoopApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean AiClient ai;

    record Res(int status, JsonNode body) {
        JsonNode data() { return body.path("data"); }
    }

    @BeforeEach
    void fakeAi() {
        when(ai.post(anyString(), any())).thenAnswer(inv -> FakeAi.respond(mapper, inv.getArgument(0), inv.getArgument(1)));
        when(ai.health()).thenReturn(new HashMap<>(Map.of("available", true)));
    }

    // ------------------------------------------------------------------ helpers
    Res call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        MockHttpServletResponse r = mvc.perform(req).andReturn().getResponse();
        String s = r.getContentAsString(StandardCharsets.UTF_8);
        return new Res(r.getStatus(), s.isBlank() ? mapper.createObjectNode() : mapper.readTree(s));
    }

    String login(String email, String password) throws Exception {
        Res r = call(post("/api/auth/login"), null, Map.of("email", email, "password", password));
        assertEquals(200, r.status(), "login failed for " + email + ": " + r.body());
        return r.data().path("token").asText();
    }

    String admin() throws Exception { return login("admin@growthloop.dev", "Admin@123"); }

    long studentIdByRoll(String adminToken, String roll) throws Exception {
        for (JsonNode s : call(get("/api/admin/students"), adminToken, null).data()) {
            if (roll.equals(s.path("rollNumber").asText())) return s.path("id").asLong();
        }
        throw new AssertionError("roll not found " + roll);
    }

    long idByName(JsonNode arr, String name) {
        for (JsonNode n : arr) if (name.equalsIgnoreCase(n.path("name").asText())) return n.path("id").asLong();
        throw new AssertionError("not found " + name);
    }

    // ------------------------------------------------------------------ auth
    @Test
    void registerLoginJwtAndMe() throws Exception {
        Res reg = call(post("/api/auth/register"), null, Map.of("fullName", "Test Learner", "email", "t1@example.com", "password", "Secret@123"));
        assertEquals(201, reg.status(), reg.body().toString());
        String token = reg.data().path("token").asText();
        assertFalse(token.isBlank());
        assertEquals("STUDENT", reg.data().path("user").path("role").asText());
        assertFalse(reg.body().toString().contains("passwordHash"), "password hash must never be returned");

        Res me = call(get("/api/auth/me"), token, null);
        assertEquals(200, me.status());
        assertEquals("t1@example.com", me.data().path("email").asText());

        assertEquals(409, call(post("/api/auth/register"), null, Map.of("fullName", "Dup", "email", "T1@example.com", "password", "Secret@123")).status());
        assertEquals(401, call(post("/api/auth/login"), null, Map.of("email", "t1@example.com", "password", "wrong-pass")).status());
        assertEquals(200, call(post("/api/auth/login"), null, Map.of("email", "t1@example.com", "password", "Secret@123")).status());
        assertEquals(401, call(get("/api/auth/me"), null, null).status());
        assertEquals(401, call(get("/api/auth/me"), "not.a.valid.jwt", null).status());
    }

    @Test
    void validationErrorsAreReported() throws Exception {
        Res r = call(post("/api/auth/register"), null, Map.of("fullName", "", "email", "not-an-email", "password", "123"));
        assertEquals(400, r.status());
        assertFalse(r.body().path("success").asBoolean(true));
        assertTrue(r.body().path("errors").size() >= 3, r.body().toString());
    }

    // ------------------------------------------------------------------ authorization
    @Test
    void roleBasedAuthorization() throws Exception {
        String student = login("aarav.shah@growthloop.dev", "Student@123");
        String teacher = login("meera.iyer@growthloop.dev", "Teacher@123");
        String admin = admin();

        assertEquals(403, call(get("/api/admin/dashboard"), student, null).status());
        assertEquals(403, call(get("/api/teacher/dashboard"), student, null).status());
        assertEquals(403, call(get("/api/admin/users"), teacher, null).status());
        assertEquals(403, call(get("/api/students/me"), teacher, null).status());
        assertEquals(200, call(get("/api/teacher/dashboard"), teacher, null).status());

        Res dash = call(get("/api/admin/dashboard"), admin, null);
        assertEquals(200, dash.status());
        assertTrue(dash.data().path("totalStudents").asInt() >= 10);
        assertEquals(3, dash.data().path("totalTeachers").asInt());
    }

    @Test
    void studentCannotReadAnotherStudentsData() throws Exception {
        String admin = admin();
        long diya = studentIdByRoll(admin, "GL24002");
        String aarav = login("aarav.shah@growthloop.dev", "Student@123");
        assertEquals(403, call(post("/api/ai/risk-analysis?studentId=" + diya), aarav, null).status());
        assertEquals(403, call(get("/api/teacher/students/" + diya), aarav, null).status());
        // without an id the student always gets their own data
        Res own = call(post("/api/ai/risk-analysis"), aarav, null);
        assertEquals(200, own.status(), own.body().toString());
        assertEquals("MEDIUM", own.data().path("riskLevel").asText());
    }

    @Test
    void teacherOnlySeesAuthorisedStudents() throws Exception {
        String admin = admin();
        long aarav = studentIdByRoll(admin, "GL24001"); // assigned to Meera
        assertEquals(200, call(get("/api/teacher/students/" + aarav), login("meera.iyer@growthloop.dev", "Teacher@123"), null).status());
        assertEquals(403, call(get("/api/teacher/students/" + aarav), login("rahul.verma@growthloop.dev", "Teacher@123"), null).status());
        assertEquals(200, call(get("/api/teacher/students/" + aarav), admin, null).status());
    }

    // ------------------------------------------------------------------ academics
    @Test
    void csvImportValidatesEveryRow() throws Exception {
        String csv = String.join("\n",
                "student_id,subject,marks,max_marks,attendance,assignment_score,semester,academic_year",
                "GL24003,CSV Test Subject,72,100,88,80,5,2030-31",
                "GL99999,CSV Test Subject,72,100,88,80,5,2030-31",
                "GL24003,CSV Other,120,100,88,80,5,2030-31",
                "GL24003,CSV Third,50,100,140,80,5,2030-31",
                "GL24003,CSV Fourth,abc,100,88,80,5,2030-31",
                "GL24003,CSV Test Subject,60,100,80,70,5,2030-31");
        MockMultipartFile file = new MockMultipartFile("file", "marks.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse raw = mvc.perform(multipart("/api/admin/academics/import").file(file)
                .header("Authorization", "Bearer " + admin())).andReturn().getResponse();
        assertEquals(200, raw.getStatus(), raw.getContentAsString());
        JsonNode report = mapper.readTree(raw.getContentAsString()).path("data");
        assertEquals(6, report.path("totalRows").asInt());
        assertEquals(1, report.path("successCount").asInt());
        assertEquals(5, report.path("failedCount").asInt());
        String errors = report.path("failed").toString();
        assertTrue(errors.contains("does not exist"));
        assertTrue(errors.contains("exceed max_marks"));
        assertTrue(errors.contains("attendance must be between 0 and 100"));
        assertTrue(errors.contains("marks must be a number") || errors.contains("marks"));
        assertTrue(errors.contains("Duplicate"));

        MockMultipartFile badCols = new MockMultipartFile("file", "bad.csv", "text/csv", "id,subject\n1,x".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, mvc.perform(multipart("/api/admin/academics/import").file(badCols)
                .header("Authorization", "Bearer " + admin())).andReturn().getResponse().getStatus());
    }

    @Test
    void aiServiceOutageDoesNotBreakTheApp() throws Exception {
        doThrow(ApiException.unavailable(AiClient.UNAVAILABLE)).when(ai).post(anyString(), any());
        String student = login("kabir.nair@growthloop.dev", "Student@123");
        Res r = call(post("/api/ai/recommendations"), student, null);
        assertEquals(503, r.status());
        assertTrue(r.body().path("message").asText().contains("temporarily unavailable"));
        assertEquals(200, call(get("/api/students/me/dashboard"), student, null).status());
    }

    // ------------------------------------------------------------------ full scenario (spec section 28)
    @Test
    void endToEndMentoringLoop() throws Exception {
        // 1-2 admin creates a student
        String admin = admin();
        Res created = call(post("/api/admin/users"), admin, Map.of("fullName", "Neha E2E", "email", "neha.e2e@example.com",
                "password", "Student@123", "role", "STUDENT", "branch", "Computer Engineering", "semester", 5));
        assertEquals(201, created.status(), created.body().toString());
        long studentId = created.data().path("student").path("id").asLong();

        // 3-4 student logs in and completes profile
        String student = login("neha.e2e@example.com", "Student@123");
        JsonNode goals = call(get("/api/career-goals"), student, null).data();
        JsonNode skills = call(get("/api/skills"), student, null).data();
        long goalId = idByName(goals, "Java Backend Developer");
        Res prof = call(put("/api/students/me"), student, Map.of("careerGoalId", goalId,
                "interests", List.of("Backend Development", "AI", "Web Development"), "availability", List.of("Mon evening")));
        assertEquals(200, prof.status(), prof.body().toString());
        List<Map<String, Object>> mySkills = List.of(
                Map.of("skillId", idByName(skills, "Java"), "proficiency", "INTERMEDIATE"),
                Map.of("skillId", idByName(skills, "Python"), "proficiency", "INTERMEDIATE"),
                Map.of("skillId", idByName(skills, "HTML"), "proficiency", "ADVANCED"),
                Map.of("skillId", idByName(skills, "CSS"), "proficiency", "INTERMEDIATE"));
        assertEquals(200, call(put("/api/students/me/skills"), student, Map.of("skills", mySkills)).status());

        // 5 admin adds academic records
        Map<String, Object> entry = new HashMap<>(Map.of("studentId", studentId, "subject", "Object Oriented Programming with Java",
                "marks", 58, "maxMarks", 100, "semester", 5, "attendancePercent", 71));
        entry.put("assignmentTitle", "OOP lab 3");
        entry.put("assignmentStatus", "PENDING");
        assertEquals(201, call(post("/api/admin/academics"), admin, entry).status());

        // 6-10 analysis pipeline -> roadmap + matches
        Res analysis = call(post("/api/ai/recommendations"), student, null);
        assertEquals(200, analysis.status(), analysis.body().toString());
        JsonNode roadmap = analysis.data().path("roadmap");
        assertTrue(roadmap.path("items").size() > 0);
        assertEquals("MEDIUM", analysis.data().path("risk").path("riskLevel").asText());
        String missing = analysis.data().path("skillGap").path("missing").toString();
        assertTrue(missing.contains("SQL") && missing.contains("Spring Boot"));
        assertFalse(missing.contains("\"Java\""));
        JsonNode match = analysis.data().path("matches").get(0);
        assertEquals("MENTOR", match.path("type").asText());
        assertFalse(match.path("user").has("email"), "matches must not expose e-mail addresses");
        long mentorUserId = match.path("user").path("id").asLong();

        long itemId = roadmap.path("items").get(0).path("id").asLong();
        assertEquals(200, call(patch("/api/roadmaps/items/" + itemId), student, Map.of("status", "IN_PROGRESS")).status());

        // 11 request a session
        long sqlId = idByName(skills, "SQL");
        Res sess = call(post("/api/sessions"), student, Map.of("mentorUserId", mentorUserId, "topic", "SQL joins help",
                "skillId", sqlId, "date", LocalDate.now().plusDays(2).toString(), "time", "18:00"));
        assertEquals(201, sess.status(), sess.body().toString());
        long sessionId = sess.data().path("id").asLong();

        // 12-13 mentor accepts and completes
        String mentorEmail = null;
        for (JsonNode u : call(get("/api/admin/users"), admin, null).data()) {
            if (u.path("id").asLong() == mentorUserId) mentorEmail = u.path("email").asText();
        }
        String mentor = login(mentorEmail, "Teacher@123");
        assertEquals(400, call(post("/api/feedback"), student, Map.of("sessionId", sessionId, "rating", 5)).status(), "no feedback before completion");
        assertEquals(403, call(patch("/api/sessions/" + sessionId + "/status"), student, Map.of("status", "ACCEPTED")).status());
        assertEquals(200, call(patch("/api/sessions/" + sessionId + "/status"), mentor, Map.of("status", "ACCEPTED")).status());
        assertEquals(200, call(patch("/api/sessions/" + sessionId + "/status"), mentor, Map.of("status", "COMPLETED", "notes", "Covered joins")).status());

        // 14-16 both give feedback; progress + regeneration
        Res fb = call(post("/api/feedback"), student, Map.of("sessionId", sessionId, "rating", 5, "helpfulness", 5,
                "comment", "Great session", "topicsCovered", "INNER JOIN", "progressUpdate", "Can write joins now"));
        assertEquals(201, fb.status(), fb.body().toString());
        assertFalse(fb.data().path("skillUpdated").asBoolean());
        assertTrue(fb.data().path("recommendationsRegenerated").asBoolean());
        assertEquals(409, call(post("/api/feedback"), student, Map.of("sessionId", sessionId, "rating", 4)).status());
        assertEquals(201, call(post("/api/feedback"), mentor, Map.of("sessionId", sessionId, "rating", 4,
                "comment", "Engaged learner", "skillProgress", Map.of("skillId", sqlId, "proficiency", "INTERMEDIATE"))).status());
        verify(ai, never()).post(eq("/model/retrain"), any());

        // 17 dashboard reflects the update
        Res dash = call(get("/api/students/me/dashboard"), student, null);
        assertEquals(200, dash.status());
        JsonNode d = dash.data();
        assertTrue(d.path("skills").toString().contains("SQL"));
        assertFalse(d.path("skillGap").path("missing").toString().contains("\"SQL\""), "SQL gap closed after feedback");
        assertTrue(d.path("roadmap").path("version").asInt() >= 2);
        assertTrue(d.path("recentFeedback").size() >= 1);
        assertEquals(1.0, d.path("academicSummary").path("pendingAssignments").asDouble());

        // teacher/mentor now sees the student (session-linked)
        assertEquals(200, call(get("/api/teacher/students/" + studentId), mentor, null).status());
        // admin can explicitly retrain (separate from regeneration)
        assertEquals(200, call(post("/api/admin/ai/retrain"), admin, null).status());
    }
}
