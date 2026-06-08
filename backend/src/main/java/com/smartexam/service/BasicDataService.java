package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.DatabaseUnavailableException;
import com.smartexam.dto.basic.ClassInfoRequest;
import com.smartexam.dto.basic.KnowledgePointRequest;
import com.smartexam.dto.basic.NoticeRequest;
import com.smartexam.dto.basic.SubjectRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BasicDataService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final NotificationService notificationService;

    public BasicDataService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, NotificationService notificationService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.notificationService = notificationService;
    }

    public List<Map<String, Object>> listClasses(String keyword, Integer status) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForList("""
                SELECT id, class_name AS className, major, grade, status, created_at AS createdAt, updated_at AS updatedAt
                FROM edu_class
                WHERE deleted = 0
                  AND (? IS NULL OR class_name LIKE CONCAT('%', ?, '%') OR major LIKE CONCAT('%', ?, '%') OR grade LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR status = ?)
                ORDER BY id DESC
                """, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), status, status);
    }

    public Map<String, Object> createClass(ClassInfoRequest request) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        try {
            jdbcTemplate.update("""
                    INSERT INTO edu_class (class_name, major, grade, status)
                    VALUES (?, ?, ?, ?)
                    """, trim(request.getClassName()), trim(request.getMajor()), trim(request.getGrade()), request.getStatus());
            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return getClassById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("班级名称已存在");
        }
    }

    public Map<String, Object> updateClass(Long id, ClassInfoRequest request) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        try {
            int rows = jdbcTemplate.update("""
                    UPDATE edu_class
                    SET class_name = ?, major = ?, grade = ?, status = ?
                    WHERE id = ? AND deleted = 0
                    """, trim(request.getClassName()), trim(request.getMajor()), trim(request.getGrade()), request.getStatus(), id);
            if (rows == 0) {
                throw new IllegalArgumentException("班级不存在");
            }
            return getClassById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("班级名称已存在");
        }
    }

    public Map<String, Object> deleteClass(Long id) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int rows = jdbcTemplate.update("UPDATE edu_class SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("班级不存在");
        }
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listSubjects(String keyword, Integer status) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForList("""
                SELECT id, subject_name AS subjectName, description, status, created_at AS createdAt, updated_at AS updatedAt
                FROM edu_subject
                WHERE deleted = 0
                  AND (? IS NULL OR subject_name LIKE CONCAT('%', ?, '%') OR description LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR status = ?)
                ORDER BY id DESC
                """, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), status, status);
    }

    public Map<String, Object> createSubject(SubjectRequest request) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        try {
            jdbcTemplate.update("""
                    INSERT INTO edu_subject (subject_name, description, status)
                    VALUES (?, ?, ?)
                    """, trim(request.getSubjectName()), trim(request.getDescription()), request.getStatus());
            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return getSubjectById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("科目名称已存在");
        }
    }

    public Map<String, Object> updateSubject(Long id, SubjectRequest request) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        try {
            int rows = jdbcTemplate.update("""
                    UPDATE edu_subject
                    SET subject_name = ?, description = ?, status = ?
                    WHERE id = ? AND deleted = 0
                    """, trim(request.getSubjectName()), trim(request.getDescription()), request.getStatus(), id);
            if (rows == 0) {
                throw new IllegalArgumentException("科目不存在");
            }
            return getSubjectById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("科目名称已存在");
        }
    }

    public Map<String, Object> deleteSubject(Long id) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int rows = jdbcTemplate.update("UPDATE edu_subject SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("科目不存在");
        }
        jdbcTemplate.update("UPDATE edu_knowledge_point SET deleted = 1 WHERE subject_id = ? AND deleted = 0", id);
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listKnowledgePoints(Long subjectId, String keyword, Integer status) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForList("""
                SELECT kp.id, kp.subject_id AS subjectId, s.subject_name AS subjectName, kp.parent_id AS parentId,
                       kp.point_name AS pointName, kp.sort_order AS sortOrder, kp.status,
                       kp.created_at AS createdAt, kp.updated_at AS updatedAt
                FROM edu_knowledge_point kp
                JOIN edu_subject s ON s.id = kp.subject_id
                WHERE kp.deleted = 0
                  AND (? IS NULL OR kp.subject_id = ?)
                  AND (? IS NULL OR kp.point_name LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR kp.status = ?)
                ORDER BY kp.subject_id, kp.sort_order, kp.id
                """, subjectId, subjectId, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), status, status);
    }

    public Map<String, Object> createKnowledgePoint(KnowledgePointRequest request) {
        validateSubjectExists(request.getSubjectId());
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        try {
            jdbcTemplate.update("""
                    INSERT INTO edu_knowledge_point (subject_id, parent_id, point_name, sort_order, status)
                    VALUES (?, ?, ?, ?, ?)
                    """, request.getSubjectId(), request.getParentId(), trim(request.getPointName()), request.getSortOrder(), request.getStatus());
            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return getKnowledgePointById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("同一科目下知识点名称已存在");
        }
    }

    public Map<String, Object> updateKnowledgePoint(Long id, KnowledgePointRequest request) {
        validateSubjectExists(request.getSubjectId());
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        try {
            int rows = jdbcTemplate.update("""
                    UPDATE edu_knowledge_point
                    SET subject_id = ?, parent_id = ?, point_name = ?, sort_order = ?, status = ?
                    WHERE id = ? AND deleted = 0
                    """, request.getSubjectId(), request.getParentId(), trim(request.getPointName()), request.getSortOrder(), request.getStatus(), id);
            if (rows == 0) {
                throw new IllegalArgumentException("知识点不存在");
            }
            return getKnowledgePointById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("同一科目下知识点名称已存在");
        }
    }

    public Map<String, Object> deleteKnowledgePoint(Long id) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int rows = jdbcTemplate.update("UPDATE edu_knowledge_point SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("知识点不存在");
        }
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listNotices(String keyword, Integer status) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForList("""
                SELECT n.id, n.title, n.content, n.status, n.publish_time AS publishTime,
                       n.publisher_id AS publisherId, u.real_name AS publisherName,
                       n.created_at AS createdAt, n.updated_at AS updatedAt
                FROM notice n
                LEFT JOIN sys_user u ON u.id = n.publisher_id
                WHERE n.deleted = 0
                  AND (? IS NULL OR n.title LIKE CONCAT('%', ?, '%') OR n.content LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR n.status = ?)
                ORDER BY n.id DESC
                """, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), status, status);
    }

    public Map<String, Object> createNotice(NoticeRequest request, AuthUser publisher) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        try {
            jdbcTemplate.update("""
                    INSERT INTO notice (title, content, publisher_id, status, publish_time)
                    VALUES (?, ?, ?, ?, ?)
                    """, trim(request.getTitle()), trim(request.getContent()), publisher.getId(), request.getStatus(), LocalDateTime.now());
            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            Map<String, Object> notice = getNoticeById(id);
            // 公告发布（status=1）时，向全体在用用户推送站内通知，使顶栏铃铛能看到新公告
            if (request.getStatus() != null && request.getStatus() == 1) {
                pushNoticeNotification(jdbcTemplate, request);
            }
            return notice;
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("公告标题已存在");
        }
    }

    /** 公告发布后向全体在用用户群发站内通知；推送失败不影响公告创建本身。 */
    private void pushNoticeNotification(JdbcTemplate jdbcTemplate, NoticeRequest request) {
        try {
            List<Long> userIds = jdbcTemplate.queryForList(
                    "SELECT id FROM sys_user WHERE deleted = 0 AND status = 1", Long.class);
            notificationService.sendBatch(userIds, "新公告：" + trim(request.getTitle()),
                    trim(request.getContent()), "NOTICE", "/basic/notices");
        } catch (Exception ex) {
            // 通知为附属能力，推送失败时静默忽略，保证公告主流程成功
        }
    }

    public Map<String, Object> updateNotice(Long id, NoticeRequest request, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireNoticeOwner(jdbcTemplate, id, user);
        try {
            int rows = jdbcTemplate.update("""
                    UPDATE notice
                    SET title = ?, content = ?, status = ?
                    WHERE id = ? AND deleted = 0
                    """, trim(request.getTitle()), trim(request.getContent()), request.getStatus(), id);
            if (rows == 0) {
                throw new IllegalArgumentException("公告不存在");
            }
            return getNoticeById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("公告标题已存在");
        }
    }

    public Map<String, Object> deleteNotice(Long id, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireNoticeOwner(jdbcTemplate, id, user);
        int rows = jdbcTemplate.update("UPDATE notice SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("公告不存在");
        }
        return Map.of("deleted", true, "id", id);
    }

    /**
     * 公告归属校验：仅发布者本人或管理员可修改/删除，避免教师互相删改公告。
     * 与 PaperService#requirePaperOwner / QuestionBankService 的归属隔离策略保持一致。
     */
    private void requireNoticeOwner(JdbcTemplate jdbcTemplate, Long id, AuthUser user) {
        List<Map<String, Object>> owners = jdbcTemplate.queryForList(
                "SELECT publisher_id FROM notice WHERE id = ? AND deleted = 0", id);
        if (owners.isEmpty()) {
            throw new IllegalArgumentException("公告不存在");
        }
        if (user != null && user.hasRole("ADMIN")) {
            return;
        }
        Object publisherId = owners.get(0).get("publisher_id");
        if (user == null || publisherId == null || !publisherId.toString().equals(String.valueOf(user.getId()))) {
            throw new IllegalArgumentException("只能管理本人发布的公告");
        }
    }

    private Map<String, Object> getClassById(Long id) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, class_name AS className, major, grade, status, created_at AS createdAt, updated_at AS updatedAt
                FROM edu_class WHERE id = ? AND deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("班级不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> getSubjectById(Long id) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, subject_name AS subjectName, description, status, created_at AS createdAt, updated_at AS updatedAt
                FROM edu_subject WHERE id = ? AND deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("科目不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> getKnowledgePointById(Long id) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT kp.id, kp.subject_id AS subjectId, s.subject_name AS subjectName, kp.parent_id AS parentId,
                       kp.point_name AS pointName, kp.sort_order AS sortOrder, kp.status,
                       kp.created_at AS createdAt, kp.updated_at AS updatedAt
                FROM edu_knowledge_point kp
                JOIN edu_subject s ON s.id = kp.subject_id
                WHERE kp.id = ? AND kp.deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("知识点不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> getNoticeById(Long id) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT n.id, n.title, n.content, n.status, n.publish_time AS publishTime,
                       n.publisher_id AS publisherId, u.real_name AS publisherName,
                       n.created_at AS createdAt, n.updated_at AS updatedAt
                FROM notice n
                LEFT JOIN sys_user u ON u.id = n.publisher_id
                WHERE n.id = ? AND n.deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("公告不存在");
        }
        return rows.get(0);
    }

    private void validateSubjectExists(Long subjectId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM edu_subject WHERE id = ? AND deleted = 0", Integer.class, subjectId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("科目不存在");
        }
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }

    private String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
