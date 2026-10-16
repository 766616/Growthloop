package com.growthloop.service;

import com.growthloop.entity.*;
import com.growthloop.entity.enums.*;
import com.growthloop.repository.*;
import com.growthloop.util.JsonUtil;
import com.growthloop.util.M;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Aggregations for the three dashboards. All numbers come from the database. */
@Service
public class DashboardService {
    private final AccessService access;
    private final AnalysisService analysis;
    private final AcademicService academics;
    private final Views views;
    private final JsonUtil json;
    private final StudentProfileRepository students;
    private final UserRepository users;
    private final TeacherProfileRepository teachers;
    private final AcademicRecordRepository records;
    private final AttendanceRepository attendance;
    private final AssignmentRepository assignments;
    private final RiskAssessmentRepository risks;
    private final SkillGapResultRepository gaps;
    private final PeerSessionRepository sessions;
    private final FeedbackRepository feedback;
    private final NotificationRepository notifications;
    private final LearningResourceRepository resources;

    public DashboardService(AccessService access, AnalysisService analysis, AcademicService academics, Views views, JsonUtil json,
                            StudentProfileRepository students, UserRepository users, TeacherProfileRepository teachers,
                            AcademicRecordRepository records, AttendanceRepository attendance, AssignmentRepository assignments,
                            RiskAssessmentRepository risks, SkillGapResultRepository gaps, PeerSessionRepository sessions,
                            FeedbackRepository feedback, NotificationRepository notifications, LearningResourceRepository resources) {
        this.access = access;
        this.analysis = analysis;
        this.academics = academics;
        this.views = views;
        this.json = json;
        this.students = students;
        this.users = users;
        this.teachers = teachers;
        this.records = records;
        this.attendance = attendance;
        this.assignments = assignments;
        this.risks = risks;
        this.gaps = gaps;
        this.sessions = sessions;
        this.feedback = feedback;
        this.notifications = notifications;
        this.resources = resources;
    }

    // ---------------------------------------------------------------- student
    @Transactional(readOnly = true)
    public Map<String, Object> student(Long userId) {
        StudentProfile s = access.studentOfUser(userId);
        List<AcademicRecord> rs = records.findByStudentIdOrderBySemesterAscSubjectAsc(s.getId());
        List<Attendance> as = attendance.findByStudentId(s.getId());
        List<Assignment> asg = assignments.findByStudentIdOrderByDueDateDesc(s.getId());
        Map<String, Object> latest = analysis.latest(s);
        @SuppressWarnings("unchecked")
        Map<String, Object> roadmap = (Map<String, Object>) latest.get("roadmap");

        Map<String, Double> attBySubject = as.stream().collect(Collectors.groupingBy(Attendance::getSubject, Collectors.averagingDouble(Attendance::getPercentage)));
        Integer latestSem = rs.stream().map(AcademicRecord::getSemester).filter(Objects::nonNull).max(Integer::compare).orElse(null);
        List<Map<String, Object>> subjectPerf = rs.stream().filter(r -> Objects.equals(r.getSemester(), latestSem))
                .map(r -> M.of("subject", r.getSubject(), "percentage", r.getPercentage(),
                        "attendance", attBySubject.get(r.getSubject()))).toList();

        Map<String, Object> nextItem = null;
        List<Map<String, Object>> nextResources = List.of();
        if (roadmap != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) roadmap.get("items");
            nextItem = items.stream().filter(i -> i.get("status") != RoadmapStatus.COMPLETED && Boolean.TRUE.equals(i.get("readyNow")))
                    .findFirst().orElse(items.stream().filter(i -> i.get("status") != RoadmapStatus.COMPLETED).findFirst().orElse(null));
            if (nextItem != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> r = (List<Map<String, Object>>) nextItem.get("resources");
                nextResources = r;
            }
        }
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> upcoming = sessions.findByRequesterIdOrMentorIdOrderBySessionDateDescIdDesc(userId, userId).stream()
                .filter(ps -> ps.getStatus() == SessionStatus.ACCEPTED || ps.getStatus() == SessionStatus.SCHEDULED || ps.getStatus() == SessionStatus.REQUESTED)
                .filter(ps -> ps.getSessionDate() == null || !ps.getSessionDate().isBefore(today))
                .sorted(Comparator.comparing(PeerSession::getSessionDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5).map(views::session).toList();
        List<Map<String, Object>> recentFeedback = feedback.findByFromUserIdOrToUserIdOrderByCreatedAtDesc(userId, userId).stream()
                .limit(3).map(views::feedback).toList();
        List<Map<String, Object>> riskHistory = risks.findByStudentIdOrderByCreatedAtAsc(s.getId()).stream()
                .map(r -> M.of("date", r.getCreatedAt(), "level", r.getRiskLevel(), "probability", r.getProbability())).toList();

        return M.of("student", M.of("id", s.getId(), "fullName", s.getUser().getFullName(), "rollNumber", s.getRollNumber(),
                        "branch", s.getBranch(), "semester", s.getSemester()),
                "careerGoal", views.careerGoalRef(s.getCareerGoal()),
                "profileComplete", s.getCareerGoal() != null && !views.studentSkills(s.getId()).isEmpty(),
                "progressPercent", s.getProgressPercent() == null ? 0 : s.getProgressPercent(),
                "academicSummary", academics.summary(rs, as, asg),
                "subjectPerformance", subjectPerf,
                "skills", views.studentSkills(s.getId()),
                "risk", latest.get("risk"), "riskHistory", riskHistory,
                "skillGap", latest.get("skillGap"),
                "roadmap", roadmap,
                "nextAction", roadmap == null ? null : roadmap.get("nextAction"),
                "nextItem", nextItem, "nextResources", nextResources,
                "recommendations", latest.get("recommendations"),
                "matches", ((List<?>) latest.get("matches")).stream().limit(4).toList(),
                "upcomingSessions", upcoming, "recentFeedback", recentFeedback,
                "unreadNotifications", notifications.countByUserIdAndReadFalse(userId),
                "lastAnalyzedAt", s.getLastAnalyzedAt(), "stale", latest.get("stale"));
    }

