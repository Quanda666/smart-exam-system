# SQL Text Block Concatenation Fix - Detailed Report

## Summary
Fixed 41 SQL text block concatenation issues across 5 service files where missing spaces between text block content and concatenated variables caused SQL syntax errors.

## Files Modified

### 1. ExamService.java (8 fixes)
- Line 190: `AND (? IS NULL OR e.exam_name LIKE CONCAT('%', ?, '%') OR p.paper_name LIKE CONCAT('%', ?, '%'))` + listScopeSql
- Line 274: `LEFT JOIN sys_user u ON u.id = e.created_by` + whereSql
- Line 315: `LEFT JOIN sys_user u ON u.id = l.triggered_by` + whereSql
- Line 630: `WHERE u.deleted = 0 AND u.status = 1 AND u.id IN (` + placeholders
- Line 1144: `AND (? IS NULL OR e.exam_name LIKE CONCAT('%', ?, '%') OR p.paper_name LIKE CONCAT('%', ?, '%'))` + scopeSql
- Line 1483: `AND (? IS NULL OR e.exam_name LIKE CONCAT('%', ?, '%') OR p.paper_name LIKE CONCAT('%', ?, '%'))` + scopeSql
- Line 2617: `LEFT JOIN edu_class c ON c.id = sp.primary_class_id AND c.deleted = 0` + whereSql
- Line 4715: `WHERE e.deleted = 0 AND e.status = ? AND et.target_type = ? AND et.target_id IN (` + placeholders

### 2. MonitorService.java (24 fixes)
- Line 148: `FROM cheat_event` + where
- Line 392: `WHERE a.exam_id = ?` + studentScopeSql
- Line 1313: `FROM operation_log l` + where
- Line 1331: `FROM operation_log l` + where
- Line 1422: `FROM operation_log l` + where
- Line 1445: `FROM operation_log l` + where
- Line 1541: `LEFT JOIN sys_user u ON u.id = l.user_id` + where
- Line 1559: `LEFT JOIN sys_user u ON u.id = l.user_id` + where
- Line 1587: `LEFT JOIN sys_user u ON u.id = l.user_id` + where
- Line 1690: `LEFT JOIN sys_user u ON u.id = l.actor_id` + where
- Line 1710: `LEFT JOIN sys_user u ON u.id = l.actor_id` + where
- Line 1738: `LEFT JOIN sys_user u ON u.id = l.actor_id` + where
- Line 1786: `LEFT JOIN sys_user u ON u.id = l.actor_id` + where
- Line 1805: `LEFT JOIN sys_user u ON u.id = l.actor_id` + where
- Line 1833: `LEFT JOIN sys_user u ON u.id = l.actor_id` + where
- Line 1881: `LEFT JOIN sys_user u ON u.id = l.triggered_by` + where
- Line 1897: `LEFT JOIN sys_user u ON u.id = l.triggered_by` + where
- Line 1922: `LEFT JOIN sys_user u ON u.id = l.triggered_by` + where
- Line 1974: `LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = l.question_id` + where
- Line 1999: `LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = l.question_id` + where
- Line 2034: `LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = l.question_id` + where
- Line 2086: `LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = l.question_id` + where
- Line 2110: `LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = l.question_id` + where
- Line 2143: `LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = l.question_id` + where

### 3. ReviewService.java (2 fixes)
- Line 188: `COALESCE(SUM(CASE WHEN ar.review_status = 0 AND` + openRecheckCondition
- Line 202: `MIN(CASE WHEN ar.review_status = 0 AND` + openRecheckCondition

### 4. SystemConfigService.java (3 fixes)
- Line 83: `LEFT JOIN sys_user actor ON actor.id = l.actor_id` + where
- Line 102: `LEFT JOIN sys_user actor ON actor.id = l.actor_id` + where
- Line 135: `LEFT JOIN sys_user actor ON actor.id = l.actor_id` + where

### 5. NotificationService.java (4 fixes)
- Line 123: `LEFT JOIN sys_user u ON u.id = n.user_id` + where
- Line 143: `LEFT JOIN sys_user u ON u.id = n.user_id` + where
- Line 175: `LEFT JOIN sys_user u ON u.id = n.user_id` + where
- Line 228: `FROM notification` + where

## Technical Details

### Problem Pattern
```java
// BEFORE (causes "user_idWHERE" syntax error)
String sql = """
    SELECT * FROM sys_user u
    LEFT JOIN sys_user_role ur ON ur.user_id = u.id
    """
    + whereClause +
    """
    ORDER BY u.id
    """;
```

### Solution Applied
```java
// AFTER (correct: "user_id WHERE")
String sql = """
    SELECT * FROM sys_user u
    LEFT JOIN sys_user_role ur ON ur.user_id = u.id 
    """
    + whereClause +
    """
    ORDER BY u.id
    """;
```

Note the trailing space after `u.id` before the closing `"""`.

## Verification
All fixes were verified by examining the modified lines in each file. Backup files (*.java.bak) were created before applying changes.

## Impact
These fixes prevent SQL syntax errors that would occur when the text block content is concatenated with variable clauses (WHERE, ORDER BY, etc.) at runtime.
