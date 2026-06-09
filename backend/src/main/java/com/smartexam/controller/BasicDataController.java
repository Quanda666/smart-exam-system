package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.basic.ClassCourseRequest;
import com.smartexam.dto.basic.ClassInfoRequest;
import com.smartexam.dto.basic.CourseRequest;
import com.smartexam.dto.basic.KnowledgePointRequest;
import com.smartexam.dto.basic.NoticeRequest;
import com.smartexam.dto.basic.StudentClassMembershipRequest;
import com.smartexam.dto.basic.SubjectRequest;
import com.smartexam.dto.basic.TeachingAssignmentRequest;
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
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER", "STUDENT");
        return ApiResponse.ok(basicDataService.listClasses(keyword, status, user));
    }

    @PostMapping("/classes")
    public ApiResponse<Map<String, Object>> createClass(@Valid @RequestBody ClassInfoRequest request) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Class created", basicDataService.createClass(request));
    }

    @PutMapping("/classes/{id}")
    public ApiResponse<Map<String, Object>> updateClass(@PathVariable Long id, @Valid @RequestBody ClassInfoRequest request) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Class updated", basicDataService.updateClass(id, request));
    }

    @DeleteMapping("/classes/{id}")
    public ApiResponse<Map<String, Object>> deleteClass(@PathVariable Long id) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Class deleted", basicDataService.deleteClass(id));
    }

    @GetMapping("/courses")
    public ApiResponse<List<Map<String, Object>>> listCourses(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Integer status) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER", "STUDENT");
        return ApiResponse.ok(basicDataService.listCourses(keyword, status, user));
    }

    @PostMapping("/courses")
    public ApiResponse<Map<String, Object>> createCourse(@Valid @RequestBody CourseRequest request) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Course created", basicDataService.createCourse(request));
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<Map<String, Object>> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Course updated", basicDataService.updateCourse(id, request));
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<Map<String, Object>> deleteCourse(@PathVariable Long id) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Course deleted", basicDataService.deleteCourse(id));
    }

    @GetMapping("/class-courses")
    public ApiResponse<List<Map<String, Object>>> listClassCourses(@RequestParam(required = false) String keyword,
                                                                   @RequestParam(required = false) Integer status) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER", "STUDENT");
        return ApiResponse.ok(basicDataService.listClassCourses(keyword, status, user));
    }

    @PostMapping("/class-courses")
    public ApiResponse<Map<String, Object>> createClassCourse(@Valid @RequestBody ClassCourseRequest request) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Class course created", basicDataService.createClassCourse(request));
    }

    @PutMapping("/class-courses/{id}")
    public ApiResponse<Map<String, Object>> updateClassCourse(@PathVariable Long id,
                                                              @Valid @RequestBody ClassCourseRequest request) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Class course updated", basicDataService.updateClassCourse(id, request));
    }

    @DeleteMapping("/class-courses/{id}")
    public ApiResponse<Map<String, Object>> deleteClassCourse(@PathVariable Long id) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Class course deleted", basicDataService.deleteClassCourse(id));
    }

    @GetMapping("/teaching-assignments")
    public ApiResponse<List<Map<String, Object>>> listTeachingAssignments(@RequestParam(required = false) Long teacherUserId,
                                                                          @RequestParam(required = false) Long classCourseId) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(basicDataService.listTeachingAssignments(teacherUserId, classCourseId, user));
    }

    @PostMapping("/teaching-assignments")
    public ApiResponse<Map<String, Object>> createTeachingAssignment(@Valid @RequestBody TeachingAssignmentRequest request) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Teaching assignment created", basicDataService.createTeachingAssignment(request));
    }

    @DeleteMapping("/teaching-assignments/{id}")
    public ApiResponse<Map<String, Object>> deleteTeachingAssignment(@PathVariable Long id) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Teaching assignment deleted", basicDataService.deleteTeachingAssignment(id));
    }

    @GetMapping("/student-memberships")
    public ApiResponse<List<Map<String, Object>>> listStudentMemberships(@RequestParam(required = false) Long studentUserId,
                                                                         @RequestParam(required = false) Long classId) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER", "STUDENT");
        return ApiResponse.ok(basicDataService.listStudentMemberships(studentUserId, classId, user));
    }

    @PostMapping("/student-memberships")
    public ApiResponse<Map<String, Object>> createStudentMembership(@Valid @RequestBody StudentClassMembershipRequest request) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Student membership saved", basicDataService.createStudentMembership(request));
    }

    @DeleteMapping("/student-memberships/{id}")
    public ApiResponse<Map<String, Object>> deleteStudentMembership(@PathVariable Long id) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Student membership deleted", basicDataService.deleteStudentMembership(id));
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
        return ApiResponse.ok("Subject created", basicDataService.createSubject(request));
    }

    @PutMapping("/subjects/{id}")
    public ApiResponse<Map<String, Object>> updateSubject(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("Subject updated", basicDataService.updateSubject(id, request));
    }

    @DeleteMapping("/subjects/{id}")
    public ApiResponse<Map<String, Object>> deleteSubject(@PathVariable Long id) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Subject deleted", basicDataService.deleteSubject(id));
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
        return ApiResponse.ok("Knowledge point created", basicDataService.createKnowledgePoint(request));
    }

    @PutMapping("/knowledge-points/{id}")
    public ApiResponse<Map<String, Object>> updateKnowledgePoint(@PathVariable Long id,
                                                                 @Valid @RequestBody KnowledgePointRequest request) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("Knowledge point updated", basicDataService.updateKnowledgePoint(id, request));
    }

    @DeleteMapping("/knowledge-points/{id}")
    public ApiResponse<Map<String, Object>> deleteKnowledgePoint(@PathVariable Long id) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok("Knowledge point deleted", basicDataService.deleteKnowledgePoint(id));
    }

    @GetMapping("/notices")
    public ApiResponse<List<Map<String, Object>>> listNotices(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Integer status) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER", "STUDENT");
        return ApiResponse.ok(basicDataService.listNotices(keyword, status, user));
    }

    @PostMapping("/notices")
    public ApiResponse<Map<String, Object>> createNotice(@Valid @RequestBody NoticeRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("Notice created", basicDataService.createNotice(request, user));
    }

    @PutMapping("/notices/{id}")
    public ApiResponse<Map<String, Object>> updateNotice(@PathVariable Long id, @Valid @RequestBody NoticeRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("Notice updated", basicDataService.updateNotice(id, request, user));
    }

    @DeleteMapping("/notices/{id}")
    public ApiResponse<Map<String, Object>> deleteNotice(@PathVariable Long id) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("Notice deleted", basicDataService.deleteNotice(id, user));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        AuthUser user = roleAccessService.requireLogin();
        return ApiResponse.ok(basicDataService.summary(user));
    }
}
