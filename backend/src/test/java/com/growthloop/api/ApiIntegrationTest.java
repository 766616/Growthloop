package com.growthloop.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthloop.exception.ApiException;
import com.growthloop.service.AiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against H2 with the demo seed loaded. The AI service is mocked, so these tests
 * cover the backend's own logic (auth, RBAC, validation, workflows, persistence).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean AiClient ai;

    static final String ADMIN = "admin@growthloop.dev";
    static final String TEACHER = "meera.iyer@growthloop.dev";
    static final String STUDENT = "aarav.shah@growthloop.dev";

    // ------------------------------------------------------------------ helpers
    String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return body(r).path("data").path("token").asText();
    }

    String admin() throws Exception { return login(ADMIN, "Admin@123"); }
    String teacher() throws Exception { return login(TEACHER, "Teacher@123"); }
    String student() throws Exception { return login(STUDENT, "Student@123"); }

    MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    String json(Object o) throws Exception { return mapper.writeValueAsString(o); }

    JsonNode body(MvcResult r) throws Exception { return mapper.readTree(r.getResponse().getContentAsString()); }

    long userId(String token) throws Exception {
        return body(mvc.perform(auth(get("/api/auth/me"), token)).andReturn()).path("data").path("id").asLong();
    }

    // ------------------------------------------------------------------ auth
    @Test
    void registerLoginAndMe() throws Exception {
        String email = "new." + System.nanoTime() + "@example.com";
        MvcResult reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "New Learner", "email", email, "password", "Secret@123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andReturn();
        String token = body(reg).path("data").path("token").asText();
        assertThat(token).isNotBlank();

        mvc.perform(auth(get("/api/auth/me"), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        mvc.perform(auth(get("/api/students/me"), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rollNumber").isNotEmpty());
    }

    @Test
    void duplicateEmailAndBadPasswordAreRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Dup", "email", STUDENT, "password", "Secret@123"))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", STUDENT, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "", "email", "not-an-email", "password", "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ------------------------------------------------------------------ RBAC
    @Test
    void roleBasedAccessIsEnforced() throws Exception {
        mvc.perform(get("/api/students/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/students/me").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());

        String s = student();
        String t = teacher();
        mvc.perform(auth(get("/api/admin/users"), s)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/teacher/students"), s)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/admin/users"), t)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/students/me"), t)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/teacher/students"), t)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/admin/users"), admin())).andExpect(status().isOk());
    }

    @Test
    void teacherCannotOpenUnassignedStudent() throws Exception {
        String a = admin();
        MvcResult created = mvc.perform(auth(post("/api/admin/users"), a).content(json(Map.of(
                        "fullName", "Unassigned Student", "email", "unassigned." + System.nanoTime() + "@example.com",
                        "password", "Student@123", "role", "STUDENT"))))
                .andExpect(status().isCreated()).andReturn();
        long studentProfileId = body(created).path("data").path("student").path("id").asLong();
        assertThat(studentProfileId).isPositive();

        mvc.perform(auth(get("/api/teacher/students/" + studentProfileId), teacher())).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/teacher/students/" + studentProfileId), a)).andExpect(status().isOk());
    }

    @Test
    void feedbackIsParticipantScopedAndAdminGlobalSearchIsProtected() throws Exception {
        String adminToken = admin();
        String learnerToken = student();
        String mentorToken = teacher();
        long learnerId = userId(learnerToken);
        long mentorId = userId(mentorToken);
        String secondEmail = "feedback.isolation." + System.nanoTime() + "@example.com";
        MvcResult secondRegistration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Isolated Learner", "email", secondEmail, "password", "Secret@123"))))
                .andExpect(status().isCreated()).andReturn();
        String otherLearnerToken = body(secondRegistration).path("data").path("token").asText();

        long skillId = body(mvc.perform(auth(get("/api/skills"), learnerToken)).andReturn())
                .path("data").get(0).path("id").asLong();
        MvcResult created = mvc.perform(auth(post("/api/sessions"), learnerToken).content(json(Map.of(
                        "mentorUserId", mentorId, "topic", "Isolation contract session", "skillId", skillId,
                        "date", LocalDate.now().plusDays(2).toString(), "notes", "Preserve these notes"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.notes").value("Preserve these notes")).andReturn();
        long sessionId = body(created).path("data").path("id").asLong();

        mvc.perform(auth(get("/api/sessions"), otherLearnerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(auth(get("/api/feedback/mine"), otherLearnerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.given").isEmpty())
                .andExpect(jsonPath("$.data.received").isEmpty());
        mvc.perform(auth(get("/api/feedback/admin"), learnerToken)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/feedback/admin"), mentorToken)).andExpect(status().isForbidden());
        mvc.perform(auth(patch("/api/sessions/" + sessionId + "/status"), otherLearnerToken)
                        .content(json(Map.of("status", "CANCELLED"))))
                .andExpect(status().isNotFound());
        mvc.perform(auth(patch("/api/sessions/" + sessionId + "/status"), learnerToken)
                        .content(json(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isForbidden());

        mvc.perform(auth(patch("/api/sessions/" + sessionId + "/status"), mentorToken)
                        .content(json(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.notes").value("Preserve these notes"));
        mvc.perform(auth(patch("/api/sessions/" + sessionId + "/status"), mentorToken)
                        .content(json(Map.of("status", "COMPLETED", "notes", ""))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.notes").doesNotExist());

        mvc.perform(auth(post("/api/feedback"), learnerToken).content(json(Map.of(
                        "sessionId", sessionId, "rating", 5, "skillProgress", Map.of("skillId", skillId, "proficiency", "INTERMEDIATE")))))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/feedback"), learnerToken).content(json(Map.of(
                        "sessionId", sessionId, "rating", 5, "comment", "Cross-client feedback"))))
                .andExpect(status().isCreated());
        mvc.perform(auth(post("/api/feedback"), learnerToken).content(json(Map.of("sessionId", sessionId, "rating", 4))))
                .andExpect(status().isConflict());
        mvc.perform(auth(post("/api/feedback"), otherLearnerToken).content(json(Map.of("sessionId", sessionId, "rating", 4))))
                .andExpect(status().isNotFound());

        long otherSkillId = body(mvc.perform(auth(get("/api/skills"), mentorToken)).andReturn())
                .path("data").get(1).path("id").asLong();
        mvc.perform(auth(post("/api/feedback"), mentorToken).content(json(Map.of("sessionId", sessionId, "rating", 4,
                        "skillProgress", Map.of("skillId", otherSkillId, "proficiency", "ADVANCED")))))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/feedback"), mentorToken).content(json(Map.of("sessionId", sessionId, "rating", 4,
                        "skillProgress", Map.of("skillId", skillId, "proficiency", "INTERMEDIATE")))))
                .andExpect(status().isCreated());

        mvc.perform(auth(get("/api/feedback/mine"), learnerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.given.length()").value(1));
        mvc.perform(auth(get("/api/feedback/mine"), mentorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.received.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.given.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(auth(get("/api/feedback/mine"), otherLearnerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.given").isEmpty())
                .andExpect(jsonPath("$.data.received").isEmpty());

        mvc.perform(auth(get("/api/feedback/admin").param("search", "Cross-client")
                        .param("rating", "5").param("sessionId", Long.toString(sessionId)), adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].from.id").value(learnerId))
                .andExpect(jsonPath("$.data[0].from.email").value("aarav.shah@growthloop.dev"))
                .andExpect(jsonPath("$.data[0].to.id").value(mentorId))
                .andExpect(jsonPath("$.data[0].session.id").value(sessionId))
                .andExpect(jsonPath("$.data[0].session.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].session.notes").doesNotExist());
        mvc.perform(auth(get("/api/feedback/admin").param("participantId", Long.toString(mentorId)), adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void sessionAndFeedbackRequestValidationRejectsInvalidData() throws Exception {
        String learnerToken = student();
        mvc.perform(auth(post("/api/sessions"), learnerToken).content(json(Map.of(
                        "mentorUserId", -1, "topic", "Invalid mentor", "date", LocalDate.now().minusDays(1).toString()))))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/feedback"), learnerToken).content(json(Map.of("sessionId", -1, "rating", 6))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ CSV import
    @Test
    void csvImportReportsRowLevelErrors() throws Exception {
        String csv = String.join("\n",
                "student_id,subject,marks,max_marks,attendance,assignment_score",
                "GL24001,Import Test Subject,72,100,88,80",
                "GL99999,Maths,50,100,80,70",
                "GL24002,Physics,120,100,80,70",
                "GL24003,Chemistry,60,100,140,70");
        MockMultipartFile file = new MockMultipartFile("file", "marks.csv", "text/csv", csv.getBytes());
        mvc.perform(multipart("/api/admin/academics/import").file(file).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(4))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(3));

        MockMultipartFile wrongHeader = new MockMultipartFile("file", "bad.csv", "text/csv", "id,name\n1,x".getBytes());
        mvc.perform(multipart("/api/admin/academics/import").file(wrongHeader).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ catalog
    @Test
    void prerequisiteCycleIsRejected() throws Exception {
        String a = admin();
        JsonNode skills = body(mvc.perform(auth(get("/api/skills"), a)).andReturn()).path("data");
        long basics = -1, spring = -1;
        for (JsonNode s : skills) {
            if (s.path("name").asText().equals("Programming Basics")) basics = s.path("id").asLong();
            if (s.path("name").asText().equals("Spring Boot")) spring = s.path("id").asLong();
        }
        assertThat(basics).isPositive();
        mvc.perform(auth(put("/api/admin/skills/" + basics), a).content(json(Map.of(
                        "name", "Programming Basics", "category", "Programming", "prerequisiteIds", java.util.List.of(spring)))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ session + feedback loop
    @Test
    void sessionLifecycleAndFeedbackRegeneratesRecommendations() throws Exception {
        JsonNode canned = mapper.readTree("""
                {"risk":{"risk_level":"LOW","probability":0.12,"indicators":[],"suggested_actions":["Keep going"],
                         "features":{"avg_pct":78.0},"model":{"model_version":"test"}},
                 "skill_gap":{"coverage_percent":55.0,"existing":[],"partial":[],"missing":[],"recommended_next":["Spring Boot"]},
                 "matches":[],
                 "recommendations":[{"type":"SKILL","title":"Learn Spring Boot","description":"Next skill","priority":1,"payload":{}}],
                 "roadmap":[],"next_action":"Start Spring Boot","warnings":[]}
                """);
        when(ai.post(eq("/recommend"), any())).thenReturn(canned);

        String s = student();
        String t = teacher();
        long teacherUserId = userId(t);

        MvcResult created = mvc.perform(auth(post("/api/sessions"), s).content(json(Map.of(
                        "mentorUserId", teacherUserId, "topic", "Spring Boot basics",
                        "date", LocalDate.now().plusDays(2).toString(), "time", "16:00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("REQUESTED")).andReturn();
        long sessionId = body(created).path("data").path("id").asLong();

        // learner cannot accept own request; cannot give feedback before completion
        mvc.perform(auth(patch("/api/sessions/" + sessionId + "/status"), s).content(json(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/feedback"), s).content(json(Map.of("sessionId", sessionId, "rating", 5))))
                .andExpect(status().isBadRequest());

        mvc.perform(auth(patch("/api/sessions/" + sessionId + "/status"), t).content(json(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        mvc.perform(auth(patch("/api/sessions/" + sessionId + "/status"), t).content(json(Map.of("status", "COMPLETED"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mvc.perform(auth(post("/api/feedback"), s).content(json(Map.of("sessionId", sessionId, "rating", 5, "helpfulness", 5,
                        "comment", "Very clear explanation", "progressUpdate", "Understood controllers"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recommendationsRegenerated").value(true));
        mvc.perform(auth(post("/api/feedback"), s).content(json(Map.of("sessionId", sessionId, "rating", 4))))
                .andExpect(status().isConflict());

        // mentor feedback about the learner still succeeds when the AI service is down
        when(ai.post(eq("/recommend"), any())).thenThrow(ApiException.unavailable(AiClient.UNAVAILABLE));
        mvc.perform(auth(post("/api/feedback"), t).content(json(Map.of("sessionId", sessionId, "rating", 4,
                        "comment", "Good questions", "progressUpdate", "Ready for REST APIs"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recommendationsRegenerated").value(false));

        mvc.perform(auth(get("/api/recommendations/me"), s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations[0].title").value("Learn Spring Boot"));
    }

    // ------------------------------------------------------------------ dashboards
    @Test
    void dashboardsReturnRealAggregates() throws Exception {
        mvc.perform(auth(get("/api/students/me/dashboard"), student())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.rollNumber").value("GL24001"))
                .andExpect(jsonPath("$.data.academicSummary.hasData").value(true));
        mvc.perform(auth(get("/api/teacher/dashboard"), teacher())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudents").isNumber());
        mvc.perform(auth(get("/api/admin/dashboard"), admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudents").isNumber())
                .andExpect(jsonPath("$.data.totalTeachers").value(3));
    }

    @Test
    void aiUnavailableGivesFriendly503() throws Exception {
        when(ai.post(eq("/risk"), any())).thenThrow(ApiException.unavailable(AiClient.UNAVAILABLE));
        mvc.perform(auth(post("/api/ai/risk-analysis"), student()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }
}
