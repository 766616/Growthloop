package com.growthloop.service;

import com.growthloop.dto.Requests;
import com.growthloop.entity.*;
import com.growthloop.entity.enums.AssignmentStatus;
import com.growthloop.exception.ApiException;
import com.growthloop.repository.*;
import com.growthloop.util.M;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AcademicService {
    public static final List<String> REQUIRED_COLUMNS =
            List.of("student_id", "subject", "marks", "max_marks", "attendance", "assignment_score");
    public static final List<String> OPTIONAL_COLUMNS = List.of("semester", "academic_year", "assignment_status");

    private final StudentProfileRepository students;
    private final AcademicRecordRepository records;
    private final AttendanceRepository attendance;
    private final AssignmentRepository assignments;
    private final NotificationService notifications;
    private final Views views;

    public AcademicService(StudentProfileRepository students, AcademicRecordRepository records, AttendanceRepository attendance,
                           AssignmentRepository assignments, NotificationService notifications, Views views) {
        this.students = students;
        this.records = records;
        this.attendance = attendance;
        this.assignments = assignments;
        this.notifications = notifications;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> forStudent(Long studentProfileId) {
        List<AcademicRecord> rs = records.findByStudentIdOrderBySemesterAscSubjectAsc(studentProfileId);
        List<Attendance> as = attendance.findByStudentId(studentProfileId);
        List<Assignment> asg = assignments.findByStudentIdOrderByDueDateDesc(studentProfileId);
        return M.of("records", rs.stream().map(views::academicRecord).toList(),
                "attendance", as.stream().map(views::attendance).toList(),
                "assignments", asg.stream().map(views::assignment).toList(),
                "summary", summary(rs, as, asg));
    }

    public Map<String, Object> summary(List<AcademicRecord> rs, List<Attendance> as, List<Assignment> asg) {
        double avg = rs.stream().mapToDouble(AcademicRecord::getPercentage).average().orElse(0);
        double att = as.stream().mapToDouble(Attendance::getPercentage).average().orElse(0);
        long pending = asg.stream().filter(a -> a.getStatus() == AssignmentStatus.PENDING || a.getStatus() == AssignmentStatus.MISSING).count();
        Map<Integer, Double> bySem = rs.stream().filter(r -> r.getSemester() != null).collect(Collectors.groupingBy(
                AcademicRecord::getSemester, TreeMap::new, Collectors.averagingDouble(AcademicRecord::getPercentage)));
        List<Map<String, Object>> trend = bySem.entrySet().stream()
                .map(e -> M.of("semester", "Sem " + e.getKey(), "average", round(e.getValue()))).toList();
        return M.of("averagePercentage", round(avg), "averageAttendance", round(att), "pendingAssignments", pending,
                "totalSubjects", rs.stream().map(AcademicRecord::getSubject).distinct().count(),
                "hasData", !rs.isEmpty() || !as.isEmpty(), "semesterTrend", trend);
    }

    @Transactional
    public Map<String, Object> addEntry(Requests.AcademicEntry r) {
        if (r.marks() > r.maxMarks()) throw ApiException.badRequest("Marks cannot be greater than max marks");
        StudentProfile s = students.findById(r.studentId()).orElseThrow(() -> ApiException.notFound("Student"));
        String year = r.academicYear() == null || r.academicYear().isBlank() ? defaultYear() : r.academicYear().trim();
        Integer sem = r.semester() != null ? r.semester() : s.getSemester();
        AcademicRecord rec = upsertRecord(s, r.subject().trim(), r.marks(), r.maxMarks(), sem, year);

        if (r.attendancePercent() != null || (r.attendedClasses() != null && r.totalClasses() != null)) {
            Double pct = r.attendancePercent();
            if (pct == null) {
                if (r.totalClasses() == 0) throw ApiException.badRequest("Total classes must be greater than 0");
                if (r.attendedClasses() > r.totalClasses()) throw ApiException.badRequest("Attended classes cannot exceed total classes");
                pct = 100.0 * r.attendedClasses() / r.totalClasses();
            }
            upsertAttendance(s, r.subject().trim(), sem, year, pct, r.attendedClasses(), r.totalClasses());
        }
        if (r.assignmentStatus() != null || r.assignmentScore() != null) {
            if (r.assignmentScore() != null && r.assignmentMaxScore() != null && r.assignmentScore() > r.assignmentMaxScore()) {
                throw ApiException.badRequest("Assignment score cannot exceed its max score");
            }
            Assignment a = new Assignment();
            a.setStudent(s);
            a.setSubject(r.subject().trim());
            a.setTitle(r.assignmentTitle() == null || r.assignmentTitle().isBlank() ? r.subject().trim() + " assignment" : r.assignmentTitle().trim());
            a.setStatus(r.assignmentStatus() == null ? AssignmentStatus.SUBMITTED : r.assignmentStatus());
            a.setScore(r.assignmentScore());
            a.setMaxScore(r.assignmentMaxScore() == null ? 100.0 : r.assignmentMaxScore());
            a.setDueDate(r.assignmentDueDate() == null ? LocalDate.now() : r.assignmentDueDate());
            a.setSemester(sem);
            a.setAcademicYear(year);
            assignments.save(a);
        }
        s.setAnalysisStale(true);
        notifications.notify(s.getUser(), "New academic data", "Academic records for " + r.subject().trim() + " were updated. Re-run your analysis to refresh recommendations.");
        return views.academicRecord(rec);
    }

    @Transactional
    public void deleteRecord(Long id) {
        AcademicRecord r = records.findById(id).orElseThrow(() -> ApiException.notFound("Academic record"));
        r.getStudent().setAnalysisStale(true);
        records.delete(r);
    }

    /** CSV import: every row is validated; valid rows are saved, invalid rows are reported with reasons. */
    @Transactional
    public Map<String, Object> importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Please upload a non-empty CSV file");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        if (!name.endsWith(".csv")) throw ApiException.badRequest("Only .csv files are accepted");

        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) rows.add(parseLine(line));
            }
        } catch (Exception e) {
            throw ApiException.badRequest("Could not read the CSV file");
        }
        if (rows.isEmpty()) throw ApiException.badRequest("The CSV file is empty");

        List<String> header = rows.get(0).stream().map(h -> h.trim().toLowerCase().replace("\uFEFF", "")).toList();
        List<String> missingCols = REQUIRED_COLUMNS.stream().filter(c -> !header.contains(c)).toList();
        List<String> unknownCols = header.stream().filter(c -> !REQUIRED_COLUMNS.contains(c) && !OPTIONAL_COLUMNS.contains(c)).toList();
        if (!missingCols.isEmpty() || !unknownCols.isEmpty()) {
            List<String> errs = new ArrayList<>();
            if (!missingCols.isEmpty()) errs.add("Missing columns: " + String.join(", ", missingCols));
            if (!unknownCols.isEmpty()) errs.add("Unknown columns: " + String.join(", ", unknownCols));
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Incorrect columns. Expected: " + String.join(",", REQUIRED_COLUMNS) + " (optional: " + String.join(",", OPTIONAL_COLUMNS) + "). " + String.join("; ", errs));
        }
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < header.size(); i++) idx.put(header.get(i), i);

        List<Map<String, Object>> failed = new ArrayList<>();
        List<Map<String, Object>> succeeded = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        Set<StudentProfile> touched = new HashSet<>();

        for (int r = 1; r < rows.size(); r++) {
            int rowNum = r + 1;
            List<String> row = rows.get(r);
            List<String> errs = new ArrayList<>();
            if (row.size() != header.size()) {
                failed.add(M.of("row", rowNum, "studentId", row.isEmpty() ? "" : row.get(0),
                        "errors", List.of("Expected " + header.size() + " values but found " + row.size())));
                continue;
            }
            String sid = row.get(idx.get("student_id")).trim();
            String subject = row.get(idx.get("subject")).trim();
            Double marks = num(row.get(idx.get("marks")), "marks", errs);
            Double max = num(row.get(idx.get("max_marks")), "max_marks", errs);
            Double att = num(row.get(idx.get("attendance")), "attendance", errs);
            Double asg = num(row.get(idx.get("assignment_score")), "assignment_score", errs);
            Integer sem = null;
            if (idx.containsKey("semester") && !row.get(idx.get("semester")).isBlank()) {
                Double d = num(row.get(idx.get("semester")), "semester", errs);
                if (d != null) {
                    if (d < 1 || d > 12 || d % 1 != 0) errs.add("semester must be a whole number 1-12");
                    else sem = d.intValue();
                }
            }
            String year = idx.containsKey("academic_year") && !row.get(idx.get("academic_year")).isBlank()
                    ? row.get(idx.get("academic_year")).trim() : defaultYear();
            AssignmentStatus status = AssignmentStatus.SUBMITTED;
            if (idx.containsKey("assignment_status") && !row.get(idx.get("assignment_status")).isBlank()) {
                try {
                    status = AssignmentStatus.valueOf(row.get(idx.get("assignment_status")).trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    errs.add("assignment_status must be one of SUBMITTED, PENDING, LATE, MISSING");
                }
            }
            if (sid.isEmpty()) errs.add("student_id is required");
            if (subject.isEmpty()) errs.add("subject is required");
            if (marks != null && marks < 0) errs.add("marks cannot be negative");
            if (max != null && max <= 0) errs.add("max_marks must be greater than 0");
            if (marks != null && max != null && marks > max) errs.add("marks (" + marks + ") exceed max_marks (" + max + ")");
            if (att != null && (att < 0 || att > 100)) errs.add("attendance must be between 0 and 100");
            if (asg != null && (asg < 0 || asg > 100)) errs.add("assignment_score must be between 0 and 100");

            StudentProfile s = null;
            if (!sid.isEmpty()) {
                s = students.findByRollNumberIgnoreCase(sid).orElse(null);
                if (s == null) errs.add("Student '" + sid + "' does not exist");
            }
            if (s != null) {
                Integer semFinal = sem != null ? sem : s.getSemester();
                String key = s.getId() + "|" + subject.toLowerCase() + "|" + semFinal + "|" + year;
                if (!seenInFile.add(key)) errs.add("Duplicate row in this file for " + sid + " / " + subject);
                else if (records.existsByStudentIdAndSubjectIgnoreCaseAndSemesterAndAcademicYear(s.getId(), subject, semFinal, year)) {
                    errs.add("Record already exists for " + sid + " / " + subject + " / sem " + semFinal + " / " + year);
                }
                sem = semFinal;
            }
            if (!errs.isEmpty()) {
                failed.add(M.of("row", rowNum, "studentId", sid, "subject", subject, "errors", errs));
                continue;
            }
            upsertRecord(s, subject, marks, max, sem, year);
            upsertAttendance(s, subject, sem, year, att, null, null);
            Assignment a = new Assignment();
            a.setStudent(s);
            a.setSubject(subject);
            a.setTitle(subject + " assignment (CSV import)");
            a.setStatus(status);
            a.setScore(status == AssignmentStatus.PENDING || status == AssignmentStatus.MISSING ? null : asg);
            a.setMaxScore(100.0);
            a.setDueDate(LocalDate.now());
            a.setSemester(sem);
            a.setAcademicYear(year);
            assignments.save(a);
            touched.add(s);
            succeeded.add(M.of("row", rowNum, "studentId", sid, "subject", subject));
        }
        for (StudentProfile s : touched) {
            s.setAnalysisStale(true);
            notifications.notify(s.getUser(), "Academic records imported", "New marks and attendance were added to your profile.");
        }
        return M.of("totalRows", rows.size() - 1, "successCount", succeeded.size(), "failedCount", failed.size(),
                "succeeded", succeeded, "failed", failed);
    }

    private AcademicRecord upsertRecord(StudentProfile s, String subject, double marks, double max, Integer sem, String year) {
        AcademicRecord rec = records.findByStudentIdOrderBySemesterAscSubjectAsc(s.getId()).stream()
                .filter(x -> x.getSubject().equalsIgnoreCase(subject) && Objects.equals(x.getSemester(), sem) && Objects.equals(x.getAcademicYear(), year))
                .findFirst().orElseGet(AcademicRecord::new);
        rec.setStudent(s);
        rec.setSubject(subject);
        rec.setMarks(marks);
        rec.setMaxMarks(max);
        rec.setPercentage(round(100.0 * marks / max));
        rec.setSemester(sem);
        rec.setAcademicYear(year);
        return records.save(rec);
    }

    private void upsertAttendance(StudentProfile s, String subject, Integer sem, String year, Double pct, Integer attended, Integer total) {
        if (pct == null) return;
        Attendance a = attendance.findFirstByStudentIdAndSubjectIgnoreCaseAndSemesterAndAcademicYear(s.getId(), subject, sem, year)
                .orElseGet(Attendance::new);
        a.setStudent(s);
        a.setSubject(subject);
        a.setSemester(sem);
        a.setAcademicYear(year);
        a.setPercentage(round(pct));
        a.setAttendedClasses(attended);
        a.setTotalClasses(total);
        attendance.save(a);
    }

    private static Double num(String raw, String field, List<String> errs) {
        if (raw == null || raw.isBlank()) {
            errs.add(field + " is required");
            return null;
        }
        try {
            double d = Double.parseDouble(raw.trim());
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new NumberFormatException();
            return d;
        } catch (NumberFormatException e) {
            errs.add(field + " '" + raw.trim() + "' is not a valid number");
            return null;
        }
    }

    /** Minimal RFC-4180 style parser: supports quoted values with commas and doubled quotes. */
    static List<String> parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cur.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    static String defaultYear() {
        LocalDate d = LocalDate.now();
        int start = d.getMonthValue() >= 6 ? d.getYear() : d.getYear() - 1;
        return start + "-" + String.valueOf(start + 1).substring(2);
    }

    static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
