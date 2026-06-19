package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.question.QuestionRequest;
import com.smartexam.service.QuestionBankService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/questions")
@RequireRoles({"ADMIN", "TEACHER"})
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    public QuestionBankController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        AuthUser user = currentUser();
        return ApiResponse.ok(questionBankService.summary(user));
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> listQuestions(@RequestParam(required = false) String keyword,
                                                                      @RequestParam(required = false) Long subjectId,
                                                                      @RequestParam(required = false) Long knowledgePointId,
                                                                      @RequestParam(required = false) String questionType,
                                                                      @RequestParam(required = false) String difficulty,
                                                                      @RequestParam(required = false) Integer status,
                                                                      @RequestParam(required = false) String reviewStatus,
                                                                      @RequestParam(defaultValue = "1") int page,
                                                                      @RequestParam(defaultValue = "10") int size) {
        AuthUser user = currentUser();
        return ApiResponse.ok(questionBankService.listQuestions(keyword, subjectId, knowledgePointId, questionType, difficulty, status, reviewStatus, page, size, user));
    }

    @GetMapping("/{id}/review-logs")
    public ApiResponse<?> listReviewLogs(@PathVariable Long id) {
        AuthUser user = currentUser();
        return ApiResponse.ok(questionBankService.listReviewLogs(id, user));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createQuestion(@Valid @RequestBody QuestionRequest request) {
        AuthUser user = currentUser();
        return ApiResponse.ok("题目创建成功", questionBankService.createQuestion(request, user));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateQuestion(@PathVariable Long id, @Valid @RequestBody QuestionRequest request) {
        AuthUser user = currentUser();
        return ApiResponse.ok("题目更新成功", questionBankService.updateQuestion(id, request, user));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> request) {
        AuthUser user = currentUser();
        return ApiResponse.ok("题目状态更新成功", questionBankService.updateStatus(id, request.get("status"), user));
    }

    @PostMapping("/{id}/review/submit")
    public ApiResponse<Map<String, Object>> submitReview(@PathVariable Long id) {
        AuthUser user = currentUser();
        return ApiResponse.ok("题目已提交审核", questionBankService.submitReview(id, user));
    }

    @PostMapping("/{id}/review/approve")
    public ApiResponse<Map<String, Object>> approveReview(@PathVariable Long id, @RequestBody(required = false) Map<String, String> request) {
        AuthUser user = currentUser();
        String comment = request == null ? null : request.get("comment");
        return ApiResponse.ok("题目审核通过", questionBankService.approveReview(id, comment, user));
    }

    @PostMapping("/{id}/review/reject")
    public ApiResponse<Map<String, Object>> rejectReview(@PathVariable Long id, @RequestBody(required = false) Map<String, String> request) {
        AuthUser user = currentUser();
        String comment = request == null ? null : request.get("comment");
        return ApiResponse.ok("题目已驳回", questionBankService.rejectReview(id, comment, user));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteQuestion(@PathVariable Long id) {
        AuthUser user = currentUser();
        return ApiResponse.ok("题目删除成功", questionBankService.deleteQuestion(id, user));
    }

    private AuthUser currentUser() {
        return AuthContext.requireSession().getUser();
    }
}