    // ---------------------------------------------------------------- teacher
    @Transactional(readOnly = true)
    public Map<String, Object> teacher(Long userId) {
        List<StudentProfile> mine = access.studentsForTeacher(userId);
        Map<String, Object> cls = classAnalytics(mine);
        List<PeerSession> my = sessions.findByRequesterIdOrMentorIdOrderBySessionDateDescIdDesc(userId, userId);
        LocalDate today = LocalDate.now();
        cls.put("pendingRequests", my.stream().filter(ps -> ps.getStatus() == SessionStatus.REQUESTED && ps.getMentor().getId().equals(userId))
                .map(views::session).toList());
        cls.put("upcomingSessions", my.stream().filter(ps -> (ps.getStatus() == SessionStatus.ACCEPTED || ps.getStatus() == SessionStatus.SCHEDULED)
                        && (ps.getSessionDate() == null || !ps.getSessionDate().isBefore(today)))
                .sorted(Comparator.comparing(PeerSession::getSessionDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(views::session).toList());
        List<Feedback> fb = feedback.findByToUserId(userId);
        cls.put("myAverageRating", fb.isEmpty() ? null : Math.round(fb.stream().mapToInt(Feedback::getRating).average().orElse(0) * 10) / 10.0);
        cls.put("recentFeedback", fb.stream().sorted(Comparator.comparing(Feedback::getCreatedAt).reversed()).limit(5).map(views::feedback).toList());
        return cls;
    }

    /** Class-level analytics for a set of students (teacher view or whole platform). */
    public Map<String, Object> classAnalytics(List<StudentProfile> list) {
        List<Map<String, Object>> progress = new ArrayList<>();
        List<Map<String, Object>> needSupport = new ArrayList<>();
        Map<String, List<Double>> subj = new TreeMap<>();
        Map<Integer, List<Double>> attBySem = new TreeMap<>();
        Map<String, Integer> gapCount = new HashMap<>();
        Map<String, Integer> riskDist = new LinkedHashMap<>();
        for (RiskLevel l : RiskLevel.values()) riskDist.put(l.name(), 0);
        double sumPerf = 0;
        int nPerf = 0;
        for (StudentProfile s : list) {
            List<AcademicRecord> rs = records.findByStudentIdOrderBySemesterAscSubjectAsc(s.getId());
            rs.forEach(r -> subj.computeIfAbsent(r.getSubject(), k -> new ArrayList<>()).add(r.getPercentage()));
            attendance.findByStudentId(s.getId()).forEach(a -> attBySem.computeIfAbsent(a.getSemester() == null ? 0 : a.getSemester(), k -> new ArrayList<>()).add(a.getPercentage()));
            double avg = rs.stream().mapToDouble(AcademicRecord::getPercentage).average().orElse(Double.NaN);
            if (!Double.isNaN(avg)) { sumPerf += avg; nPerf++; }
            Optional<RiskAssessment> risk = risks.findFirstByStudentIdOrderByCreatedAtDescIdDesc(s.getId());
            RiskLevel level = risk.map(RiskAssessment::getRiskLevel).orElse(RiskLevel.UNKNOWN);
            riskDist.merge(level.name(), 1, Integer::sum);
            gaps.findFirstByStudentIdOrderByCreatedAtDescIdDesc(s.getId()).ifPresent(g -> {
                for (Object o : json.readList(g.getMissingJson())) {
                    if (o instanceof Map<?, ?> m && m.get("skill") != null) gapCount.merge(String.valueOf(m.get("skill")), 1, Integer::sum);
                }
            });
            Map<String, Object> row = M.of("id", s.getId(), "fullName", s.getUser().getFullName(), "rollNumber", s.getRollNumber(),
                    "careerGoal", s.getCareerGoal() == null ? null : s.getCareerGoal().getName(),
                    "averagePercentage", Double.isNaN(avg) ? null : AcademicService.round(avg),
                    "progressPercent", s.getProgressPercent() == null ? 0 : s.getProgressPercent(),
                    "riskLevel", level, "riskProbability", risk.map(RiskAssessment::getProbability).orElse(null),
                    "indicators", risk.map(r -> json.readList(r.getIndicatorsJson())).orElse(List.of()));
            progress.add(row);
            if (level == RiskLevel.MEDIUM || level == RiskLevel.HIGH) needSupport.add(row);
        }
        needSupport.sort((a, b) -> Double.compare(num(b.get("riskProbability")), num(a.get("riskProbability"))));
        return M.of("totalStudents", list.size(),
                "averagePerformance", nPerf == 0 ? null : AcademicService.round(sumPerf / nPerf),
                "studentsNeedingSupport", needSupport,
                "riskDistribution", riskDist.entrySet().stream().map(e -> M.of("level", e.getKey(), "count", e.getValue())).toList(),
                "subjectPerformance", subj.entrySet().stream().map(e -> M.of("subject", e.getKey(),
                        "average", AcademicService.round(e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0)))).toList(),
                "attendanceTrend", attBySem.entrySet().stream().map(e -> M.of("semester", e.getKey() == 0 ? "N/A" : "Sem " + e.getKey(),
                        "average", AcademicService.round(e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0)))).toList(),
                "skillGapTrends", top(gapCount, 8, "skill"),
                "studentProgress", progress);
    }

