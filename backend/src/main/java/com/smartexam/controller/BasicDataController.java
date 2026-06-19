package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
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
import com.smartexam.service.OperationLogService;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/basic")
@RequireRoles({"ADMIN", "TEACHER", "STUDENT"})
public class BasicDataController {

    private final BasicDataService basicDataService;
    private final OperationLogService operationLogService;

    public BasicDataController(BasicDataService basicDataService, OperationLogService operationLogService) {
        this.basicDataService = basicDataService;
        this.operationLogService = operationLogService;
    }

    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> listClasses(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Integer status) {
        AuthUser user = currentUser();
        return ApiResponse.ok(basicDataService.listClasses(keyword, status, user));
    }

    @PostMapping("/classes")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> createClass(@Valid @RequestBody ClassInfoRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.createClass(request);
        return ApiResponse.ok("Class created", withOperationLogId(user, result,
                "CREATE_BASIC_CLASS", "CLASS#" + result.get("id"), request.getClassName()));
    }

    @PutMapping("/classes/{id}")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> updateClass(@PathVariable Long id, @Valid @RequestBody ClassInfoRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.updateClass(id, request);
        return ApiResponse.ok("Class updated", withOperationLogId(user, result,
                "UPDATE_BASIC_CLASS", "CLASS#" + id, request.getClassName()));
    }

    @DeleteMapping("/classes/{id}")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> deleteClass(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.deleteClass(id);
        return ApiResponse.ok("Class deleted", withOperationLogId(user, result,
                "DELETE_BASIC_CLASS", "CLASS#" + id, null));
    }

    @GetMapping("/courses")
    public ApiResponse<List<Map<String, Object>>> listCourses(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Integer status) {
        AuthUser user = currentUser();
        return ApiResponse.ok(basicDataService.listCourses(keyword, status, user));
    }

    @PostMapping("/courses")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> createCourse(@Valid @RequestBody CourseRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.createCourse(request);
        return ApiResponse.ok("Course created", withOperationLogId(user, result,
                "CREATE_BASIC_COURSE", "COURSE#" + result.get("id"), request.getCourseName()));
    }

