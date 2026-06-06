package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.question.QuestionRequest;
import com.smartexam.service.QuestionBankService;
import com.smartexam.service.RoleAccessService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/questions")
public class QuestionBankController {

    private final QuestionBankService questionBankService;
    private final RoleAccessService roleAccessService;

    public QuestionBankController(QuestionBankService questionBankService, RoleAccessService roleAccessService) {
        this.questionBankService = questionBankService;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(questionBankService.summary());
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listQuestions(@RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) Long subjectId,
                                                                @RequestParam(required = false) Long knowledgePointId,
                                                                @RequestParam(required = false) String questionType,
                                                                @RequestParam(required = false) String difficulty,
                                                                @RequestParam(required = false) Integer status) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(questionBankService.listQuestions(keyword, subjectId, knowledgePointId, questionType, difficulty, status));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createQuestion(@Valid @RequestBody QuestionRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("题目创建成功", questionBankService.createQuestion(request, user));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateQuestion(@PathVariable Long id, @Valid @RequestBody QuestionRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("题目更新成功", questionBankService.updateQuestion(id, request, user));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("题目状态更新成功", questionBankService.updateStatus(id, request.get("status"), user));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteQuestion(@PathVariable Long id) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("题目删除成功", questionBankService.deleteQuestion(id, user));
    }

    @GetMapping("/student-deny-check")
    public ApiResponse<Map<String, Object>> studentDenyCheck() {
        AuthContext.requireSession();
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(Map.of("accessible", true));
    }
}
