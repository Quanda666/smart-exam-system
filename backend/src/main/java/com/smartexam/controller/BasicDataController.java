package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.basic.ClassInfoRequest;
import com.smartexam.dto.basic.KnowledgePointRequest;
import com.smartexam.dto.basic.NoticeRequest;
import com.smartexam.dto.basic.SubjectRequest;
import com.smartexam.service.BasicDataService;
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
@RequestMapping("/api/basic")
public class BasicDataController {

    private final BasicDataService basicDataService;
    private final RoleAccessService roleAccessService;

    public BasicDataController(BasicDataService basicDataService, RoleAccessService roleAccessService) {
        this.basicDataService = basicDataService;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> listClasses(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Integer status) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(basicDataService.listClasses(keyword, status));
    }

    @PostMapping("/classes")
    public ApiResponse<Map<String, Object>> createClass(@Valid @RequestBody ClassInfoRequest request) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("班级创建成功", basicDataService.createClass(request));
    }

    @PutMapping("/classes/{id}")
    public ApiResponse<Map<String, Object>> updateClass(@PathVariable Long id, @Valid @RequestBody ClassInfoRequest request) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("班级更新成功", basicDataService.updateClass(id, request));
    }

    @DeleteMapping("/classes/{id}")
    public ApiResponse<Map<String, Object>> deleteClass(@PathVariable Long id) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("班级删除成功", basicDataService.deleteClass(id));
    }

    @GetMapping("/subjects")
    public ApiResponse<List<Map<String, Object>>> listSubjects(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) Integer status) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER", "STUDENT");
        return ApiResponse.ok(basicDataService.listSubjects(keyword, status));
    }

    @PostMapping("/subjects")
    public ApiResponse<Map<String, Object>> createSubject(@Valid @RequestBody SubjectRequest request) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("科目创建成功", basicDataService.createSubject(request));
    }

    @PutMapping("/subjects/{id}")
    public ApiResponse<Map<String, Object>> updateSubject(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("科目更新成功", basicDataService.updateSubject(id, request));
    }

    @DeleteMapping("/subjects/{id}")
    public ApiResponse<Map<String, Object>> deleteSubject(@PathVariable Long id) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("科目删除成功", basicDataService.deleteSubject(id));
    }

    @GetMapping("/knowledge-points")
    public ApiResponse<List<Map<String, Object>>> listKnowledgePoints(@RequestParam(required = false) Long subjectId,
                                                                      @RequestParam(required = false) String keyword,
                                                                      @RequestParam(required = false) Integer status) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER", "STUDENT");
        return ApiResponse.ok(basicDataService.listKnowledgePoints(subjectId, keyword, status));
    }

    @PostMapping("/knowledge-points")
    public ApiResponse<Map<String, Object>> createKnowledgePoint(@Valid @RequestBody KnowledgePointRequest request) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("知识点创建成功", basicDataService.createKnowledgePoint(request));
    }

    @PutMapping("/knowledge-points/{id}")
    public ApiResponse<Map<String, Object>> updateKnowledgePoint(@PathVariable Long id,
                                                                 @Valid @RequestBody KnowledgePointRequest request) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("知识点更新成功", basicDataService.updateKnowledgePoint(id, request));
    }

    @DeleteMapping("/knowledge-points/{id}")
    public ApiResponse<Map<String, Object>> deleteKnowledgePoint(@PathVariable Long id) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("知识点删除成功", basicDataService.deleteKnowledgePoint(id));
    }

    @GetMapping("/notices")
    public ApiResponse<List<Map<String, Object>>> listNotices(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Integer status) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER", "STUDENT");
        return ApiResponse.ok(basicDataService.listNotices(keyword, status));
    }

    @PostMapping("/notices")
    public ApiResponse<Map<String, Object>> createNotice(@Valid @RequestBody NoticeRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("公告创建成功", basicDataService.createNotice(request, user));
    }

    @PutMapping("/notices/{id}")
    public ApiResponse<Map<String, Object>> updateNotice(@PathVariable Long id, @Valid @RequestBody NoticeRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("公告更新成功", basicDataService.updateNotice(id, request, user));
    }

    @DeleteMapping("/notices/{id}")
    public ApiResponse<Map<String, Object>> deleteNotice(@PathVariable Long id) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("公告删除成功", basicDataService.deleteNotice(id, user));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        AuthContext.requireSession();
        return ApiResponse.ok(Map.of(
                "classes", basicDataService.listClasses(null, null).size(),
                "subjects", basicDataService.listSubjects(null, null).size(),
                "knowledgePoints", basicDataService.listKnowledgePoints(null, null, null).size(),
                "notices", basicDataService.listNotices(null, null).size()
        ));
    }
}
