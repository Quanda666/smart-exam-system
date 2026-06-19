package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.paper.GeneratePaperRequest;
import com.smartexam.dto.paper.PaperRequest;
import com.smartexam.service.OperationLogService;
import com.smartexam.service.PaperService;
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

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/papers")
@RequireRoles({"ADMIN", "TEACHER"})
public class PaperController {

    private final PaperService paperService;
    private final OperationLogService operationLogService;

    public PaperController(PaperService paperService, OperationLogService operationLogService) {
        this.paperService = paperService;
        this.operationLogService = operationLogService;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        AuthUser user = currentUser();
        return ApiResponse.ok(paperService.summary(user));
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> listPapers(@RequestParam(required = false) String keyword,
                                                                    @RequestParam(required = false) Long subjectId,
                                                                    @RequestParam(required = false) Integer status,
                                                                    @RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "10") int size) {
        AuthUser user = currentUser();
        return ApiResponse.ok(paperService.listPapers(keyword, subjectId, status, page, size, user));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getPaper(@PathVariable Long id) {
        AuthUser user = currentUser();
        return ApiResponse.ok(paperService.getPaper(id, user));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createPaper(@Valid @RequestBody PaperRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = paperService.createPaper(request, user);
        return ApiResponse.ok("Paper created", withOperationLogId(user, result,
                "CREATE_PAPER", "PAPER#" + result.get("id"), request.getPaperName()));
    }

    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generatePaper(@Valid @RequestBody GeneratePaperRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = paperService.generatePaper(request, user);
        return ApiResponse.ok("Paper generated", withOperationLogId(user, result,
                "GENERATE_PAPER", "PAPER#" + result.get("id"), request.getPaperName()));
    }

    @PostMapping("/{id}/copy")
    public ApiResponse<Map<String, Object>> copyPaper(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = paperService.copyPaper(id, user);
        return ApiResponse.ok("Paper copied", withOperationLogId(user, result,
                "COPY_PAPER", "PAPER#" + result.get("id"), "sourcePaperId=" + id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updatePaper(@PathVariable Long id, @Valid @RequestBody PaperRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = paperService.updatePaper(id, request, user);
        return ApiResponse.ok("Paper updated", withOperationLogId(user, result,
                "UPDATE_PAPER", "PAPER#" + id, request.getPaperName()));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> request) {
        AuthUser user = currentUser();
        Integer status = request.get("status");
        Map<String, Object> result = paperService.updateStatus(id, status, user);
        return ApiResponse.ok("Paper status updated", withOperationLogId(user, result,
                "UPDATE_PAPER_STATUS", "PAPER#" + id, "status=" + status));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deletePaper(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = paperService.deletePaper(id, user);
        return ApiResponse.ok("Paper deleted", withOperationLogId(user, result,
                "DELETE_PAPER", "PAPER#" + id, null));
    }

    private AuthUser currentUser() {
        return AuthContext.requireSession().getUser();
    }

    private Map<String, Object> withOperationLogId(AuthUser user,
                                                   Map<String, Object> values,
                                                   String action,
                                                   String target,
                                                   String detail) {
        Long operationLogId = operationLogService.record(user.getId(), user.getRealName(), action, target, detail);
        Map<String, Object> result = new LinkedHashMap<>(values);
        result.put("operationLogId", operationLogId);
        return result;
    }
}