    @PutMapping("/courses/{id}")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.updateCourse(id, request);
        return ApiResponse.ok("Course updated", withOperationLogId(user, result,
                "UPDATE_BASIC_COURSE", "COURSE#" + id, request.getCourseName()));
    }

    @DeleteMapping("/courses/{id}")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> deleteCourse(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.deleteCourse(id);
        return ApiResponse.ok("Course deleted", withOperationLogId(user, result,
                "DELETE_BASIC_COURSE", "COURSE#" + id, null));
    }

    @GetMapping("/class-courses")
    public ApiResponse<List<Map<String, Object>>> listClassCourses(@RequestParam(required = false) String keyword,
                                                                   @RequestParam(required = false) Integer status) {
        AuthUser user = currentUser();
        return ApiResponse.ok(basicDataService.listClassCourses(keyword, status, user));
    }

    @PostMapping("/class-courses")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> createClassCourse(@Valid @RequestBody ClassCourseRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.createClassCourse(request);
        return ApiResponse.ok("Class course created", withOperationLogId(user, result,
                "CREATE_BASIC_CLASS_COURSE", "CLASS_COURSE#" + result.get("classCourseId"), request.getTermName()));
    }

    @PutMapping("/class-courses/{id}")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> updateClassCourse(@PathVariable Long id,
                                                              @Valid @RequestBody ClassCourseRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.updateClassCourse(id, request);
        return ApiResponse.ok("Class course updated", withOperationLogId(user, result,
                "UPDATE_BASIC_CLASS_COURSE", "CLASS_COURSE#" + id, request.getTermName()));
    }

    @DeleteMapping("/class-courses/{id}")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> deleteClassCourse(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.deleteClassCourse(id);
        return ApiResponse.ok("Class course deleted", withOperationLogId(user, result,
                "DELETE_BASIC_CLASS_COURSE", "CLASS_COURSE#" + id, null));
    }

    @GetMapping("/teaching-assignments")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<List<Map<String, Object>>> listTeachingAssignments(@RequestParam(required = false) Long teacherUserId,
                                                                          @RequestParam(required = false) Long classCourseId) {
        AuthUser user = currentUser();
        return ApiResponse.ok(basicDataService.listTeachingAssignments(teacherUserId, classCourseId, user));
    }

    @PostMapping("/teaching-assignments")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> createTeachingAssignment(@Valid @RequestBody TeachingAssignmentRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.createTeachingAssignment(request);
        return ApiResponse.ok("Teaching assignment created", withOperationLogId(user, result,
                "CREATE_BASIC_TEACHING_ASSIGNMENT", "TEACHING_ASSIGNMENT#" + result.get("id"),
                "teacherUserId=" + request.getTeacherUserId() + "; classCourseId=" + request.getClassCourseId()));
    }

    @DeleteMapping("/teaching-assignments/{id}")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> deleteTeachingAssignment(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.deleteTeachingAssignment(id);
        return ApiResponse.ok("Teaching assignment deleted", withOperationLogId(user, result,
                "DELETE_BASIC_TEACHING_ASSIGNMENT", "TEACHING_ASSIGNMENT#" + id, null));
    }

    @GetMapping("/student-memberships")
    public ApiResponse<List<Map<String, Object>>> listStudentMemberships(@RequestParam(required = false) Long studentUserId,
                                                                         @RequestParam(required = false) Long classId) {
        AuthUser user = currentUser();
        return ApiResponse.ok(basicDataService.listStudentMemberships(studentUserId, classId, user));
    }

    @PostMapping("/student-memberships")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> createStudentMembership(@Valid @RequestBody StudentClassMembershipRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.createStudentMembership(request);
        return ApiResponse.ok("Student membership saved", withOperationLogId(user, result,
                "CREATE_BASIC_STUDENT_MEMBERSHIP", "STUDENT_MEMBERSHIP#" + result.get("id"),
                "studentUserId=" + request.getStudentUserId() + "; classId=" + request.getClassId()));
    }

    @DeleteMapping("/student-memberships/{id}")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> deleteStudentMembership(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.deleteStudentMembership(id);
        return ApiResponse.ok("Student membership deleted", withOperationLogId(user, result,
                "DELETE_BASIC_STUDENT_MEMBERSHIP", "STUDENT_MEMBERSHIP#" + id, null));
    }

    @GetMapping("/subjects")
    public ApiResponse<List<Map<String, Object>>> listSubjects(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(basicDataService.listSubjects(keyword, status));
    }

    @PostMapping("/subjects")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> createSubject(@Valid @RequestBody SubjectRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.createSubject(request);
        return ApiResponse.ok("Subject created", withOperationLogId(user, result,
                "CREATE_BASIC_SUBJECT", "SUBJECT#" + result.get("id"), request.getSubjectName()));
    }

    @PutMapping("/subjects/{id}")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> updateSubject(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.updateSubject(id, request);
        return ApiResponse.ok("Subject updated", withOperationLogId(user, result,
                "UPDATE_BASIC_SUBJECT", "SUBJECT#" + id, request.getSubjectName()));
    }

    @DeleteMapping("/subjects/{id}")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> deleteSubject(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.deleteSubject(id);
        return ApiResponse.ok("Subject deleted", withOperationLogId(user, result,
                "DELETE_BASIC_SUBJECT", "SUBJECT#" + id, null));
    }

    @GetMapping("/knowledge-points")
    public ApiResponse<List<Map<String, Object>>> listKnowledgePoints(@RequestParam(required = false) Long subjectId,
                                                                       @RequestParam(required = false) String keyword,
                                                                       @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(basicDataService.listKnowledgePoints(subjectId, keyword, status));
    }

    @PostMapping("/knowledge-points")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> createKnowledgePoint(@Valid @RequestBody KnowledgePointRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.createKnowledgePoint(request);
        return ApiResponse.ok("Knowledge point created", withOperationLogId(user, result,
                "CREATE_BASIC_KNOWLEDGE_POINT", "KNOWLEDGE_POINT#" + result.get("id"), request.getPointName()));
    }

    @PutMapping("/knowledge-points/{id}")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> updateKnowledgePoint(@PathVariable Long id,
                                                                 @Valid @RequestBody KnowledgePointRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.updateKnowledgePoint(id, request);
        return ApiResponse.ok("Knowledge point updated", withOperationLogId(user, result,
                "UPDATE_BASIC_KNOWLEDGE_POINT", "KNOWLEDGE_POINT#" + id, request.getPointName()));
    }

    @DeleteMapping("/knowledge-points/{id}")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> deleteKnowledgePoint(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.deleteKnowledgePoint(id);
        return ApiResponse.ok("Knowledge point deleted", withOperationLogId(user, result,
                "DELETE_BASIC_KNOWLEDGE_POINT", "KNOWLEDGE_POINT#" + id, null));
    }

    @GetMapping("/notices")
    public ApiResponse<List<Map<String, Object>>> listNotices(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Integer status,
                                                              @RequestParam(required = false) Long noticeId) {
        AuthUser user = currentUser();
        return ApiResponse.ok(basicDataService.listNotices(keyword, status, noticeId, user));
    }

    @PostMapping("/notices")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> createNotice(@Valid @RequestBody NoticeRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.createNotice(request, user);
        return ApiResponse.ok("Notice created", withOperationLogId(user, result,
                "CREATE_BASIC_NOTICE", "NOTICE#" + result.get("id"), request.getTitle()));
    }

    @PutMapping("/notices/{id}")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> updateNotice(@PathVariable Long id, @Valid @RequestBody NoticeRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.updateNotice(id, request, user);
        return ApiResponse.ok("Notice updated", withOperationLogId(user, result,
                "UPDATE_BASIC_NOTICE", "NOTICE#" + id, request.getTitle()));
    }

    @DeleteMapping("/notices/{id}")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> deleteNotice(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = basicDataService.deleteNotice(id, user);
        return ApiResponse.ok("Notice deleted", withOperationLogId(user, result,
                "DELETE_BASIC_NOTICE", "NOTICE#" + id, null));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        AuthUser user = currentUser();
        return ApiResponse.ok(basicDataService.summary(user));
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
