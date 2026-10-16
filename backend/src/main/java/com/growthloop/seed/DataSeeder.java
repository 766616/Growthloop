package com.growthloop.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthloop.entity.*;
import com.growthloop.entity.enums.*;
import com.growthloop.repository.*;
import com.growthloop.service.AiClient;
import com.growthloop.service.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Loads fictional demo data (seed/seed-data.json) into an EMPTY database.
 * Academic numbers are generated deterministically from each student's roll number,
 * so every fresh install shows the same data.
 */
@Component
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final boolean enabled;
    private final String adminPw;
    private final String teacherPw;
    private final String studentPw;
    private final ObjectMapper mapper;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final AiClient ai;
    private final AnalysisService analysis;
    private final UserRepository users;
    private final SkillRepository skills;
    private final SkillPrerequisiteRepository prereqs;
    private final CareerGoalRepository goals;
    private final CareerGoalSkillRepository goalSkills;
    private final LearningResourceRepository resources;
    private final TeacherProfileRepository teachers;
    private final StudentProfileRepository students;
    private final StudentSkillRepository studentSkills;
    private final AcademicRecordRepository records;
    private final AttendanceRepository attendance;
    private final AssignmentRepository assignments;
    private final PeerSessionRepository sessions;
    private final FeedbackRepository feedback;

    public DataSeeder(@Value("${app.seed.enabled:true}") boolean enabled,
                      @Value("${app.seed.demo-password-admin:Admin@123}") String adminPw,
                      @Value("${app.seed.demo-password-teacher:Teacher@123}") String teacherPw,
                      @Value("${app.seed.demo-password-student:Student@123}") String studentPw,
                      ObjectMapper mapper, PasswordEncoder encoder, TransactionTemplate tx, AiClient ai, AnalysisService analysis,
                      UserRepository users, SkillRepository skills, SkillPrerequisiteRepository prereqs, CareerGoalRepository goals,
                      CareerGoalSkillRepository goalSkills, LearningResourceRepository resources, TeacherProfileRepository teachers,
                      StudentProfileRepository students, StudentSkillRepository studentSkills, AcademicRecordRepository records,
                      AttendanceRepository attendance, AssignmentRepository assignments, PeerSessionRepository sessions,
                      FeedbackRepository feedback) {
        this.enabled = enabled;
        this.adminPw = adminPw;
        this.teacherPw = teacherPw;
        this.studentPw = studentPw;
        this.mapper = mapper;
        this.encoder = encoder;
        this.tx = tx;
        this.ai = ai;
        this.analysis = analysis;
        this.users = users;
        this.skills = skills;
        this.prereqs = prereqs;
        this.goals = goals;
        this.goalSkills = goalSkills;
        this.resources = resources;
        this.teachers = teachers;
        this.students = students;
        this.studentSkills = studentSkills;
        this.records = records;
        this.attendance = attendance;
        this.assignments = assignments;
        this.sessions = sessions;
        this.feedback = feedback;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!enabled) return;
        if (users.count() > 0) {
            log.info("Database already has users - demo seed skipped");
            return;
        }
        JsonNode root;
        try (InputStream in = new ClassPathResource("seed/seed-data.json").getInputStream()) {
            root = mapper.readTree(in);
        }
        tx.executeWithoutResult(status -> seed(root));
        log.info("Demo data seeded: {} users, {} skills, {} career goals, {} resources",
                users.count(), skills.count(), goals.count(), resources.count());

        // Best effort: pre-compute analysis so dashboards are populated on first login.
        Map<String, Object> health = ai.health();
        if (health != null && Boolean.TRUE.equals(health.get("available"))) {
            int ok = 0;
            for (StudentProfile s : students.findAll()) {
                try {
                    tx.executeWithoutResult(st -> analysis.runFull(students.findById(s.getId()).orElseThrow()));
                    ok++;
                } catch (Exception e) {
                    log.warn("Initial analysis failed for {}: {}", s.getRollNumber(), e.getMessage());
                }
            }
            log.info("Initial AI analysis generated for {} students", ok);
        } else {
            log.warn("AI service not reachable during seeding - students can run analysis later from their dashboard");
        }
    }

    private void seed(JsonNode root) {
        // ---- skills + prerequisites
        Map<String, Skill> skillByName = new HashMap<>();
        for (JsonNode n : root.path("skills")) {
            Skill s = new Skill();
            s.setName(n.path("name").asText());
            s.setCategory(n.path("category").asText(null));
            s.setDescription(n.path("description").asText(null));
            s.setDifficulty(Proficiency.valueOf(n.path("difficulty").asText("BEGINNER")));
            s.setEstimatedHours(n.path("estimated_hours").asDouble(10));
            s.setPracticeTask(n.path("practice_task").asText(null));
            skills.save(s);
            skillByName.put(s.getName().toLowerCase(), s);
        }
        for (JsonNode n : root.path("skills")) {
            Skill s = skillByName.get(n.path("name").asText().toLowerCase());
            for (JsonNode p : n.path("prerequisites")) {
                Skill pre = skillByName.get(p.asText().toLowerCase());
                if (pre == null) continue;
                SkillPrerequisite sp = new SkillPrerequisite();
                sp.setSkill(s);
                sp.setPrerequisite(pre);
                prereqs.save(sp);
            }
        }
        // ---- career goals
        Map<String, CareerGoal> goalByName = new HashMap<>();
        for (JsonNode n : root.path("career_goals")) {
            CareerGoal g = new CareerGoal();
            g.setName(n.path("name").asText());
            g.setDescription(n.path("description").asText(null));
            goals.save(g);
            goalByName.put(g.getName().toLowerCase(), g);
            for (JsonNode gs : n.path("skills")) {
                Skill s = skillByName.get(gs.path("skill").asText().toLowerCase());
                if (s == null) continue;
                CareerGoalSkill c = new CareerGoalSkill();
                c.setCareerGoal(g);
                c.setSkill(s);
                c.setImportance(gs.path("importance").asInt(3));
                c.setRequiredLevel(Proficiency.valueOf(gs.path("required_level").asText("INTERMEDIATE")));
                goalSkills.save(c);
            }
        }
        // ---- learning resources
        for (JsonNode n : root.path("resources")) {
            LearningResource r = new LearningResource();
            r.setTitle(n.path("title").asText());
            r.setDescription(n.path("description").asText(null));
            r.setSkill(skillByName.get(n.path("skill").asText("").toLowerCase()));
            r.setType(ResourceType.valueOf(n.path("type").asText("ARTICLE").toUpperCase()));
            r.setUrl(n.path("url").asText(null));
            r.setDifficulty(Proficiency.valueOf(n.path("difficulty").asText("BEGINNER")));
            r.setEstimatedMinutes(n.path("estimated_minutes").asInt(60));
            resources.save(r);
        }
        // ---- users
        JsonNode a = root.path("admin");
        user(a.path("full_name").asText(), a.path("email").asText(), Role.ADMIN, adminPw);

        Map<String, User> userByEmail = new HashMap<>();
        Map<String, TeacherProfile> teacherByEmail = new HashMap<>();
        for (JsonNode n : root.path("teachers")) {
            User u = user(n.path("full_name").asText(), n.path("email").asText(), Role.TEACHER, teacherPw);
            TeacherProfile t = new TeacherProfile();
            t.setUser(u);
            t.setDepartment(n.path("department").asText(null));
            t.setDesignation(n.path("designation").asText(null));
            t.setMentor(n.path("is_mentor").asBoolean(true));
            t.setAvailability(csv(n.path("availability")));
            t.setExpertise(csv(n.path("expertise")));
            teachers.save(t);
            userByEmail.put(u.getEmail(), u);
            teacherByEmail.put(u.getEmail(), t);
        }

        JsonNode subjects = root.path("subjects");
        String thisYear = yearLabel(0);
        String lastYear = yearLabel(-1);
        for (JsonNode n : root.path("students")) {
            User u = user(n.path("full_name").asText(), n.path("email").asText(), Role.STUDENT, studentPw);
            userByEmail.put(u.getEmail(), u);
            StudentProfile p = new StudentProfile();
            p.setUser(u);
            p.setRollNumber(n.path("roll").asText());
            p.setBranch(n.path("branch").asText(null));
            p.setEducation("B.E. " + n.path("branch").asText(""));
            p.setSemester(n.path("semester").asInt(5));
            p.setCareerGoal(goalByName.get(n.path("goal").asText("").toLowerCase()));
            p.setInterests(csv(n.path("interests")));
            p.setAvailability(csv(n.path("availability")));
            p.setLearningPreferences("Hands-on practice, short videos");
            p.setAssignedTeacher(teacherByEmail.get(n.path("teacher").asText()));
            p.setProgressPercent(0.0);
            p.setAnalysisStale(true);
            students.save(p);
            for (JsonNode sk : n.path("skills")) {
                Skill s = skillByName.get(sk.path("skill").asText().toLowerCase());
                if (s == null) continue;
                StudentSkill ss = new StudentSkill();
                ss.setStudent(p);
                ss.setSkill(s);
                ss.setProficiency(Proficiency.valueOf(sk.path("proficiency").asText("BEGINNER")));
                studentSkills.save(ss);
            }
            academics(p, n.path("perf").asDouble(0.6), subjects.path(p.getBranch()), thisYear, lastYear);
        }

        // ---- sessions + feedback
        LocalDate today = LocalDate.now();
        for (JsonNode n : root.path("sessions")) {
            User req = userByEmail.get(n.path("requester").asText());
            User men = userByEmail.get(n.path("mentor").asText());
            if (req == null || men == null) continue;
            PeerSession ps = new PeerSession();
            ps.setRequester(req);
            ps.setMentor(men);
            ps.setTopic(n.path("topic").asText());
            ps.setSkill(skillByName.get(n.path("skill").asText("").toLowerCase()));
            ps.setSessionDate(today.plusDays(n.path("days_offset").asInt(0)));
            ps.setSessionTime(LocalTime.parse(n.path("time").asText("18:00")));
            ps.setStatus(SessionStatus.valueOf(n.path("status").asText("REQUESTED")));
            ps.setNotes(n.path("notes").asText(null));
            sessions.save(ps);
            for (JsonNode f : n.path("feedback")) {
                User from = userByEmail.get(f.path("by").asText());
                if (from == null) continue;
                Feedback fb = new Feedback();
                fb.setSession(ps);
                fb.setFromUser(from);
                fb.setToUser(from.getId().equals(req.getId()) ? men : req);
                fb.setRating(f.path("rating").asInt(4));
                fb.setHelpfulness(f.path("helpfulness").asInt(4));
                fb.setComment(f.path("comment").asText(null));
                fb.setTopicsCovered(f.path("topics").asText(null));
                fb.setProgressUpdate(f.path("progress").asText(null));
                feedback.save(fb);
            }
        }
        feedback.flush();
        for (TeacherProfile t : teacherByEmail.values()) {
            List<Feedback> fb = feedback.findByToUserId(t.getUser().getId());
            t.setTrustScore(fb.isEmpty() ? null : Math.round(fb.stream().mapToInt(Feedback::getRating).average().orElse(0) / 5.0 * 1000) / 1000.0);
        }
    }

    /** Two semesters of marks, attendance and assignments driven by a performance factor. */
    private void academics(StudentProfile p, double perf, JsonNode subjectList, String thisYear, String lastYear) {
        Random rnd = new Random(p.getRollNumber().hashCode());
        List<String> subjects = new ArrayList<>();
        subjectList.forEach(x -> subjects.add(x.asText()));
        if (subjects.isEmpty()) subjects.addAll(List.of("Mathematics", "Programming", "Communication Skills"));
        int current = p.getSemester() == null ? 5 : p.getSemester();
        // weaker students trend downwards, stronger ones slightly upwards
        double trend = perf < 0.55 ? -6 : (perf > 0.75 ? 3 : 0);
        for (int k = 0; k < 2; k++) {
            int sem = current - 1 + k;
            String year = k == 0 ? lastYear : thisYear;
            for (String subject : subjects) {
                double pct = clamp(perf * 100 + rnd.nextGaussian() * 7 + (k == 1 ? trend : 0), 18, 98);
                double marks = Math.round(pct);
                AcademicRecord r = new AcademicRecord();
                r.setStudent(p);
                r.setSubject(subject);
                r.setMarks(marks);
                r.setMaxMarks(100.0);
                r.setPercentage(marks);
                r.setSemester(sem);
                r.setAcademicYear(year);
                records.save(r);

                int total = 40;
                double attPct = clamp(52 + perf * 45 + rnd.nextGaussian() * 6 + (k == 1 ? trend : 0), 35, 100);
                int attended = (int) Math.round(total * attPct / 100);
                Attendance at = new Attendance();
                at.setStudent(p);
                at.setSubject(subject);
                at.setSemester(sem);
                at.setAcademicYear(year);
                at.setAttendedClasses(attended);
                at.setTotalClasses(total);
                at.setPercentage(Math.round(1000.0 * attended / total) / 10.0);
                attendance.save(at);

                Assignment as = new Assignment();
                as.setStudent(p);
                as.setSubject(subject);
                as.setTitle(subject + " - Assignment " + (k + 1));
                as.setSemester(sem);
                as.setAcademicYear(year);
                as.setMaxScore(20.0);
                as.setDueDate(LocalDate.now().minusDays(k == 1 ? 7 + rnd.nextInt(20) : 150 + rnd.nextInt(30)));
                double roll = rnd.nextDouble();
                if (k == 1 && roll > perf + 0.15) {
                    as.setStatus(perf < 0.5 && roll > 0.85 ? AssignmentStatus.MISSING : AssignmentStatus.PENDING);
                } else {
                    as.setStatus(roll > perf + 0.05 ? AssignmentStatus.LATE : AssignmentStatus.SUBMITTED);
                    as.setScore((double) Math.round(clamp(perf * 20 + rnd.nextGaussian() * 2, 4, 20)));
                }
                assignments.save(as);
            }
        }
    }

    private User user(String name, String email, Role role, String rawPassword) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email.toLowerCase());
        u.setRole(role);
        u.setEnabled(true);
        u.setPasswordHash(encoder.encode(rawPassword));
        return users.save(u);
    }

    private static String csv(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(x -> out.add(x.asText()));
        return String.join(",", out);
    }

    private static String yearLabel(int offset) {
        LocalDate d = LocalDate.now();
        int start = (d.getMonthValue() >= 6 ? d.getYear() : d.getYear() - 1) + offset;
        return start + "-" + String.valueOf(start + 1).substring(2);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