    // ---------------------------------------------------------------- admin
    @Transactional(readOnly = true)
    public Map<String, Object> admin() {
        List<StudentProfile> all = students.findAll();
        Map<String, Object> cls = classAnalytics(all);
        Map<String, Integer> requested = new HashMap<>();
        List<PeerSession> allSessions = sessions.findAll();
        for (PeerSession ps : allSessions) {
            requested.merge(ps.getSkill() != null ? ps.getSkill().getName() : ps.getTopic(), 1, Integer::sum);
        }
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (SessionStatus st : SessionStatus.values()) byStatus.put(st.name(), 0L);
        allSessions.forEach(ps -> byStatus.merge(ps.getStatus().name(), 1L, Long::sum));
        cls.put("totalTeachers", users.countByRole(Role.TEACHER));
        cls.put("totalMentors", teachers.findByMentorTrue().size());
        cls.put("totalAdmins", users.countByRole(Role.ADMIN));
        cls.put("totalSessions", allSessions.size());
        cls.put("completedSessions", sessions.countByStatus(SessionStatus.COMPLETED));
        cls.put("totalFeedback", feedback.count());
        cls.put("totalResources", resources.count());
        cls.put("mostRequestedSkills", top(requested, 8, "skill"));
        cls.put("sessionsByStatus", byStatus.entrySet().stream().map(e -> M.of("status", e.getKey(), "count", e.getValue())).toList());
        cls.put("recentUsers", users.findAllByOrderByCreatedAtDesc().stream().limit(6).map(views::user).toList());
        cls.put("recentSessions", allSessions.stream().sorted(Comparator.comparing(PeerSession::getCreatedAt).reversed()).limit(6).map(views::session).toList());
        return cls;
    }

    private static List<Map<String, Object>> top(Map<String, Integer> counts, int n, String key) {
        return counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(n)
                .map(e -> M.of(key, e.getKey(), "count", e.getValue())).toList();
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
