package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.dto.ai.AiGeneratedQuestion;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.material.MaterialQuestionFromLibraryRequest;
import com.smartexam.service.MaterialLibraryService;
import com.smartexam.service.RoleAccessService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/materials")
public class MaterialLibraryController {

    private final MaterialLibraryService materialLibraryService;
    private final RoleAccessService roleAccessService;

    public MaterialLibraryController(MaterialLibraryService materialLibraryService,
                                     RoleAccessService roleAccessService) {
        this.materialLibraryService = materialLibraryService;
        this.roleAccessService = roleAccessService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@RequestPart("file") MultipartFile file,
                                                   @RequestParam Long subjectId,
                                                   @RequestParam(required = false) String title) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("资料已上传并生成知识点大纲", materialLibraryService.uploadMaterial(file, subjectId, title, user));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Long subjectId) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(materialLibraryService.listMaterials(keyword, subjectId, user));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(materialLibraryService.getMaterialDetail(id, user));
    }

    @PostMapping("/{id}/questions/generate")
    public ApiResponse<List<AiGeneratedQuestion>> generateQuestions(@PathVariable Long id,
                                                                    @Valid @RequestBody MaterialQuestionFromLibraryRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(materialLibraryService.generateQuestions(id, request, user));
    }
}
