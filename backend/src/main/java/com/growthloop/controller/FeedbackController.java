package com.growthloop.controller;

import com.growthloop.common.ApiResponse;
import com.growthloop.dto.Requests;
import com.growthloop.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import com.growthloop.exception.ApiException;
import com.growthloop.repository.StudentProfileRepository;
import com.growthloop.service.AnalysisService;
import com.growthloop.service.FeedbackService;
import com.growthloop.util.M;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Feedback is saved first (own transaction). Then recommendations are regenerated for the learner
 * in a separate transaction; if the AI service is down the feedback is still kept and the
 * profile stays flagged "stale" so the student can refresh later. No ML retraining happens here.
 */
@RestController
@RequestMapping("/api/feedback")
@Validated
public class FeedbackController {
    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);
    private final FeedbackService feedback;
    private final AnalysisService analysis;
    private final StudentProfileRepository students;
    private final TransactionTemplate tx;

    public FeedbackController(FeedbackService feedback, AnalysisService analysis, StudentProfileRepository students, TransactionTemplate tx) {
        this.feedback = feedback;
        this.analysis = analysis;
        this.students = students;
        this.tx = tx;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> submit(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody Requests.FeedbackCreate r) {
        Map<String, Object> out = feedback.submit(me.getId(), r);
        Long learnerId = (Long) out.get("learnerStudentId");
        boolean regenerated = false;
        String note = null;
        if (learnerId != null) {
            try {
                tx.executeWithoutResult(st -> students.findById(learnerId).ifPresent(analysis::runFull));
                regenerated = true;
            } catch (ApiException e) {
                note = e.getMessage();
                log.warn("Recommendation regeneration after feedback skipped: {}", e.getMessage());
            } catch (Exception e) {
                note = "Recommendations will refresh the next time analysis runs.";
                log.warn("Recommendation regeneration after feedback failed", e);
            }
        }
        Map<String, Object> data = M.of("feedback", out.get("feedback"), "skillUpdated", out.get("skillUpdated"),
                "recommendationsRegenerated", regenerated, "note", note);
        return ApiResponse.ok(regenerated ? "Feedback saved - recommendations updated" : "Feedback saved", data);
    }

    @GetMapping({"", "/me", "/mine"})
    public ApiResponse<Map<String, Object>> mine(@AuthenticationPrincipal AppUserPrincipal me) {
        return ApiResponse.ok(feedback.mine(me.getId()));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> admin(
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 100) String search,
            @RequestParam(required = false) @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5) Integer rating,
            @RequestParam(required = false) @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5) Integer helpfulness,
            @RequestParam(required = false) @jakarta.validation.constraints.Positive Long sessionId,
            @RequestParam(required = false) @jakarta.validation.constraints.Positive Long fromUserId,
            @RequestParam(required = false) @jakarta.validation.constraints.Positive Long toUserId,
            @RequestParam(required = false) @jakarta.validation.constraints.Positive Long participantId) {
        return ApiResponse.ok(feedback.searchAll(search, rating, helpfulness, sessionId, fromUserId, toUserId, participantId));
    }
}
