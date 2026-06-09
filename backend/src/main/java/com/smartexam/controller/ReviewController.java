package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.review.ReviewRequest;
import com.smartexam.service.ReviewService;
import com.smartexam.service.RoleAccessService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final RoleAccessService roleAccessService;

    public ReviewController(ReviewService reviewService, RoleAccessService roleAccessService) {
        this.reviewService = reviewService;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping("/pending")
    public ApiResponse<List<Map<String, Object>>> getPendingReviews() {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(reviewService.getPendingReviews(user));
    }

    @GetMapping("/attempt/{attemptId}")
    public ApiResponse<Map<String, Object>> getReviewDetails(@PathVariable Long attemptId) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(reviewService.getReviewDetails(attemptId, user));
    }

    @PostMapping("/attempt/{attemptId}")
    public ApiResponse<Map<String, Object>> submitReview(@PathVariable Long attemptId, @Valid @RequestBody List<ReviewRequest> reviews) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(reviewService.submitReview(attemptId, reviews, user));
    }
}
