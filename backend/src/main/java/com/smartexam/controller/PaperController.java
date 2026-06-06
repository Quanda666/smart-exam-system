package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.paper.GeneratePaperRequest;
import com.smartexam.dto.paper.PaperRequest;
import com.smartexam.service.PaperService;
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
@RequestMapping("/api/papers")
public class PaperController {

    private final PaperService paperService;
    private final RoleAccessService roleAccessService;

    public PaperController(PaperService paperService, RoleAccessService roleAccessService) {
        this.paperService = paperService;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(paperService.summary());
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listPapers(@RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Long subjectId,
                                                             @RequestParam(required = false) Integer status) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(paperService.listPapers(keyword, subjectId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getPaper(@PathVariable Long id) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(paperService.getPaper(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createPaper(@Valid @RequestBody PaperRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("试卷创建成功", paperService.createPaper(request, user));
    }

    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generatePaper(@Valid @RequestBody GeneratePaperRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("规则组卷成功", paperService.generatePaper(request, user));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updatePaper(@PathVariable Long id, @Valid @RequestBody PaperRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("试卷更新成功", paperService.updatePaper(id, request, user));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("试卷状态更新成功", paperService.updateStatus(id, request.get("status"), user));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deletePaper(@PathVariable Long id) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("试卷删除成功", paperService.deletePaper(id, user));
    }
}
