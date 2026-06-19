package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.dto.ai.AiGeneratedQuestion;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.material.MaterialQuestionFromLibraryRequest;
import com.smartexam.service.MaterialLibraryService;
import com.smartexam.service.OperationLogService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/materials")
@RequireRoles({"ADMIN", "TEACHER"})
public class MaterialLibraryController {

    private final MaterialLibraryService materialLibraryService;
    private final OperationLogService operationLogService;

    public MaterialLibraryController(MaterialLibraryService materialLibraryService,
                                     OperationLogService operationLogService) {
        this.materialLibraryService = materialLibraryService;
        this.operationLogService = operationLogService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@RequestPart("file") MultipartFile file,
                                                   @RequestParam Long subjectId,
                                                   @RequestParam(required = false) String title) {
        AuthUser user = currentUser();
        Map<String, Object> result = materialLibraryService.uploadMaterial(file, subjectId, title, user);
        return ApiResponse.ok("Material uploaded", withOperationLogId(user, result,
                "UPLOAD_MATERIAL", "MATERIAL#" + result.get("id"), materialDetail(result)));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Long subjectId) {
        AuthUser user = currentUser();
        return ApiResponse.ok(materialLibraryService.listMaterials(keyword, subjectId, user));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        AuthUser user = currentUser();
        return ApiResponse.ok(materialLibraryService.getMaterialDetail(id, user));
    }

    @PostMapping("/{id}/questions/generate")
    public ApiResponse<Map<String, Object>> generateQuestions(@PathVariable Long id,
                                                              @Valid @RequestBody MaterialQuestionFromLibraryRequest request) {
        AuthUser user = currentUser();
        List<AiGeneratedQuestion> questions = materialLibraryService.generateQuestions(id, request, user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questions", questions);
        result.put("generatedCount", questions.size());
        return ApiResponse.ok(withOperationLogId(user, result,
                "GENERATE_MATERIAL_QUESTIONS", "MATERIAL#" + id, "generatedCount=" + questions.size()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = materialLibraryService.deleteMaterial(id, user);
        return ApiResponse.ok("Material deleted", withOperationLogId(user, result,
                "DELETE_MATERIAL", "MATERIAL#" + id, materialDetail(result)));
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

    private String materialDetail(Map<String, Object> material) {
        Object title = material.get("title");
        Object fileName = material.get("fileName");
        return "title=" + (title == null ? "" : title)
                + "; fileName=" + (fileName == null ? "" : fileName);
    }
}
