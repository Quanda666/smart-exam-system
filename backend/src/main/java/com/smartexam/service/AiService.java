package com.smartexam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartexam.config.AiProperties;
import com.smartexam.auth.AuthContext;
import com.smartexam.dto.ai.AiGeneratedQuestion;
import com.smartexam.dto.ai.AiGeneratedQuestionOption;
import com.smartexam.dto.ai.GenerateQuestionBatchRequest;
import com.smartexam.dto.ai.MaterialQuestionGenerationRequest;
import com.smartexam.dto.ai.SuggestReviewRequest;
import com.smartexam.dto.ai.WrongQuestionExplainRequest;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiService {

    public static final String PROMPT_VERSION_QUESTION_GENERATE = "question-generate-v2";
    public static final String PROMPT_VERSION_QUESTION_IMPORT = "question-import-v2";
    public static final String PROMPT_VERSION_MATERIAL_GENERATE = "material-question-v3";
    public static final String PROMPT_VERSION_MATERIAL_OUTLINE = "material-outline-v1";

    private static final List<String> OBJECTIVE_TYPES = List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");
    private static final List<String> ALL_TYPES = List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SUBJECTIVE");
    private static final List<String> ALL_DIFFICULTIES = List.of("EASY", "MEDIUM", "HARD");
    private static final int MAX_GENERATED_QUESTION_TEXT_LENGTH = 4000;
    private static final int MAX_GENERATED_OPTION_CONTENT_LENGTH = 1000;
    private static final int MAX_MATERIAL_TYPE_COUNT = 30;
    private static final int MAX_BATCH_QUESTION_COUNT = 10;

    private final AiProperties aiProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public AiService(AiProperties aiProperties,
                     ObjectMapper objectMapper,
                     ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = Math.max(1, aiProperties.getTimeoutSeconds()) * 1000;
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    public List<AiGeneratedQuestion> generateQuestionDrafts(GenerateQuestionBatchRequest request) {
        validateBatchRequest(request);
        String prompt = buildQuestionDraftPrompt(request);
        if (isMockMode()) {
            List<AiGeneratedQuestion> local = buildLocalQuestionDrafts(request);
            logAiUsage("QUESTION_GENERATE_LOCAL", prompt, serializeForLog(local), true, null);
            return stampQuestionRuntime(local, PROMPT_VERSION_QUESTION_GENERATE);
        }

        String content = requestRemote("QUESTION_GENERATE", prompt, aiProperties.getApiKey());
        List<AiGeneratedQuestion> parsed = parseGeneratedQuestions(content);
        if (parsed.isEmpty()) {
            throw new IllegalStateException("AI 未返回可用题目草稿");
        }

        List<AiGeneratedQuestion> normalized = new ArrayList<>();
        int count = normalizedCount(request.getCount());
        for (int i = 0; i < parsed.size() && normalized.size() < count; i++) {
            normalized.add(normalizeGeneratedQuestion(parsed.get(i), request, i + 1));
        }
        while (normalized.size() < count) {
            normalized.add(createLocalQuestionDraft(request, normalized.size() + 1));
        }
        return stampQuestionRuntime(normalized, PROMPT_VERSION_QUESTION_GENERATE);
    }

    public List<AiGeneratedQuestion> generateQuestionDraftsFromMaterial(String materialText, MaterialQuestionGenerationRequest request) {
        validateMaterialRequest(request);
        Map<String, Integer> typeCounts = normalizedTypeCounts(request.getTypeCounts());
        String prompt = buildMaterialQuestionPrompt(materialText, request, typeCounts);
        if (!isMockMode()) {
            try {
                List<AiGeneratedQuestion> parsed = parseGeneratedQuestions(requestRemote("MATERIAL_GENERATE", prompt, aiProperties.getApiKey()));
                List<AiGeneratedQuestion> normalized = normalizeMaterialQuestions(parsed, request, typeCounts);
                if (hasRequestedCounts(normalized, typeCounts)) {
                    return stampQuestionRuntime(normalized, PROMPT_VERSION_MATERIAL_GENERATE);
                }
            } catch (Exception ignore) {
                // 模型输出不稳定时回退到本地草稿，老师仍然能继续微调。
            }
        }
        List<AiGeneratedQuestion> local = buildLocalMaterialQuestionDrafts(materialText, request, typeCounts);
        logAiUsage("MATERIAL_GENERATE_LOCAL", prompt, serializeForLog(local), true, null);
        return stampQuestionRuntime(local, PROMPT_VERSION_MATERIAL_GENERATE);
    }

    public String explainWrongQuestion(WrongQuestionExplainRequest request, AuthUser user) {
        WrongQuestionExplainRequest grounded = loadReleasedWrongQuestionForExplain(
                request.getQuestionId(), request.getExamId(), user);
        String prompt = buildWrongQuestionPrompt(grounded);
        if (isMockMode()) {
            String local = buildLocalWrongQuestionExplanation(grounded);
            logAiUsage("WRONG_QUESTION_EXPLAIN_LOCAL", prompt, local, true, null);
            return local;
        }
        return requestRemote("WRONG_QUESTION_EXPLAIN", prompt, aiProperties.getApiKey());
    }

    private WrongQuestionExplainRequest loadReleasedWrongQuestionForExplain(Long questionId, Long examId, AuthUser user) {
        if (questionId == null) {
            throw new IllegalArgumentException("questionId is required");
        }
        if (questionId <= 0) {
            throw new IllegalArgumentException("questionId must be positive");
        }
        if (examId == null) {
            throw new IllegalArgumentException("examId is required");
        }
        if (examId <= 0) {
            throw new IllegalArgumentException("examId must be positive");
        }
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                WITH wrong_answers AS (
                  SELECT ar.id AS answer_record_id,
                         ar.question_id,
                         e.id AS exam_id,
                         COALESCE(eqs.stem, q.stem) AS stem,
                         COALESCE(eqs.question_type, q.question_type) AS question_type,
                         COALESCE(eqs.correct_answer, q.correct_answer) AS correct_answer,
                         COALESCE(eqs.analysis, q.analysis) AS analysis,
                         ar.answer_content AS student_answer,
                         COALESCE(ea.submit_time, ar.created_at) AS wrong_time
                  FROM answer_record ar
                  JOIN exam_attempt ea ON ea.id = ar.attempt_id
                  JOIN exam e ON e.id = ea.exam_id AND e.deleted = 0
                  JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                  JOIN question q ON q.id = ar.question_id
                  LEFT JOIN exam_question_snapshot eqs
                    ON eqs.exam_id = e.id AND eqs.question_id = ar.question_id
                  WHERE ea.user_id = ?
                    AND ar.question_id = ?
                    AND e.id = ?
                    AND ea.status = 5
                    AND ea.score IS NOT NULL
                    AND ar.review_status = 1
                    AND ar.is_correct = 0
                    AND NOT EXISTS (
                      SELECT 1 FROM score_appeal sa
                      WHERE sa.attempt_id = ea.id
                        AND sa.status = 1
                        AND sa.handling_result = 'RECHECK_REQUIRED'
                    )
                ),
                ranked_wrong AS (
                  SELECT wrong_answers.*,
                         COUNT(*) OVER (PARTITION BY question_id, exam_id) AS wrong_count,
                         ROW_NUMBER() OVER (
                           PARTITION BY question_id, exam_id
                           ORDER BY wrong_time DESC, answer_record_id DESC
                         ) AS row_no
                  FROM wrong_answers
                )
                SELECT question_id, exam_id, stem, question_type, correct_answer, analysis,
                       student_answer, wrong_count
                FROM ranked_wrong
                WHERE row_no = 1
                """, user.getId(), questionId, examId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Wrong question explanation is only available for released wrong answers");
        }
        Map<String, Object> row = rows.get(0);
        WrongQuestionExplainRequest grounded = new WrongQuestionExplainRequest();
        grounded.setQuestionId(((Number) row.get("question_id")).longValue());
        grounded.setExamId(((Number) row.get("exam_id")).longValue());
        grounded.setStem(stringValue(row.get("stem")));
        grounded.setQuestionType(stringValue(row.get("question_type")));
        grounded.setCorrectAnswer(stringValue(row.get("correct_answer")));
        grounded.setAnalysis(stringValue(row.get("analysis")));
        grounded.setStudentAnswer(stringValue(row.get("student_answer")));
        grounded.setWrongCount(((Number) row.get("wrong_count")).intValue());
        grounded.setOptions(loadWrongQuestionOptionsForExplain(jdbcTemplate,
                ((Number) row.get("exam_id")).longValue(), questionId, grounded.getCorrectAnswer()));
        return grounded;
    }

    private List<AiGeneratedQuestionOption> loadWrongQuestionOptionsForExplain(JdbcTemplate jdbcTemplate,
                                                                               Long examId,
                                                                               Long questionId,
                                                                               String correctAnswer) {
        List<AiGeneratedQuestionOption> snapshotOptions = jdbcTemplate.query("""
                SELECT option_label, option_content
                FROM exam_question_option_snapshot
                WHERE exam_id = ? AND question_id = ?
                ORDER BY sort_order
                """, (rs, rowNum) -> option(
                rs.getString("option_label"),
                rs.getString("option_content"),
                answerContainsOption(correctAnswer, rs.getString("option_label"))
        ), examId, questionId);
        if (!snapshotOptions.isEmpty()) {
            return snapshotOptions;
        }
        return jdbcTemplate.query("""
                SELECT option_label, option_content, is_correct
                FROM question_option
                WHERE question_id = ?
                ORDER BY sort_order
                """, (rs, rowNum) -> option(
                rs.getString("option_label"),
                rs.getString("option_content"),
                rs.getInt("is_correct") == 1
        ), questionId);
    }

    public String suggestReview(SuggestReviewRequest request) {
        String prompt = "请对以下主观题答案进行评分，并给出评语。\n题目：" + request.getQuestion()
                + "\n参考答案：" + request.getCorrectAnswer() + "\n学生答案：" + request.getStudentAnswer();
        return callAi("SUGGEST_REVIEW", prompt);
    }

    public List<Map<String, Object>> generateMaterialOutline(String title, String materialText) {
        String prompt = buildMaterialOutlinePrompt(title, materialText);
        if (!isMockMode()) {
            try {
                List<Map<String, Object>> outline = parseMaterialOutline(requestRemote("MATERIAL_OUTLINE", prompt, aiProperties.getApiKey()));
                if (!outline.isEmpty()) {
                    return outline;
                }
            } catch (Exception ignore) {
                // 大纲生成失败时走本地规则，资料库上传流程不能因此中断。
            }
        }
        List<Map<String, Object>> local = buildLocalMaterialOutline(title, materialText);
        logAiUsage("MATERIAL_OUTLINE_LOCAL", prompt, serializeForLog(local), true, null);
        return local;
    }

    public String currentModel() {
        return firstNonBlank(aiProperties.getModel(), "mock-local");
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("Database connection is unavailable");
        }
        return jdbcTemplate;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean answerContainsOption(Object correctAnswer, Object optionLabel) {
        String answer = normalizeAnswerToken(correctAnswer);
        String label = normalizeAnswerToken(optionLabel);
        return !label.isEmpty() && answer.contains(label);
    }

    private String normalizeAnswerToken(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        StringBuilder normalized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                normalized.append(Character.toUpperCase(ch));
            }
        }
        return normalized.toString();
    }

    private String callAi(String scene, String prompt) {
        String apiKey = aiProperties.getApiKey();
        // 模拟模式或未配置 API Key：返回明确的模拟占位，不实际调用大模型
        if (aiProperties.isMockEnabled() || apiKey == null || apiKey.isBlank()) {
            String local = "【AI 模拟回复】当前为模拟模式，未实际调用大模型。\n"
                    + "如需真实回复，请将 AI_MOCK_ENABLED 设为 false 并配置 OPENAI_API_KEY / OPENAI_BASE_URL / OPENAI_MODEL。";
            logAiUsage(scene + "_LOCAL", prompt, local, true, null);
            return local;
        }
        return requestRemote(scene, prompt, apiKey);
    }

    private boolean isMockMode() {
        String apiKey = aiProperties.getApiKey();
        return aiProperties.isMockEnabled() || apiKey == null || apiKey.isBlank();
    }

    private String requestRemote(String scene, String prompt, String apiKey) {
        try {
            String url = aiProperties.getBaseUrl() + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", aiProperties.getModel());
            body.put("temperature", 0.25);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "你是智慧在线考试系统的教学 AI 助手，回答必须严谨、可直接落地到教学业务。"),
                    Map.of("role", "user", "content", prompt)
            ));

            ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            String content = extractContent(response.getBody());
            String result = content == null || content.isBlank() ? "AI 未返回有效内容" : content;
            logAiUsage(scene, prompt, result, true, null);
            return result;
        } catch (Exception ex) {
            logAiUsage(scene, prompt, null, false, ex.getMessage());
            throw new IllegalStateException("AI 服务调用失败：" + ex.getMessage(), ex);
        }
    }

    private void logAiUsage(String scene, String prompt, String response, boolean success, String errorMessage) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return;
        }
        try {
            Long userId = AuthContext.getSession() == null ? null : AuthContext.getSession().getUser().getId();
            ensureAiUsageLogTable(jdbcTemplate);
            jdbcTemplate.update("""
                    INSERT INTO ai_usage_log (user_id, scene, prompt, `response`, `success`, error_message)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    userId,
                    truncate(scene, 64),
                    truncate(prompt, 8000),
                    truncate(response, 8000),
                    success ? 1 : 0,
                    truncate(errorMessage, 500));
        } catch (Exception ignore) {
            // AI 日志不能影响主业务流程。
        }
    }

    private void ensureAiUsageLogTable(JdbcTemplate jdbcTemplate) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_usage_log (
                      id            BIGINT       NOT NULL AUTO_INCREMENT,
                      user_id       BIGINT       DEFAULT NULL,
                      scene         VARCHAR(64)  DEFAULT NULL,
                      prompt        TEXT         DEFAULT NULL,
                      `response`    TEXT         DEFAULT NULL,
                      `success`     TINYINT      NOT NULL DEFAULT 1,
                      error_message VARCHAR(500) DEFAULT NULL,
                      created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id),
                      KEY idx_ai_log_user (user_id),
                      KEY idx_ai_log_scene_success_time (scene, `success`, created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 调用日志'
                    """);
        } catch (Exception ignored) {
            // 写入日志不能影响 AI 主流程；表不存在时后续 INSERT 也会被外层捕获。
        }
    }

    private String serializeForLog(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return value.toString();
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object choices = body.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object message = first.get("message");
            if (message instanceof Map<?, ?> msg) {
                Object content = msg.get("content");
                return content == null ? null : content.toString();
            }
        }
        return null;
    }

    private void validateBatchRequest(GenerateQuestionBatchRequest request) {
        String type = normalizeCode(request.getQuestionType());
        String difficulty = normalizeCode(request.getDifficulty());
        if (!ALL_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的题型：" + request.getQuestionType());
        }
        if (!ALL_DIFFICULTIES.contains(difficulty)) {
            throw new IllegalArgumentException("不支持的难度：" + request.getDifficulty());
        }
        normalizedCount(request.getCount());
    }

    private void validateMaterialRequest(MaterialQuestionGenerationRequest request) {
        String difficulty = normalizeCode(request.getDifficulty());
        if (!ALL_DIFFICULTIES.contains(difficulty)) {
            throw new IllegalArgumentException("不支持的难度：" + request.getDifficulty());
        }
        if (request.getSubjectId() == null || blankToNull(request.getSubjectName()) == null) {
            throw new IllegalArgumentException("请先选择科目");
        }
        Map<String, Integer> typeCounts = normalizedTypeCounts(request.getTypeCounts());
        int total = typeCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            throw new IllegalArgumentException("请至少选择一种题型数量");
        }
        if (total > 30) {
            throw new IllegalArgumentException("单次最多生成30道题");
        }
    }

    private String buildQuestionDraftPrompt(GenerateQuestionBatchRequest request) {
        String topic = topicName(request);
        return """
                请为在线考试题库生成题目草稿，只返回 JSON，不要输出 Markdown、解释或代码块。
                JSON 格式必须是一个数组，每个元素包含：
                subjectId, knowledgePointId, questionType, difficulty, stem, correctAnswer, analysis, defaultScore, status, options。

                业务要求：
                - 科目：%s（ID=%d）
                - 知识点：%s（ID=%s）
                - 题型：%s
                - 难度：%s
                - 数量：%d
                - 默认分值：%s
                - 题干必须清晰完整，不能出现“根据教材”“如下图”等无法独立作答的表达。
                - 解析必须说明关键思路，不能只重复答案。
                - status 固定为 0，表示草稿。
                - SINGLE_CHOICE 必须给 4 个选项且只有 1 个 correct=true。
                - MULTIPLE_CHOICE 必须给 4 个选项且至少 2 个 correct=true。
                - TRUE_FALSE 必须给 A=正确、B=错误 两个选项且只有 1 个 correct=true。
                - FILL_BLANK 和 SUBJECTIVE 的 options 必须为空数组，correctAnswer 必须可用于阅卷参考。
                - correctAnswer 对客观题填写正确选项标识，例如 A 或 A,B。
                - 不要编造图片、附件、外部材料。
                %s
                """.formatted(
                safeText(request.getSubjectName()),
                request.getSubjectId(),
                topic,
                request.getKnowledgePointId() == null ? "null" : request.getKnowledgePointId(),
                normalizeCode(request.getQuestionType()),
                normalizeCode(request.getDifficulty()),
                normalizedCount(request.getCount()),
                request.getDefaultScore() == null ? BigDecimal.valueOf(5) : request.getDefaultScore(),
                blankToNull(request.getRequirements()) == null ? "" : "- 补充要求：" + request.getRequirements().trim()
        );
    }

    private String buildMaterialQuestionPrompt(String materialText, MaterialQuestionGenerationRequest request, Map<String, Integer> typeCounts) {
        return """
                请根据课程材料生成在线考试题库草稿，只返回 JSON 数组，不要输出 Markdown。
                每道题包含：subjectId, knowledgePointId, questionType, difficulty, stem, correctAnswer, analysis, defaultScore, status, options。
                If material chunks use labels like [page 1 paragraph 2], return sourcePage, sourceParagraph, and sourceExcerpt.

                课程上下文：
                - 科目：%s（ID=%d）
                - 关联知识点：%s（ID=%s）
                - 难度：%s
                - 默认分值：%s
                - 题型数量：%s
                - status 固定为 0。
                - 题目必须来自材料内容，不要编造材料外事实。
                - 解析必须指出材料中的依据或关键概念。
                - SINGLE_CHOICE 4个选项且1个正确；MULTIPLE_CHOICE 4个选项且至少2个正确；TRUE_FALSE 使用 A=正确、B=错误；FILL_BLANK/SUBJECTIVE 不要选项。
                %s

                课程材料：
                %s
                """.formatted(
                safeText(request.getSubjectName()),
                request.getSubjectId(),
                firstNonBlank(request.getKnowledgePointName(), "自动关联材料核心内容"),
                request.getKnowledgePointId() == null ? "null" : request.getKnowledgePointId(),
                normalizeCode(request.getDifficulty()),
                request.getDefaultScore() == null ? BigDecimal.valueOf(5) : request.getDefaultScore(),
                typeCounts,
                blankToNull(request.getRequirements()) == null ? "" : "- 补充要求：" + request.getRequirements().trim(),
                promptText(materialText)
        );
    }

    private String buildMaterialOutlinePrompt(String title, String materialText) {
        return """
                请为老师上传的课程资料生成知识点大纲，只返回 JSON 数组，不要输出 Markdown。
                每个元素包含：title, summary, keywords, sourcePage, sourceParagraph。

                要求：
                - title 是可用于教学和出题的知识点名称。
                - summary 用 1-2 句话概括该知识点考查价值。
                - keywords 用逗号分隔 3-6 个关键词。
                - sourcePage/sourceParagraph 尽量根据资料中的页码、幻灯片或段落位置填写；无法确定时填 1。
                - 最多返回 12 个知识点，按资料顺序排列。

                资料标题：%s
                资料内容：
                %s
                """.formatted(firstNonBlank(title, "未命名资料"), promptText(materialText));
    }

    private List<Map<String, Object>> parseMaterialOutline(String content) {
        try {
            String json = extractJson(content);
            JsonNode root = objectMapper.readTree(json);
            JsonNode outlineNode = root.isArray() ? root : root.get("outline");
            if (outlineNode == null || !outlineNode.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode node : outlineNode) {
                String title = node.path("title").asText("");
                if (blankToNull(title) == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", truncate(title.trim(), 200));
                item.put("summary", truncate(node.path("summary").asText(""), 1000));
                item.put("keywords", truncate(node.path("keywords").asText(""), 500));
                item.put("sourcePage", Math.max(1, node.path("sourcePage").asInt(1)));
                item.put("sourceParagraph", Math.max(1, node.path("sourceParagraph").asInt(1)));
                result.add(item);
                if (result.size() >= 12) {
                    break;
                }
            }
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> buildLocalMaterialOutline(String title, String materialText) {
        List<Map<String, Object>> result = new ArrayList<>();
        String[] lines = safeText(materialText).split("\\R");
        int paragraph = 0;
        for (String raw : lines) {
            String line = raw.replaceAll("\\s+", " ").trim();
            if (line.isBlank()) {
                continue;
            }
            paragraph++;
            if (!looksLikeOutlineTitle(line)) {
                continue;
            }
            result.add(outlineItem(line, "围绕“" + line + "”梳理概念、条件、过程与典型应用，可作为出题覆盖点。", paragraph));
            if (result.size() >= 12) {
                return result;
            }
        }
        if (result.isEmpty()) {
            String compact = safeText(materialText).replaceAll("\\s+", " ").trim();
            if (compact.isBlank()) {
                result.add(outlineItem(firstNonBlank(title, "课程核心内容"), "资料文本较少，建议教师补充讲义后再生成题目。", 1));
            } else {
                int chunkSize = Math.max(80, Math.min(220, compact.length() / 4));
                for (int i = 0; i < compact.length() && result.size() < 6; i += chunkSize) {
                    String part = compact.substring(i, Math.min(compact.length(), i + chunkSize));
                    String itemTitle = inferOutlineTitle(part, result.size() + 1);
                    result.add(outlineItem(itemTitle, "根据资料片段提炼的知识点，适合用于初步覆盖与教师复核。", result.size() + 1));
                }
            }
        }
        return result;
    }

    private boolean looksLikeOutlineTitle(String line) {
        if (line.length() < 4 || line.length() > 60) {
            return false;
        }
        if (line.endsWith("。") || line.endsWith("；") || line.endsWith(";")) {
            return false;
        }
        return line.matches("^(第[一二三四五六七八九十\\d]+[章节].*|[一二三四五六七八九十]+[、.].*|\\d+(?:\\.\\d+)*[、.\\s].*|[#*-]+\\s*.+|.*(概述|原理|流程|机制|方法|应用|特性|结构|案例|总结))$");
    }

    private Map<String, Object> outlineItem(String title, String summary, int paragraph) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", truncate(cleanOutlineTitle(title), 200));
        item.put("summary", truncate(summary, 1000));
        item.put("keywords", truncate(extractKeywords(title), 500));
        item.put("sourcePage", Math.max(1, (paragraph - 1) / 6 + 1));
        item.put("sourceParagraph", Math.max(1, paragraph));
        return item;
    }

    private String cleanOutlineTitle(String title) {
        return safeText(title).replaceFirst("^[#*\\-\\s]+", "").replaceFirst("^\\d+(?:\\.\\d+)*[、.\\s]+", "").trim();
    }

    private String inferOutlineTitle(String text, int index) {
        String cleaned = safeText(text).replaceAll("[，。！？；：,.!?;:].*", "").trim();
        if (cleaned.length() >= 4 && cleaned.length() <= 24) {
            return cleaned;
        }
        return "资料核心知识点 " + index;
    }

    private String extractKeywords(String text) {
        String cleaned = cleanOutlineTitle(text).replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5]+", " ").trim();
        String[] parts = cleaned.split("\\s+");
        List<String> keywords = new ArrayList<>();
        for (String part : parts) {
            if (part.length() >= 2 && keywords.stream().noneMatch(part::equalsIgnoreCase)) {
                keywords.add(part);
            }
            if (keywords.size() >= 6) {
                break;
            }
        }
        return keywords.isEmpty() ? cleaned : String.join(",", keywords);
    }

    private List<AiGeneratedQuestion> parseGeneratedQuestions(String content) {
        try {
            String json = extractJson(content);
            JsonNode root = objectMapper.readTree(json);
            JsonNode questionsNode = root.isArray() ? root : root.get("questions");
            if (questionsNode == null || !questionsNode.isArray()) {
                return List.of();
            }
            List<AiGeneratedQuestion> questions = new ArrayList<>();
            for (JsonNode node : questionsNode) {
                questions.add(objectMapper.convertValue(node, AiGeneratedQuestion.class));
            }
            return questions;
        } catch (Exception ex) {
            throw new IllegalStateException("AI 返回内容无法解析为题目草稿：" + ex.getMessage(), ex);
        }
    }

    private String extractJson(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                text = text.substring(firstLine + 1, lastFence).trim();
            }
        }

        int arrayStart = text.indexOf('[');
        int arrayEnd = text.lastIndexOf(']');
        int objectStart = text.indexOf('{');
        int objectEnd = text.lastIndexOf('}');
        if (arrayStart >= 0 && arrayEnd > arrayStart && (objectStart < 0 || arrayStart < objectStart)) {
            return text.substring(arrayStart, arrayEnd + 1);
        }
        if (objectStart >= 0 && objectEnd > objectStart) {
            return text.substring(objectStart, objectEnd + 1);
        }
        throw new IllegalArgumentException("未找到 JSON 内容");
    }

    private List<AiGeneratedQuestion> buildLocalQuestionDrafts(GenerateQuestionBatchRequest request) {
        List<AiGeneratedQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= normalizedCount(request.getCount()); i++) {
            questions.add(createLocalQuestionDraft(request, i));
        }
        return questions;
    }

    private AiGeneratedQuestion createLocalQuestionDraft(GenerateQuestionBatchRequest request, int index) {
        String type = normalizeCode(request.getQuestionType());
        String difficulty = normalizeCode(request.getDifficulty());
        String topic = topicName(request);
        AiGeneratedQuestion question = new AiGeneratedQuestion();
        question.setSubjectId(request.getSubjectId());
        question.setKnowledgePointId(request.getKnowledgePointId());
        question.setQuestionType(type);
        question.setDifficulty(difficulty);
        question.setDefaultScore(request.getDefaultScore());
        question.setStatus(0);

        if ("SINGLE_CHOICE".equals(type)) {
            question.setStem("关于“" + topic + "”，下列说法正确的是？（AI草稿 " + index + "）");
            question.setOptions(options(
                    option("A", topic + "需要结合概念、条件和应用场景理解", true),
                    option("B", topic + "只需要记住名称即可完成所有判断", false),
                    option("C", topic + "与本课程其他知识点没有关联", false),
                    option("D", topic + "在考试中只能以主观题形式出现", false)
            ));
            question.setCorrectAnswer("A");
        } else if ("MULTIPLE_CHOICE".equals(type)) {
            question.setStem("学习“" + topic + "”时，以下哪些做法有助于正确掌握该知识点？（AI草稿 " + index + "）");
            question.setOptions(options(
                    option("A", "先明确核心概念及适用条件", true),
                    option("B", "结合典型例题分析解题步骤", true),
                    option("C", "忽略题目中的限定条件", false),
                    option("D", "把相近概念进行对比归纳", true)
            ));
            question.setCorrectAnswer("A,B,D");
        } else if ("TRUE_FALSE".equals(type)) {
            question.setStem("判断：掌握“" + topic + "”时，只记忆结论而不理解适用条件，容易导致迁移应用错误。（AI草稿 " + index + "）");
            question.setOptions(options(
                    option("A", "正确", true),
                    option("B", "错误", false)
            ));
            question.setCorrectAnswer("A");
        } else if ("FILL_BLANK".equals(type)) {
            question.setStem("请写出“" + topic + "”中一个必须关注的关键条件或核心概念。（AI草稿 " + index + "）");
            question.setCorrectAnswer(topic + "的核心概念、适用条件或关键步骤之一");
            question.setOptions(List.of());
        } else {
            question.setStem("请结合一个具体例子，说明你对“" + topic + "”的理解，并指出常见错误。（AI草稿 " + index + "）");
            question.setCorrectAnswer("应说明核心概念、适用条件、示例过程，并能指出至少一个常见误区。");
            question.setOptions(List.of());
        }
        question.setAnalysis("本题用于检查学生是否真正理解“" + topic + "”，作答时应先抓住核心概念，再结合条件判断，避免只凭关键词作答。");
        return question;
    }

    private AiGeneratedQuestion normalizeGeneratedQuestion(AiGeneratedQuestion question, GenerateQuestionBatchRequest request, int index) {
        return normalizeQuestionForType(question, request, normalizeCode(request.getQuestionType()), index);
    }

    private AiGeneratedQuestion normalizeQuestionForType(AiGeneratedQuestion question, GenerateQuestionBatchRequest request, String type, int index) {
        return normalizeQuestionForType(question, request, type, index, false);
    }

    private AiGeneratedQuestion normalizeQuestionForType(AiGeneratedQuestion question,
                                                        GenerateQuestionBatchRequest request,
                                                        String type,
                                                        int index,
                                                        boolean allowIncompleteAnswer) {
        String normalizedType = ALL_TYPES.contains(normalizeCode(type)) ? normalizeCode(type) : inferQuestionType(question);
        String difficulty = normalizeCode(request.getDifficulty());
        GenerateQuestionBatchRequest typedRequest = requestForType(request, normalizedType, 1);
        AiGeneratedQuestion normalized = question == null ? createLocalQuestionDraft(typedRequest, index) : question;
        normalized.setSubjectId(request.getSubjectId());
        normalized.setKnowledgePointId(request.getKnowledgePointId());
        normalized.setQuestionType(normalizedType);
        normalized.setDifficulty(difficulty);
        normalized.setDefaultScore(request.getDefaultScore());
        normalized.setStatus(0);

        if (blankToNull(normalized.getStem()) == null) {
            normalized.setStem(createLocalQuestionDraft(typedRequest, index).getStem());
        } else {
            normalized.setStem(normalizeGeneratedQuestionText(normalized.getStem(), "AI generated question stem"));
        }
        normalized.setAnalysis(normalizeGeneratedQuestionText(
                firstNonBlank(normalized.getAnalysis(), "请结合题干条件分析关键概念，按步骤判断后再得出答案。"),
                "AI generated question analysis"));

        if (OBJECTIVE_TYPES.contains(normalizedType)) {
            normalized.setOptions(normalizeObjectiveOptions(normalized, typedRequest, index, allowIncompleteAnswer));
            normalized.setCorrectAnswer(correctLabels(normalized.getOptions()).stream().collect(Collectors.joining(",")));
        } else {
            normalized.setOptions(List.of());
            normalized.setCorrectAnswer(normalizeGeneratedQuestionText(
                    firstNonBlank(normalized.getCorrectAnswer(), createLocalQuestionDraft(typedRequest, index).getCorrectAnswer()),
                    "AI generated question correct answer"));
        }
        return normalized;
    }

    private String normalizeGeneratedQuestionText(String value, String fieldName) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (normalized.length() > MAX_GENERATED_QUESTION_TEXT_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must be 4000 characters or less");
        }
        return normalized;
    }

    private String normalizeGeneratedOptionContent(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("AI generated option content is required");
        }
        if (normalized.length() > MAX_GENERATED_OPTION_CONTENT_LENGTH) {
            throw new IllegalArgumentException("AI generated option content must be 1000 characters or less");
        }
        return normalized;
    }

    private List<AiGeneratedQuestion> stampQuestionRuntime(List<AiGeneratedQuestion> questions, String promptVersion) {
        for (AiGeneratedQuestion question : questions) {
            if (blankToNull(question.getAiModel()) == null) {
                question.setAiModel(currentModel());
            }
            if (blankToNull(question.getPromptVersion()) == null) {
                question.setPromptVersion(promptVersion);
            }
        }
        return questions;
    }

    private List<AiGeneratedQuestionOption> normalizeObjectiveOptions(AiGeneratedQuestion question,
                                                                      GenerateQuestionBatchRequest request,
                                                                      int index,
                                                                      boolean allowIncompleteAnswer) {
        String type = normalizeCode(request.getQuestionType());
        List<AiGeneratedQuestionOption> raw = question.getOptions() == null ? List.of() : question.getOptions();
        if ("TRUE_FALSE".equals(type)) {
            if (!raw.isEmpty()) {
                List<AiGeneratedQuestionOption> normalized = new ArrayList<>();
                int limit = Math.min(raw.size(), 2);
                for (int i = 0; i < limit; i++) {
                    AiGeneratedQuestionOption item = raw.get(i);
                    String content = blankToNull(item.getOptionContent()) == null
                            ? (i == 0 ? "正确" : "错误")
                            : item.getOptionContent().trim();
                    normalized.add(option(String.valueOf((char) ('A' + i)),
                            normalizeGeneratedOptionContent(content),
                            Boolean.TRUE.equals(item.getCorrect())));
                }
                if (blankToNull(question.getCorrectAnswer()) == null && correctLabels(normalized).isEmpty()) {
                    return normalized;
                }
            }
            String answer = safeText(question.getCorrectAnswer()).toUpperCase(Locale.ROOT);
            boolean correctIsB = answer.contains("B") || answer.contains("错") || answer.contains("FALSE");
            if (allowIncompleteAnswer && blankToNull(answer) == null) {
                return options(
                        option("A", "正确", false),
                        option("B", "错误", false)
                );
            }
            return options(
                    option("A", "正确", !correctIsB),
                    option("B", "错误", correctIsB)
            );
        }

        List<AiGeneratedQuestionOption> normalized = new ArrayList<>();
        int limit = Math.min(raw.size(), 8);
        for (int i = 0; i < limit; i++) {
            AiGeneratedQuestionOption item = raw.get(i);
            if (blankToNull(item.getOptionContent()) == null) {
                continue;
            }
            normalized.add(option(String.valueOf((char) ('A' + normalized.size())),
                    normalizeGeneratedOptionContent(item.getOptionContent()),
                    Boolean.TRUE.equals(item.getCorrect())));
        }
        if (normalized.size() < 4) {
            if (allowIncompleteAnswer && !normalized.isEmpty()) {
                return normalized;
            }
            return createLocalQuestionDraft(request, index).getOptions();
        }

        Set<String> answerLabels = new HashSet<>(correctLabels(normalized));
        boolean hasAnswerText = blankToNull(question.getCorrectAnswer()) != null;
        if (answerLabels.isEmpty() && hasAnswerText) {
            answerLabels.addAll(labelsMentionedInAnswer(question.getCorrectAnswer(), normalized));
        }
        if (answerLabels.isEmpty() && allowIncompleteAnswer) {
            return normalized;
        }
        if ("SINGLE_CHOICE".equals(type)) {
            String selected = answerLabels.isEmpty() ? "A" : answerLabels.iterator().next();
            normalized.forEach(option -> option.setCorrect(selected.equals(option.getOptionLabel())));
        } else {
            if (answerLabels.size() < 2) {
                if (allowIncompleteAnswer) {
                    normalized.forEach(option -> option.setCorrect(answerLabels.contains(option.getOptionLabel())));
                    return normalized;
                }
                answerLabels.add("A");
                answerLabels.add("B");
            }
            normalized.forEach(option -> option.setCorrect(answerLabels.contains(option.getOptionLabel())));
        }
        return normalized;
    }

    private Set<String> labelsMentionedInAnswer(String correctAnswer, List<AiGeneratedQuestionOption> options) {
        String answer = safeText(correctAnswer).toUpperCase(Locale.ROOT);
        Set<String> labels = new HashSet<>();
        for (AiGeneratedQuestionOption option : options) {
            String label = option.getOptionLabel();
            if (label != null && !label.isBlank() && answer.contains(label.toUpperCase(Locale.ROOT))) {
                labels.add(label);
            }
        }
        return labels;
    }

    private Set<String> correctLabels(List<AiGeneratedQuestionOption> options) {
        return options.stream()
                .filter(option -> Boolean.TRUE.equals(option.getCorrect()))
                .map(AiGeneratedQuestionOption::getOptionLabel)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private List<AiGeneratedQuestion> normalizeMaterialQuestions(List<AiGeneratedQuestion> parsed,
                                                                 MaterialQuestionGenerationRequest request,
                                                                 Map<String, Integer> typeCounts) {
        String topic = materialTopic("", request);
        Map<String, List<AiGeneratedQuestion>> bucket = new LinkedHashMap<>();
        for (String type : ALL_TYPES) {
            bucket.put(type, new ArrayList<>());
        }
        for (AiGeneratedQuestion question : parsed) {
            String type = ALL_TYPES.contains(normalizeCode(question.getQuestionType())) ? normalizeCode(question.getQuestionType()) : inferQuestionType(question);
            bucket.computeIfAbsent(type, ignored -> new ArrayList<>()).add(question);
        }

        List<AiGeneratedQuestion> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            String type = entry.getKey();
            int count = entry.getValue();
            GenerateQuestionBatchRequest typed = requestFromMaterial(request, type, count, topic);
            List<AiGeneratedQuestion> candidates = bucket.getOrDefault(type, List.of());
            for (int i = 0; i < count; i++) {
                AiGeneratedQuestion candidate = i < candidates.size() ? candidates.get(i) : createLocalQuestionDraft(typed, i + 1);
                result.add(normalizeQuestionForType(candidate, typed, type, i + 1));
            }
        }
        return result;
    }

    private boolean hasRequestedCounts(List<AiGeneratedQuestion> questions, Map<String, Integer> typeCounts) {
        Map<String, Long> actual = questions.stream().collect(Collectors.groupingBy(q -> normalizeCode(q.getQuestionType()), Collectors.counting()));
        return typeCounts.entrySet().stream().allMatch(entry -> actual.getOrDefault(entry.getKey(), 0L) >= entry.getValue());
    }

    private List<AiGeneratedQuestion> buildLocalMaterialQuestionDrafts(String materialText,
                                                                       MaterialQuestionGenerationRequest request,
                                                                       Map<String, Integer> typeCounts) {
        String topic = materialTopic(materialText, request);
        String snippet = materialSnippet(materialText);
        List<AiGeneratedQuestion> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            GenerateQuestionBatchRequest typed = requestFromMaterial(request, entry.getKey(), entry.getValue(), topic);
            for (int i = 1; i <= entry.getValue(); i++) {
                AiGeneratedQuestion question = createLocalQuestionDraft(typed, i);
                question.setAnalysis("依据课程材料要点“" + snippet + "”，本题考察学生能否抓住概念、条件和应用场景。教师可在保存前继续补充更精确的材料出处。");
                result.add(question);
            }
        }
        return result;
    }

    private Set<String> extractAnswerLabels(String answer) {
        Set<String> labels = new HashSet<>();
        String upper = safeText(answer).toUpperCase(Locale.ROOT);
        for (char label = 'A'; label <= 'H'; label++) {
            if (upper.indexOf(label) >= 0) {
                labels.add(String.valueOf(label));
            }
        }
        return labels;
    }

    private String inferQuestionType(AiGeneratedQuestion question) {
        if (question == null) {
            return "SINGLE_CHOICE";
        }
        return inferQuestionType(question.getStem(), question.getOptions(), question.getCorrectAnswer());
    }

    private String inferQuestionType(String stem, List<AiGeneratedQuestionOption> options, String answer) {
        if (options != null && !options.isEmpty()) {
            if (options.size() == 2 && options.stream().map(option -> safeText(option.getOptionContent())).collect(Collectors.joining("|")).matches(".*(正确|错误|对|错|true|false).*")) {
                return "TRUE_FALSE";
            }
            return extractAnswerLabels(answer).size() > 1 ? "MULTIPLE_CHOICE" : "SINGLE_CHOICE";
        }
        String text = safeText(stem);
        if (text.contains("____") || text.contains("______") || text.matches(".*[（(]\\s*[）)].*")) {
            return "FILL_BLANK";
        }
        return "SUBJECTIVE";
    }

    private Map<String, Integer> normalizedTypeCounts(Map<String, Integer> raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String type : ALL_TYPES) {
            int value = raw == null ? 0 : raw.getOrDefault(type, raw.getOrDefault(type.toLowerCase(Locale.ROOT), 0));
            if (value > 0) {
                if (value > MAX_MATERIAL_TYPE_COUNT) {
                    throw new IllegalArgumentException("Each material question type count must be 30 or less");
                }
                result.put(type, value);
            }
        }
        return result;
    }

    private GenerateQuestionBatchRequest requestForType(GenerateQuestionBatchRequest base, String type, int count) {
        GenerateQuestionBatchRequest request = new GenerateQuestionBatchRequest();
        request.setSubjectId(base.getSubjectId());
        request.setSubjectName(base.getSubjectName());
        request.setKnowledgePointId(base.getKnowledgePointId());
        request.setKnowledgePointName(base.getKnowledgePointName());
        request.setQuestionType(type);
        request.setDifficulty(base.getDifficulty());
        request.setCount(count);
        request.setDefaultScore(base.getDefaultScore());
        request.setRequirements(base.getRequirements());
        return request;
    }

    private GenerateQuestionBatchRequest requestFromMaterial(MaterialQuestionGenerationRequest source, String type, int count, String topic) {
        GenerateQuestionBatchRequest request = new GenerateQuestionBatchRequest();
        request.setSubjectId(source.getSubjectId());
        request.setSubjectName(source.getSubjectName());
        request.setKnowledgePointId(source.getKnowledgePointId());
        request.setKnowledgePointName(firstNonBlank(source.getKnowledgePointName(), topic));
        request.setQuestionType(type);
        request.setDifficulty(source.getDifficulty());
        request.setCount(count);
        request.setDefaultScore(source.getDefaultScore());
        request.setRequirements(source.getRequirements());
        return request;
    }

    private String materialTopic(String materialText, MaterialQuestionGenerationRequest request) {
        String knowledgePoint = blankToNull(request.getKnowledgePointName());
        if (knowledgePoint != null) {
            return knowledgePoint;
        }
        for (String line : safeText(materialText).split("\\R")) {
            String cleaned = line.replaceAll("[#*\\-\\d.、：:]+", "").trim();
            if (cleaned.length() >= 4 && cleaned.length() <= 40) {
                return cleaned;
            }
        }
        return safeText(request.getSubjectName());
    }

    private String materialSnippet(String materialText) {
        String compact = safeText(materialText).replaceAll("\\s+", " ").trim();
        if (compact.isBlank()) {
            return "课程材料核心内容";
        }
        return compact.length() > 60 ? compact.substring(0, 60) + "..." : compact;
    }

    private String promptText(String text) {
        String value = safeText(text).trim();
        return value.length() > 18_000 ? value.substring(0, 18_000) : value;
    }

    private String buildWrongQuestionPrompt(WrongQuestionExplainRequest request) {
        String options = optionsToText(request.getOptions());
        return """
                请作为耐心但高效的考试辅导老师，为学生讲解一道错题。
                输出中文，按以下四段组织，每段 2-4 句：错因定位、正确思路、关键知识点、下次作答提醒。
                段落标题必须使用【错因定位】、【正确思路】、【关键知识点】、【下次作答提醒】。
                不要使用 Markdown 标记、代码块或 LaTeX 语法；数学表达式请用普通文本，例如“向量a·向量b=0”“2m-4=0”。
                不要泛泛鼓励，不要编造题外背景。

                题型：%s
                错误次数：%s
                题干：%s
                选项：
                %s
                学生答案：%s
                正确答案：%s
                原解析：%s
                """.formatted(
                firstNonBlank(request.getQuestionType(), "未标注"),
                request.getWrongCount() == null ? "未记录" : request.getWrongCount(),
                safeText(request.getStem()),
                options.isBlank() ? "无" : options,
                firstNonBlank(request.getStudentAnswer(), "未提供"),
                firstNonBlank(request.getCorrectAnswer(), "未提供"),
                firstNonBlank(request.getAnalysis(), "暂无")
        );
    }

    private String buildLocalWrongQuestionExplanation(WrongQuestionExplainRequest request) {
        String type = firstNonBlank(request.getQuestionType(), "题目");
        String answer = firstNonBlank(request.getCorrectAnswer(), "参考答案未提供");
        String analysis = firstNonBlank(request.getAnalysis(), "这道题需要先定位题干条件，再对应到核心概念判断。");
        int wrongCount = request.getWrongCount() == null ? 1 : request.getWrongCount();
        return "【错因定位】\n"
                + "这道" + type + "已经错了 " + wrongCount + " 次，优先检查是否只抓住了题干关键词，却没有核对限定条件。"
                + (request.getStudentAnswer() == null || request.getStudentAnswer().isBlank() ? "" : "\n你的答案是：" + request.getStudentAnswer() + "，需要和参考答案逐项对照。")
                + "\n\n【正确思路】\n"
                + "先把题干中的条件圈出来，再判断每个选项或答案点是否满足这些条件。参考答案是：" + answer + "。"
                + "\n\n【关键知识点】\n"
                + analysis
                + "\n\n【下次作答提醒】\n"
                + "遇到同类题时先写出判断依据，再选答案；如果是选择题，逐项排除比直接凭印象选择更稳。";
    }

    private String optionsToText(List<AiGeneratedQuestionOption> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (AiGeneratedQuestionOption option : options) {
            joiner.add(firstNonBlank(option.getOptionLabel(), "?") + ". " + safeText(option.getOptionContent()));
        }
        return joiner.toString();
    }

    private List<AiGeneratedQuestionOption> options(AiGeneratedQuestionOption... options) {
        return new ArrayList<>(List.of(options));
    }

    private AiGeneratedQuestionOption option(String label, String content, boolean correct) {
        AiGeneratedQuestionOption option = new AiGeneratedQuestionOption();
        option.setOptionLabel(label);
        option.setOptionContent(content);
        option.setCorrect(correct);
        return option;
    }

    private String topicName(GenerateQuestionBatchRequest request) {
        String knowledgePoint = blankToNull(request.getKnowledgePointName());
        return knowledgePoint == null ? safeText(request.getSubjectName()) : knowledgePoint;
    }

    private int normalizedCount(Integer count) {
        int value = count == null ? 3 : count;
        if (value < 1) {
            throw new IllegalArgumentException("AI batch question count must be at least 1");
        }
        if (value > MAX_BATCH_QUESTION_COUNT) {
            throw new IllegalArgumentException("AI batch question count must be 10 or less");
        }
        return value;
    }

    private String normalizeCode(String value) {
        return safeText(value).trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String fallback) {
        String trimmed = blankToNull(first);
        return trimmed == null ? fallback : trimmed;
    }

    private String blankToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
