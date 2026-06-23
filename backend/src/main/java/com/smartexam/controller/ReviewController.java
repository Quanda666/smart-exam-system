package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.common.ExportFile;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.review.ScoreAppealRecheckCloseRequest;
import com.smartexam.dto.review.ReviewRequest;
import com.smartexam.dto.review.ScoreAppealReplyRequest;
import com.smartexam.service.ReviewService;
import com.smartexam.service.ScoreAppealService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@RequireRoles({"ADMIN", "TEACHER"})
public class ReviewController {

    private final ReviewService reviewService;
    private final ScoreAppealService scoreAppealService;

    public ReviewController(ReviewService reviewService, ScoreAppealService scoreAppealService) {
        this.reviewService = reviewService;
        this.scoreAppealService = scoreAppealService;
    }

    @GetMapping("/pending")
    public ApiResponse<List<Map<String, Object>>> getPendingReviews(@RequestParam(required = false) Long examId,
                                                                    @RequestParam(required = false) String reviewType) {
        AuthUser user = currentUser();
        return ApiResponse.ok(reviewService.getPendingReviews(examId, reviewType, user));
    }

    @GetMapping("/progress")
    public ApiResponse<List<Map<String, Object>>> listReviewProgress(@RequestParam(required = false) Long examId) {
        AuthUser user = currentUser();
        return ApiResponse.ok(reviewService.listReviewProgress(examId, user));
    }

    @GetMapping("/attempt/{attemptId}")
    public ApiResponse<Map<String, Object>> getReviewDetails(@PathVariable Long attemptId) {
        AuthUser user = currentUser();
        return ApiResponse.ok(reviewService.getReviewDetails(attemptId, user));
    }

    @PostMapping("/attempt/{attemptId}")
    public ApiResponse<Map<String, Object>> submitReview(@PathVariable Long attemptId, @Valid @RequestBody List<ReviewRequest> reviews) {
        AuthUser user = currentUser();
        return ApiResponse.ok(reviewService.submitReview(attemptId, reviews, user));
    }

    @GetMapping("/attempt/{attemptId}/score-logs")
    public ApiResponse<List<Map<String, Object>>> listReviewScoreLogs(@PathVariable Long attemptId) {
        AuthUser user = currentUser();
        return ApiResponse.ok(reviewService.listReviewScoreLogs(attemptId, user));
    }

    @GetMapping("/attempt/{attemptId}/score-logs/export")
    public ResponseEntity<byte[]> exportReviewScoreLogs(@PathVariable Long attemptId) {
        AuthUser user = currentUser();
        ExportFile file = reviewService.exportReviewScoreLogs(attemptId, user);
        return file.toDownload();
    }

    @GetMapping("/appeals")
    public ApiResponse<List<Map<String, Object>>> listAppeals(@RequestParam(required = false) Integer status,
                                                              @RequestParam(required = false) String handlingResult,
                                                              @RequestParam(required = false) Long appealId,
                                                              @RequestParam(required = false) Long examId) {
        AuthUser user = currentUser();
        if (examId != null) {
            return ApiResponse.ok(scoreAppealService.listAppeals(status, handlingResult, appealId, examId, user));
        }
        return ApiResponse.ok(scoreAppealService.listAppeals(status, handlingResult, appealId, user));
    }

    @GetMapping("/appeals/{id}/logs")
    public ApiResponse<List<Map<String, Object>>> listAppealLogs(@PathVariable Long id) {
        AuthUser user = currentUser();
        return ApiResponse.ok(scoreAppealService.listAppealLogs(id, user));
    }

    @GetMapping("/appeals/{id}/logs/export")
    public ResponseEntity<byte[]> exportAppealLogs(@PathVariable Long id) {
        AuthUser user = currentUser();
        ExportFile file = scoreAppealService.exportAppealLogs(id, user);
        return file.toDownload();
    }

    @GetMapping("/appeals/{id}/recheck/readiness")
    public ApiResponse<Map<String, Object>> getAppealRecheckReadiness(@PathVariable Long id) {
        AuthUser user = currentUser();
        return ApiResponse.ok(scoreAppealService.recheckReadiness(id, user));
    }

    @PostMapping("/appeals/{id}/reply")
    public ApiResponse<Map<String, Object>> replyAppeal(@PathVariable Long id,
                                                        @Valid @RequestBody ScoreAppealReplyRequest request) {
        AuthUser user = currentUser();
        return ApiResponse.ok("申诉已处理", scoreAppealService.replyAppeal(id, request, user));
    }

    @PostMapping("/appeals/{id}/recheck/close")
    public ApiResponse<Map<String, Object>> closeAppealRecheck(@PathVariable Long id,
                                                               @Valid @RequestBody ScoreAppealRecheckCloseRequest request) {
        AuthUser user = currentUser();
        return ApiResponse.ok("复核已完成", scoreAppealService.closeRecheck(id, request, user));
    }

    private AuthUser currentUser() {
        return AuthContext.requireSession().getUser();
    }
}
