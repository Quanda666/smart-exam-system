package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.dto.ai.AiGeneratedQuestion;
import com.smartexam.dto.ai.AiGeneratedQuestionOption;
import com.smartexam.dto.ai.GenerateQuestionBatchRequest;
import com.smartexam.dto.ai.MaterialQuestionGenerationRequest;
import com.smartexam.dto.ai.SaveGeneratedQuestionsRequest;
import com.smartexam.dto.ai.SuggestReviewRequest;
import com.smartexam.dto.ai.WrongQuestionExplainRequest;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.question.QuestionOptionRequest;
import com.smartexam.dto.question.QuestionRequest;
import com.smartexam.service.AiService;
import com.smartexam.service.AiStatusService;
import com.smartexam.service.DocumentTextExtractorService;
import com.smartexam.service.QuestionBankService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final int MAX_SOURCE_DETAIL_LENGTH = 255;

    private final AiStatusService aiStatusService;
    private final AiService aiService;
    private final DocumentTextExtractorService documentTextExtractorService;
    private final QuestionBankService questionBankService;

    public AiController(AiStatusService aiStatusService,
                        AiService aiService,
                        DocumentTextExtractorService documentTextExtractorService,
                        QuestionBankService questionBankService) {
        this.aiStatusService = aiStatusService;
        this.aiService = aiService;
        this.documentTextExtractorService = documentTextExtractorService;
        this.questionBankService = questionBankService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(aiStatusService.getStatus());
    }

    @PostMapping("/questions/generate")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<List<AiGeneratedQuestion>> generateQuestionDrafts(@Valid @RequestBody GenerateQuestionBatchRequest request) {
        return ApiResponse.ok(withSource(aiService.generateQuestionDrafts(request), "AI_GENERATED", "AI generator"));
    }

    @PostMapping(value = "/questions/generate-from-material", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<List<AiGeneratedQuestion>> generateFromMaterial(@RequestPart("file") MultipartFile file,
                                                                       @RequestParam Long subjectId,
                                                                       @RequestParam String subjectName,
                                                                       @RequestParam(required = false) Long knowledgePointId,
                                                                       @RequestParam(required = false) String knowledgePointName,
                                                                       @RequestParam(defaultValue = "MEDIUM") String difficulty,
                                                                       @RequestParam(defaultValue = "5") BigDecimal defaultScore,
                                                                       @RequestParam(required = false) String requirements,
                                                                       @RequestParam(defaultValue = "0") Integer singleChoiceCount,
                                                                       @RequestParam(defaultValue = "0") Integer multipleChoiceCount,
                                                                       @RequestParam(defaultValue = "0") Integer trueFalseCount,
                                                                       @RequestParam(defaultValue = "0") Integer fillBlankCount,
                                                                       @RequestParam(defaultValue = "0") Integer subjectiveCount) {
        MaterialQuestionGenerationRequest request = new MaterialQuestionGenerationRequest();
        request.setSubjectId(subjectId);
        request.setSubjectName(subjectName);
        request.setKnowledgePointId(knowledgePointId);
        request.setKnowledgePointName(knowledgePointName);
        request.setDifficulty(difficulty);
        request.setDefaultScore(defaultScore);
        request.setRequirements(requirements);
        request.setTypeCounts(typeCounts(singleChoiceCount, multipleChoiceCount, trueFalseCount, fillBlankCount, subjectiveCount));
        String text = documentTextExtractorService.extract(file);
        return ApiResponse.ok(withSource(aiService.generateQuestionDraftsFromMaterial(text, request),
                "AI_MATERIAL", sourceDetail(file, "Course material generation")));
    }

    @PostMapping("/questions/save")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> saveGeneratedQuestions(@Valid @RequestBody SaveGeneratedQuestionsRequest request) {
        AuthUser user = currentUser();
        List<Map<String, Object>> saved = request.getQuestions().stream()
                .map(this::toQuestionRequest)
                .map(question -> questionBankService.createQuestion(question, user))
                .toList();
        List<Object> questionReviewLogIds = saved.stream()
                .map(question -> question.get("questionReviewLogId"))
                .filter(id -> id != null)
                .toList();
        return ApiResponse.ok("AI question drafts saved", Map.of(
                "savedCount", saved.size(),
                "questionReviewLogIds", questionReviewLogIds,
                "questions", saved
        ));
    }

    @PostMapping("/wrong-question/explain")
    @RequireRoles({"STUDENT"})
    public ApiResponse<String> explainWrongQuestion(@Valid @RequestBody WrongQuestionExplainRequest request) {
        return ApiResponse.ok(aiService.explainWrongQuestion(request, currentUser()));
    }

    @PostMapping("/suggest-review")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<String> suggestReview(@Valid @RequestBody SuggestReviewRequest request) {
        return ApiResponse.ok(aiService.suggestReview(request));
    }

    private AuthUser currentUser() {
        return AuthContext.requireSession().getUser();
    }

    private QuestionRequest toQuestionRequest(AiGeneratedQuestion draft) {
        QuestionRequest request = new QuestionRequest();
        request.setSubjectId(draft.getSubjectId());
        request.setKnowledgePointId(draft.getKnowledgePointId());
        request.setQuestionType(draft.getQuestionType());
        request.setDifficulty(draft.getDifficulty());
        request.setStem(draft.getStem());
        request.setCorrectAnswer(draft.getCorrectAnswer());
        request.setAnalysis(draft.getAnalysis());
        request.setDefaultScore(draft.getDefaultScore());
        request.setStatus(0);
        request.setSourceType(draft.getSourceType());
        request.setSourceDetail(draft.getSourceDetail());
        request.setMaterialId(draft.getMaterialId());
        request.setSourcePage(draft.getSourcePage());
        request.setSourceParagraph(draft.getSourceParagraph());
        request.setSourceExcerpt(draft.getSourceExcerpt());
        request.setAiModel(draft.getAiModel());
        request.setPromptVersion(draft.getPromptVersion());
        List<AiGeneratedQuestionOption> options = draft.getOptions() == null ? List.of() : draft.getOptions();
        request.setOptions(options.stream().map(this::toOptionRequest).toList());
        return request;
    }

    private List<AiGeneratedQuestion> withSource(List<AiGeneratedQuestion> questions, String sourceType, String sourceDetail) {
        for (AiGeneratedQuestion question : questions) {
            question.setSourceType(sourceType);
            question.setSourceDetail(sourceDetail);
        }
        return questions;
    }

    private String sourceDetail(MultipartFile file, String fallback) {
        String filename = file.getOriginalFilename();
        String detail = filename == null || filename.isBlank() ? fallback : fallback + ": " + filename;
        if (detail.length() > MAX_SOURCE_DETAIL_LENGTH) {
            throw new IllegalArgumentException("AI source detail must be 255 characters or less");
        }
        return detail;
    }

    private QuestionOptionRequest toOptionRequest(AiGeneratedQuestionOption option) {
        QuestionOptionRequest request = new QuestionOptionRequest();
        request.setOptionLabel(option.getOptionLabel());
        request.setOptionContent(option.getOptionContent());
        request.setCorrect(option.getCorrect());
        return request;
    }

    private Map<String, Integer> typeCounts(Integer singleChoice,
                                            Integer multipleChoice,
                                            Integer trueFalse,
                                            Integer fillBlank,
                                            Integer subjective) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("SINGLE_CHOICE", safeCount(singleChoice));
        counts.put("MULTIPLE_CHOICE", safeCount(multipleChoice));
        counts.put("TRUE_FALSE", safeCount(trueFalse));
        counts.put("FILL_BLANK", safeCount(fillBlank));
        counts.put("SUBJECTIVE", safeCount(subjective));
        return counts;
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
