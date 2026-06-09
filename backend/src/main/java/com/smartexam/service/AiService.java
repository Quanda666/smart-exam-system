package com.smartexam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartexam.config.AiProperties;
import com.smartexam.dto.ai.AiGeneratedQuestion;
import com.smartexam.dto.ai.AiGeneratedQuestionOption;
import com.smartexam.dto.ai.GenerateQuestionBatchRequest;
import com.smartexam.dto.ai.MaterialQuestionGenerationRequest;
import com.smartexam.dto.ai.SuggestReviewRequest;
import com.smartexam.dto.ai.WrongQuestionExplainRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    private static final List<String> OBJECTIVE_TYPES = List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");
    private static final List<String> ALL_TYPES = List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SUBJECTIVE");
    private static final List<String> ALL_DIFFICULTIES = List.of("EASY", "MEDIUM", "HARD");
    private static final Pattern QUESTION_START = Pattern.compile("^(?:第\\s*\\d+\\s*题\\s*[：:]?|\\d{1,3}\\s*[.、)）]\\s*).+");
    private static final Pattern OPTION_LINE = Pattern.compile("^[（(]?([A-Ha-h])[）).、]\\s*(.+)$");
    private static final Pattern ANSWER_LINE = Pattern.compile("^(?:正确答案|参考答案|答案)\\s*[：:]\\s*(.*)$");
    private static final Pattern ANALYSIS_LINE = Pattern.compile("^(?:答案解析|题目解析|解析)\\s*[：:]\\s*(.*)$");

    private final AiProperties aiProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiService(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = Math.max(1, aiProperties.getTimeoutSeconds()) * 1000;
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    public List<AiGeneratedQuestion> generateQuestionDrafts(GenerateQuestionBatchRequest request) {
        validateBatchRequest(request);
        if (isMockMode()) {
            return buildLocalQuestionDrafts(request);
        }

        String prompt = buildQuestionDraftPrompt(request);
        String content = requestRemote(prompt, aiProperties.getApiKey());
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
        return normalized;
    }

    public List<AiGeneratedQuestion> importQuestionsFromDocument(String documentText, GenerateQuestionBatchRequest defaults) {
        validateDocumentDefaults(defaults);
        if (!isMockMode()) {
            try {
                List<AiGeneratedQuestion> parsed = parseGeneratedQuestions(requestRemote(buildQuestionImportPrompt(documentText, defaults), aiProperties.getApiKey()));
                List<AiGeneratedQuestion> normalized = normalizeImportedQuestions(parsed, defaults);
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            } catch (Exception ignore) {
                // 真实模型解析失败时继续走本地规则，避免导入入口直接不可用。
            }
        }

        List<AiGeneratedQuestion> local = parseQuestionDocumentLocally(documentText, defaults);
        if (local.isEmpty()) {
            throw new IllegalArgumentException("未识别到题目，请检查文档格式，建议使用“1.题干 + A.选项 + 答案 + 解析”的结构");
        }
        return local;
    }

    public List<AiGeneratedQuestion> generateQuestionDraftsFromMaterial(String materialText, MaterialQuestionGenerationRequest request) {
        validateMaterialRequest(request);
        Map<String, Integer> typeCounts = normalizedTypeCounts(request.getTypeCounts());
        if (!isMockMode()) {
            try {
                List<AiGeneratedQuestion> parsed = parseGeneratedQuestions(requestRemote(buildMaterialQuestionPrompt(materialText, request, typeCounts), aiProperties.getApiKey()));
                List<AiGeneratedQuestion> normalized = normalizeMaterialQuestions(parsed, request, typeCounts);
                if (hasRequestedCounts(normalized, typeCounts)) {
                    return normalized;
                }
            } catch (Exception ignore) {
                // 模型输出不稳定时回退到本地草稿，老师仍然能继续微调。
            }
        }
        return buildLocalMaterialQuestionDrafts(materialText, request, typeCounts);
    }

    public String explainWrongQuestion(WrongQuestionExplainRequest request) {
        if (isMockMode()) {
            return buildLocalWrongQuestionExplanation(request);
        }
        return requestRemote(buildWrongQuestionPrompt(request), aiProperties.getApiKey());
    }

    public String suggestReview(SuggestReviewRequest request) {
        String prompt = "请对以下主观题答案进行评分，并给出评语。\n题目：" + request.getQuestion()
                + "\n参考答案：" + request.getCorrectAnswer() + "\n学生答案：" + request.getStudentAnswer();
        return callAi(prompt);
    }

    private String callAi(String prompt) {
        String apiKey = aiProperties.getApiKey();
        // 模拟模式或未配置 API Key：返回明确的模拟占位，不实际调用大模型
        if (aiProperties.isMockEnabled() || apiKey == null || apiKey.isBlank()) {
            return "【AI 模拟回复】当前为模拟模式，未实际调用大模型。\n"
                    + "如需真实回复，请将 AI_MOCK_ENABLED 设为 false 并配置 OPENAI_API_KEY / OPENAI_BASE_URL / OPENAI_MODEL。";
        }
        return requestRemote(prompt, apiKey);
    }

    private boolean isMockMode() {
        String apiKey = aiProperties.getApiKey();
        return aiProperties.isMockEnabled() || apiKey == null || apiKey.isBlank();
    }

    private String requestRemote(String prompt, String apiKey) {
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
            return content == null || content.isBlank() ? "AI 未返回有效内容" : content;
        } catch (Exception ex) {
            throw new IllegalStateException("AI 服务调用失败：" + ex.getMessage(), ex);
        }
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
    }

    private void validateDocumentDefaults(GenerateQuestionBatchRequest request) {
        String difficulty = normalizeCode(request.getDifficulty());
        if (!ALL_DIFFICULTIES.contains(difficulty)) {
            throw new IllegalArgumentException("不支持的难度：" + request.getDifficulty());
        }
        if (request.getSubjectId() == null || blankToNull(request.getSubjectName()) == null) {
            throw new IllegalArgumentException("请先选择科目");
        }
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

    private String buildQuestionImportPrompt(String documentText, GenerateQuestionBatchRequest defaults) {
        return """
                请从老师上传的题目文档中识别题目，并转换成在线考试题库草稿。只返回 JSON 数组，不要输出 Markdown。
                每道题包含：subjectId, knowledgePointId, questionType, difficulty, stem, correctAnswer, analysis, defaultScore, status, options。

                识别规则：
                - 保留原题语义，不要额外改写或创造新题。
                - 自动判断题型：SINGLE_CHOICE、MULTIPLE_CHOICE、TRUE_FALSE、FILL_BLANK、SUBJECTIVE。
                - 客观题必须抽取选项并标记 correct；若文档没有答案，可先给出最可能答案，但 analysis 中提示老师确认。
                - 非客观题 options 为空数组，correctAnswer 放参考答案。
                - status 固定为 0。
                - 所有题目使用 subjectId=%d、knowledgePointId=%s、difficulty=%s、defaultScore=%s。

                题目文档：
                %s
                """.formatted(
                defaults.getSubjectId(),
                defaults.getKnowledgePointId() == null ? "null" : defaults.getKnowledgePointId(),
                normalizeCode(defaults.getDifficulty()),
                defaults.getDefaultScore() == null ? BigDecimal.valueOf(5) : defaults.getDefaultScore(),
                promptText(documentText)
        );
    }

    private String buildMaterialQuestionPrompt(String materialText, MaterialQuestionGenerationRequest request, Map<String, Integer> typeCounts) {
        return """
                请根据课程材料生成在线考试题库草稿，只返回 JSON 数组，不要输出 Markdown。
                每道题包含：subjectId, knowledgePointId, questionType, difficulty, stem, correctAnswer, analysis, defaultScore, status, options。

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
            normalized.setStem(truncate(normalized.getStem().trim(), 4000));
        }
        normalized.setAnalysis(truncate(firstNonBlank(normalized.getAnalysis(), "请结合题干条件分析关键概念，按步骤判断后再得出答案。"), 4000));

        if (OBJECTIVE_TYPES.contains(normalizedType)) {
            normalized.setOptions(normalizeObjectiveOptions(normalized, typedRequest, index));
            normalized.setCorrectAnswer(correctLabels(normalized.getOptions()).stream().collect(Collectors.joining(",")));
        } else {
            normalized.setOptions(List.of());
            normalized.setCorrectAnswer(truncate(firstNonBlank(normalized.getCorrectAnswer(), createLocalQuestionDraft(typedRequest, index).getCorrectAnswer()), 4000));
        }
        return normalized;
    }

    private List<AiGeneratedQuestionOption> normalizeObjectiveOptions(AiGeneratedQuestion question, GenerateQuestionBatchRequest request, int index) {
        String type = normalizeCode(request.getQuestionType());
        if ("TRUE_FALSE".equals(type)) {
            String answer = safeText(question.getCorrectAnswer()).toUpperCase(Locale.ROOT);
            boolean correctIsB = answer.contains("B") || answer.contains("错") || answer.contains("FALSE");
            return options(
                    option("A", "正确", !correctIsB),
                    option("B", "错误", correctIsB)
            );
        }

        List<AiGeneratedQuestionOption> raw = question.getOptions() == null ? List.of() : question.getOptions();
        List<AiGeneratedQuestionOption> normalized = new ArrayList<>();
        int limit = Math.min(raw.size(), 6);
        for (int i = 0; i < limit; i++) {
            AiGeneratedQuestionOption item = raw.get(i);
            if (blankToNull(item.getOptionContent()) == null) {
                continue;
            }
            normalized.add(option(String.valueOf((char) ('A' + normalized.size())),
                    truncate(item.getOptionContent().trim(), 1000),
                    Boolean.TRUE.equals(item.getCorrect())));
        }
        if (normalized.size() < 4) {
            return createLocalQuestionDraft(request, index).getOptions();
        }

        Set<String> answerLabels = new HashSet<>(correctLabels(normalized));
        if (answerLabels.isEmpty()) {
            answerLabels.addAll(labelsMentionedInAnswer(question.getCorrectAnswer(), normalized));
        }
        if ("SINGLE_CHOICE".equals(type)) {
            String selected = answerLabels.isEmpty() ? "A" : answerLabels.iterator().next();
            normalized.forEach(option -> option.setCorrect(selected.equals(option.getOptionLabel())));
        } else {
            if (answerLabels.size() < 2) {
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

    private List<AiGeneratedQuestion> normalizeImportedQuestions(List<AiGeneratedQuestion> parsed, GenerateQuestionBatchRequest defaults) {
        List<AiGeneratedQuestion> normalized = new ArrayList<>();
        for (AiGeneratedQuestion question : parsed) {
            if (normalized.size() >= 100) {
                break;
            }
            String type = ALL_TYPES.contains(normalizeCode(question.getQuestionType())) ? normalizeCode(question.getQuestionType()) : inferQuestionType(question);
            normalized.add(normalizeQuestionForType(question, defaults, type, normalized.size() + 1));
        }
        return normalized;
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

    private List<AiGeneratedQuestion> parseQuestionDocumentLocally(String documentText, GenerateQuestionBatchRequest defaults) {
        List<List<String>> blocks = splitQuestionBlocks(documentText);
        List<AiGeneratedQuestion> result = new ArrayList<>();
        for (List<String> block : blocks) {
            AiGeneratedQuestion parsed = parseQuestionBlock(block, defaults, result.size() + 1);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    private List<List<String>> splitQuestionBlocks(String documentText) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String rawLine : safeText(documentText).split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (QUESTION_START.matcher(line).matches() && !current.isEmpty()) {
                blocks.add(current);
                current = new ArrayList<>();
            }
            current.add(line);
        }
        if (!current.isEmpty()) {
            blocks.add(current);
        }
        return blocks;
    }

    private AiGeneratedQuestion parseQuestionBlock(List<String> block, GenerateQuestionBatchRequest defaults, int index) {
        StringBuilder stem = new StringBuilder();
        StringBuilder analysis = new StringBuilder();
        List<AiGeneratedQuestionOption> parsedOptions = new ArrayList<>();
        String answer = "";
        boolean readingAnalysis = false;

        for (String raw : block) {
            String line = raw.trim();
            Matcher answerMatcher = ANSWER_LINE.matcher(line);
            if (answerMatcher.matches()) {
                answer = answerMatcher.group(1).trim();
                readingAnalysis = false;
                continue;
            }
            Matcher analysisMatcher = ANALYSIS_LINE.matcher(line);
            if (analysisMatcher.matches()) {
                analysis.append(analysisMatcher.group(1).trim());
                readingAnalysis = true;
                continue;
            }
            Matcher optionMatcher = OPTION_LINE.matcher(line);
            if (optionMatcher.matches()) {
                parsedOptions.add(option(optionMatcher.group(1).toUpperCase(Locale.ROOT), optionMatcher.group(2).trim(), false));
                readingAnalysis = false;
                continue;
            }
            if (readingAnalysis) {
                analysis.append(analysis.isEmpty() ? "" : "\n").append(line);
            } else {
                stem.append(stem.isEmpty() ? "" : "\n").append(stripQuestionNumber(line));
            }
        }

        String cleanStem = cleanStem(stem.toString());
        if (cleanStem.isBlank()) {
            return null;
        }

        AiGeneratedQuestion question = new AiGeneratedQuestion();
        question.setSubjectId(defaults.getSubjectId());
        question.setKnowledgePointId(defaults.getKnowledgePointId());
        question.setDifficulty(normalizeCode(defaults.getDifficulty()));
        question.setDefaultScore(defaults.getDefaultScore());
        question.setStatus(0);
        question.setStem(cleanStem);
        question.setAnalysis(analysis.isEmpty() ? "由文档自动识别，请教师确认答案和解析后保存。" : analysis.toString().trim());

        String type = inferQuestionType(cleanStem, parsedOptions, answer);
        question.setQuestionType(type);
        if (OBJECTIVE_TYPES.contains(type)) {
            List<AiGeneratedQuestionOption> options = parsedOptions.isEmpty() && "TRUE_FALSE".equals(type)
                    ? options(option("A", "正确", false), option("B", "错误", false))
                    : parsedOptions;
            markCorrectOptions(options, answer, type);
            question.setOptions(options);
            question.setCorrectAnswer(correctLabels(options).stream().collect(Collectors.joining(",")));
        } else {
            question.setOptions(List.of());
            question.setCorrectAnswer(firstNonBlank(answer, "请教师补充参考答案"));
        }
        return normalizeQuestionForType(question, defaults, type, index);
    }

    private void markCorrectOptions(List<AiGeneratedQuestionOption> options, String answer, String type) {
        Set<String> labels = extractAnswerLabels(answer);
        if ("TRUE_FALSE".equals(type) && labels.isEmpty()) {
            String upper = safeText(answer).toUpperCase(Locale.ROOT);
            labels.add(upper.contains("错") || upper.contains("FALSE") ? "B" : "A");
        }
        if (labels.isEmpty() && !options.isEmpty()) {
            labels.add(options.get(0).getOptionLabel());
        }
        for (AiGeneratedQuestionOption option : options) {
            option.setCorrect(labels.contains(option.getOptionLabel()));
        }
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
                result.put(type, Math.min(value, 30));
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

    private String stripQuestionNumber(String line) {
        return line.replaceFirst("^(?:第\\s*\\d+\\s*题\\s*[：:]?|\\d{1,3}\\s*[.、)）]\\s*)", "").trim();
    }

    private String cleanStem(String stem) {
        return stem.replaceFirst("^[【\\[]?(?:单选题|多选题|判断题|填空题|主观题|简答题)[】\\]]?\\s*", "").trim();
    }

    private String buildWrongQuestionPrompt(WrongQuestionExplainRequest request) {
        String options = optionsToText(request.getOptions());
        return """
                请作为耐心但高效的考试辅导老师，为学生讲解一道错题。
                输出中文，按以下四段组织，每段 2-4 句：错因定位、正确思路、关键知识点、下次作答提醒。
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
        return Math.max(1, Math.min(value, 10));
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
