package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.basic.ClassInfoRequest;
import com.smartexam.dto.basic.KnowledgePointRequest;
import com.smartexam.dto.basic.NoticeRequest;
import com.smartexam.dto.basic.SubjectRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class BasicDataService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ConcurrentMap<Long, Map<String, Object>> fallbackClasses = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Map<String, Object>> fallbackSubjects = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Map<String, Object>> fallbackKnowledgePoints = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Map<String, Object>> fallbackNotices = new ConcurrentHashMap<>();
    private final AtomicLong fallbackClassId = new AtomicLong(10);
    private final AtomicLong fallbackSubjectId = new AtomicLong(10);
    private final AtomicLong fallbackKnowledgePointId = new AtomicLong(10);
    private final AtomicLong fallbackNoticeId = new AtomicLong(10);

    public BasicDataService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        seedFallbackData();
    }

    public List<Map<String, Object>> listClasses(String keyword, Integer status) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                return jdbcTemplate.queryForList("""
                        SELECT id, class_name AS className, major, grade, status, created_at AS createdAt, updated_at AS updatedAt
                        FROM edu_class
                        WHERE deleted = 0
                          AND (? IS NULL OR class_name LIKE CONCAT('%', ?, '%') OR major LIKE CONCAT('%', ?, '%') OR grade LIKE CONCAT('%', ?, '%'))
                          AND (? IS NULL OR status = ?)
                        ORDER BY id DESC
                        """, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), status, status);
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据，保证阶段 3 可本地演示。
            }
        }
        return filterFallback(fallbackClasses, keyword, status, "className", "major", "grade");
    }

    public Map<String, Object> createClass(ClassInfoRequest request) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update("""
                        INSERT INTO edu_class (class_name, major, grade, status)
                        VALUES (?, ?, ?, ?)
                        """, trim(request.getClassName()), trim(request.getMajor()), trim(request.getGrade()), request.getStatus());
                Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                return getClassById(id);
            } catch (DuplicateKeyException ex) {
                throw new IllegalArgumentException("班级名称已存在");
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        ensureUnique(fallbackClasses, "className", request.getClassName(), null, "班级名称已存在");
        long id = fallbackClassId.incrementAndGet();
        Map<String, Object> row = baseRow(id, request.getStatus());
        row.put("className", trim(request.getClassName()));
        row.put("major", trim(request.getMajor()));
        row.put("grade", trim(request.getGrade()));
        fallbackClasses.put(id, row);
        return row;
    }

    public Map<String, Object> updateClass(Long id, ClassInfoRequest request) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
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
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        Map<String, Object> row = requireFallbackRow(fallbackClasses, id, "班级不存在");
        ensureUnique(fallbackClasses, "className", request.getClassName(), id, "班级名称已存在");
        row.put("className", trim(request.getClassName()));
        row.put("major", trim(request.getMajor()));
        row.put("grade", trim(request.getGrade()));
        row.put("status", request.getStatus());
        row.put("updatedAt", LocalDateTime.now());
        return row;
    }

    public Map<String, Object> deleteClass(Long id) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                int rows = jdbcTemplate.update("UPDATE edu_class SET deleted = 1 WHERE id = ? AND deleted = 0", id);
                if (rows == 0) {
                    throw new IllegalArgumentException("班级不存在");
                }
                return Map.of("deleted", true, "id", id);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        requireFallbackRow(fallbackClasses, id, "班级不存在");
        fallbackClasses.remove(id);
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listSubjects(String keyword, Integer status) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                return jdbcTemplate.queryForList("""
                        SELECT id, subject_name AS subjectName, description, status, created_at AS createdAt, updated_at AS updatedAt
                        FROM edu_subject
                        WHERE deleted = 0
                          AND (? IS NULL OR subject_name LIKE CONCAT('%', ?, '%') OR description LIKE CONCAT('%', ?, '%'))
                          AND (? IS NULL OR status = ?)
                        ORDER BY id DESC
                        """, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), status, status);
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        return filterFallback(fallbackSubjects, keyword, status, "subjectName", "description");
    }

    public Map<String, Object> createSubject(SubjectRequest request) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update("""
                        INSERT INTO edu_subject (subject_name, description, status)
                        VALUES (?, ?, ?)
                        """, trim(request.getSubjectName()), trim(request.getDescription()), request.getStatus());
                Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                return getSubjectById(id);
            } catch (DuplicateKeyException ex) {
                throw new IllegalArgumentException("科目名称已存在");
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        ensureUnique(fallbackSubjects, "subjectName", request.getSubjectName(), null, "科目名称已存在");
        long id = fallbackSubjectId.incrementAndGet();
        Map<String, Object> row = baseRow(id, request.getStatus());
        row.put("subjectName", trim(request.getSubjectName()));
        row.put("description", trim(request.getDescription()));
        fallbackSubjects.put(id, row);
        return row;
    }

    public Map<String, Object> updateSubject(Long id, SubjectRequest request) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
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
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        Map<String, Object> row = requireFallbackRow(fallbackSubjects, id, "科目不存在");
        ensureUnique(fallbackSubjects, "subjectName", request.getSubjectName(), id, "科目名称已存在");
        row.put("subjectName", trim(request.getSubjectName()));
        row.put("description", trim(request.getDescription()));
        row.put("status", request.getStatus());
        row.put("updatedAt", LocalDateTime.now());
        return row;
    }

    public Map<String, Object> deleteSubject(Long id) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                int rows = jdbcTemplate.update("UPDATE edu_subject SET deleted = 1 WHERE id = ? AND deleted = 0", id);
                if (rows == 0) {
                    throw new IllegalArgumentException("科目不存在");
                }
                return Map.of("deleted", true, "id", id);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        requireFallbackRow(fallbackSubjects, id, "科目不存在");
        fallbackSubjects.remove(id);
        fallbackKnowledgePoints.entrySet().removeIf(entry -> Objects.equals(longValue(entry.getValue().get("subjectId")), id));
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listKnowledgePoints(Long subjectId, String keyword, Integer status) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
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
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        return fallbackKnowledgePoints.values().stream()
                .filter(row -> subjectId == null || Objects.equals(longValue(row.get("subjectId")), subjectId))
                .filter(row -> status == null || Objects.equals(intValue(row.get("status")), status))
                .filter(row -> matchesKeyword(row, keyword, "pointName", "subjectName"))
                .sorted(Comparator.comparing(row -> String.valueOf(row.get("subjectName"))))
                .map(LinkedHashMap::new)
                .collect(Collectors.toList());
    }

    public Map<String, Object> createKnowledgePoint(KnowledgePointRequest request) {
        validateSubjectExists(request.getSubjectId());
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update("""
                        INSERT INTO edu_knowledge_point (subject_id, parent_id, point_name, sort_order, status)
                        VALUES (?, ?, ?, ?, ?)
                        """, request.getSubjectId(), request.getParentId(), trim(request.getPointName()), request.getSortOrder(), request.getStatus());
                Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                return getKnowledgePointById(id);
            } catch (DuplicateKeyException ex) {
                throw new IllegalArgumentException("同一科目下知识点名称已存在");
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        ensureUniqueKnowledgePoint(request.getSubjectId(), request.getPointName(), null);
        long id = fallbackKnowledgePointId.incrementAndGet();
        Map<String, Object> row = baseRow(id, request.getStatus());
        row.put("subjectId", request.getSubjectId());
        row.put("subjectName", subjectNameOf(request.getSubjectId()));
        row.put("parentId", request.getParentId());
        row.put("pointName", trim(request.getPointName()));
        row.put("sortOrder", request.getSortOrder());
        fallbackKnowledgePoints.put(id, row);
        return row;
    }

    public Map<String, Object> updateKnowledgePoint(Long id, KnowledgePointRequest request) {
        validateSubjectExists(request.getSubjectId());
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
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
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        Map<String, Object> row = requireFallbackRow(fallbackKnowledgePoints, id, "知识点不存在");
        ensureUniqueKnowledgePoint(request.getSubjectId(), request.getPointName(), id);
        row.put("subjectId", request.getSubjectId());
        row.put("subjectName", subjectNameOf(request.getSubjectId()));
        row.put("parentId", request.getParentId());
        row.put("pointName", trim(request.getPointName()));
        row.put("sortOrder", request.getSortOrder());
        row.put("status", request.getStatus());
        row.put("updatedAt", LocalDateTime.now());
        return row;
    }

    public Map<String, Object> deleteKnowledgePoint(Long id) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                int rows = jdbcTemplate.update("UPDATE edu_knowledge_point SET deleted = 1 WHERE id = ? AND deleted = 0", id);
                if (rows == 0) {
                    throw new IllegalArgumentException("知识点不存在");
                }
                return Map.of("deleted", true, "id", id);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        requireFallbackRow(fallbackKnowledgePoints, id, "知识点不存在");
        fallbackKnowledgePoints.remove(id);
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listNotices(String keyword, Integer status) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
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
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        return filterFallback(fallbackNotices, keyword, status, "title", "content", "publisherName");
    }

    public Map<String, Object> createNotice(NoticeRequest request, AuthUser publisher) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update("""
                        INSERT INTO notice (title, content, publisher_id, status, publish_time)
                        VALUES (?, ?, ?, ?, ?)
                        """, trim(request.getTitle()), trim(request.getContent()), publisher.getId(), request.getStatus(), LocalDateTime.now());
                Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                return getNoticeById(id);
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        long id = fallbackNoticeId.incrementAndGet();
        Map<String, Object> row = baseRow(id, request.getStatus());
        row.put("title", trim(request.getTitle()));
        row.put("content", trim(request.getContent()));
        row.put("publisherId", publisher.getId());
        row.put("publisherName", publisher.getRealName());
        row.put("publishTime", LocalDateTime.now());
        fallbackNotices.put(id, row);
        return row;
    }

    public Map<String, Object> updateNotice(Long id, NoticeRequest request) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
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
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        Map<String, Object> row = requireFallbackRow(fallbackNotices, id, "公告不存在");
        row.put("title", trim(request.getTitle()));
        row.put("content", trim(request.getContent()));
        row.put("status", request.getStatus());
        row.put("updatedAt", LocalDateTime.now());
        return row;
    }

    public Map<String, Object> deleteNotice(Long id) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                int rows = jdbcTemplate.update("UPDATE notice SET deleted = 1 WHERE id = ? AND deleted = 0", id);
                if (rows == 0) {
                    throw new IllegalArgumentException("公告不存在");
                }
                return Map.of("deleted", true, "id", id);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        requireFallbackRow(fallbackNotices, id, "公告不存在");
        fallbackNotices.remove(id);
        return Map.of("deleted", true, "id", id);
    }

    private Map<String, Object> getClassById(Long id) {
        List<Map<String, Object>> rows = jdbcTemplateProvider.getIfAvailable().queryForList("""
                SELECT id, class_name AS className, major, grade, status, created_at AS createdAt, updated_at AS updatedAt
                FROM edu_class WHERE id = ? AND deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("班级不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> getSubjectById(Long id) {
        List<Map<String, Object>> rows = jdbcTemplateProvider.getIfAvailable().queryForList("""
                SELECT id, subject_name AS subjectName, description, status, created_at AS createdAt, updated_at AS updatedAt
                FROM edu_subject WHERE id = ? AND deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("科目不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> getKnowledgePointById(Long id) {
        List<Map<String, Object>> rows = jdbcTemplateProvider.getIfAvailable().queryForList("""
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
        List<Map<String, Object>> rows = jdbcTemplateProvider.getIfAvailable().queryForList("""
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

    private void seedFallbackData() {
        Map<String, Object> classRow = baseRow(1L, 1);
        classRow.put("className", "23本科计科1班");
        classRow.put("major", "计算机科学与技术");
        classRow.put("grade", "2023级");
        fallbackClasses.put(1L, classRow);

        Map<String, Object> javaSubject = baseRow(1L, 1);
        javaSubject.put("subjectName", "Java程序设计");
        javaSubject.put("description", "用于演示 Java 基础、集合、线程、面向对象等题目。");
        fallbackSubjects.put(1L, javaSubject);

        Map<String, Object> dbSubject = baseRow(2L, 1);
        dbSubject.put("subjectName", "数据库系统");
        dbSubject.put("description", "用于演示 SQL、事务、索引、数据库设计等题目。");
        fallbackSubjects.put(2L, dbSubject);

        addFallbackKnowledgePoint(1L, 1L, "Java程序设计", "集合框架", 1);
        addFallbackKnowledgePoint(2L, 1L, "Java程序设计", "线程与并发", 2);
        addFallbackKnowledgePoint(3L, 2L, "数据库系统", "SQL查询", 1);
        addFallbackKnowledgePoint(4L, 2L, "数据库系统", "事务与ACID", 2);

        Map<String, Object> notice = baseRow(1L, 1);
        notice.put("title", "阶段 3 基础资料管理演示公告");
        notice.put("content", "当前系统已进入班级、科目、知识点和公告管理阶段。");
        notice.put("publisherId", 1L);
        notice.put("publisherName", "系统管理员");
        notice.put("publishTime", LocalDateTime.now());
        fallbackNotices.put(1L, notice);
    }

    private void addFallbackKnowledgePoint(Long id, Long subjectId, String subjectName, String pointName, Integer sortOrder) {
        Map<String, Object> row = baseRow(id, 1);
        row.put("subjectId", subjectId);
        row.put("subjectName", subjectName);
        row.put("parentId", null);
        row.put("pointName", pointName);
        row.put("sortOrder", sortOrder);
        fallbackKnowledgePoints.put(id, row);
    }

    private Map<String, Object> baseRow(Long id, Integer status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("status", status == null ? 1 : status);
        row.put("createdAt", LocalDateTime.now());
        row.put("updatedAt", LocalDateTime.now());
        return row;
    }

    private List<Map<String, Object>> filterFallback(ConcurrentMap<Long, Map<String, Object>> source, String keyword, Integer status, String... keywordFields) {
        return source.values().stream()
                .filter(row -> status == null || Objects.equals(intValue(row.get("status")), status))
                .filter(row -> matchesKeyword(row, keyword, keywordFields))
                .sorted(Comparator.comparing(row -> -longValue(row.get("id"))))
                .map(LinkedHashMap::new)
                .collect(Collectors.toList());
    }

    private boolean matchesKeyword(Map<String, Object> row, String keyword, String... fields) {
        String normalized = blankToNull(keyword);
        if (normalized == null) {
            return true;
        }
        String lowerKeyword = normalized.toLowerCase(Locale.ROOT);
        for (String field : fields) {
            Object value = row.get(field);
            if (value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> requireFallbackRow(ConcurrentMap<Long, Map<String, Object>> source, Long id, String message) {
        Map<String, Object> row = source.get(id);
        if (row == null) {
            throw new IllegalArgumentException(message);
        }
        return row;
    }

    private void ensureUnique(ConcurrentMap<Long, Map<String, Object>> source, String field, String value, Long excludeId, String message) {
        String normalized = trim(value);
        boolean exists = source.values().stream()
                .anyMatch(row -> !Objects.equals(longValue(row.get("id")), excludeId) && normalized.equals(row.get(field)));
        if (exists) {
            throw new IllegalArgumentException(message);
        }
    }

    private void ensureUniqueKnowledgePoint(Long subjectId, String pointName, Long excludeId) {
        String normalized = trim(pointName);
        boolean exists = fallbackKnowledgePoints.values().stream()
                .anyMatch(row -> !Objects.equals(longValue(row.get("id")), excludeId)
                        && Objects.equals(longValue(row.get("subjectId")), subjectId)
                        && normalized.equals(row.get("pointName")));
        if (exists) {
            throw new IllegalArgumentException("同一科目下知识点名称已存在");
        }
    }

    private void validateSubjectExists(Long subjectId) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM edu_subject WHERE id = ? AND deleted = 0", Integer.class, subjectId);
                if (count == null || count == 0) {
                    throw new IllegalArgumentException("科目不存在");
                }
                return;
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                // 数据库不可用时使用内存演示数据。
            }
        }
        if (!fallbackSubjects.containsKey(subjectId)) {
            throw new IllegalArgumentException("科目不存在");
        }
    }

    private String subjectNameOf(Long subjectId) {
        Map<String, Object> subject = fallbackSubjects.get(subjectId);
        return subject == null ? "未知科目" : String.valueOf(subject.get("subjectName"));
    }

    private String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
