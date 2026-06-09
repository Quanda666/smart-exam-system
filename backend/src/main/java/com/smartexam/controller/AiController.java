package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.dto.ai.AiGeneratedQuestion;
import com.smartexam.dto.ai.AiGeneratedQuestionOption;
import com.smartexam.dto.ai.ExplainRequest;
import com.smartexam.dto.ai.GenerateQuestionBatchRequest;
import com.smartexam.dto.ai.GenerateQuestionRequest;
import com.smartexam.dto.ai.SaveGeneratedQuestionsRequest;
import com.smartexam.dto.ai.SuggestReviewRequest;
import com.smartexam.dto.ai.WrongQuestionExplainRequest;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.question.QuestionOptionRequest;
import com.smartexam.dto.question.QuestionRequest;
import com.smartexam.service.AiService;
import com.smartexam.service.AiStatusService;
import com.smartexam.service.QuestionBankService;
import com.smartexam.service.RoleAccessService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiStatusService aiStatusService;
    private final AiService aiService;
    private final QuestionBankService questionBankService;
    private final RoleAccessService roleAccessService;

    public AiController(AiStatusService aiStatusService,
                        AiService aiService,
                        QuestionBankService questionBankService,
                        RoleAccessService roleAccessService) {
        this.aiStatusService = aiStatusService;
        this.aiService = aiService;
        this.questionBankService = questionBankService;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(aiStatusService.getStatus());
    }

    @PostMapping("/generate-question")
    public ApiResponse<String> generateQuestion(@Valid @RequestBody GenerateQuestionRequest request) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(aiService.generateQuestion(request));
    }

    @PostMapping("/questions/generate")
    public ApiResponse<List<AiGeneratedQuestion>> generateQuestionDrafts(@Valid @RequestBody GenerateQuestionBatchRequest request) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(aiService.generateQuestionDrafts(request));
    }

    @PostMapping("/questions/save")
    public ApiResponse<Map<String, Object>> saveGeneratedQuestions(@Valid @RequestBody SaveGeneratedQuestionsRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        List<Map<String, Object>> saved = request.getQuestions().stream()
                .map(this::toQuestionRequest)
                .map(question -> questionBankService.createQuestion(question, user))
                .toList();
        return ApiResponse.ok("AI题目草稿已保存", Map.of(
                "savedCount", saved.size(),
                "questions", saved
        ));
    }

    @PostMapping("/explain")
    public ApiResponse<String> explain(@Valid @RequestBody ExplainRequest request) {
        roleAccessService.requireLogin();
        return ApiResponse.ok(aiService.explain(request.getText()));
    }

    @PostMapping("/wrong-question/explain")
    public ApiResponse<String> explainWrongQuestion(@Valid @RequestBody WrongQuestionExplainRequest request) {
        roleAccessService.requireLogin();
        return ApiResponse.ok(aiService.explainWrongQuestion(request));
    }

    @PostMapping("/suggest-review")
    public ApiResponse<String> suggestReview(@Valid @RequestBody SuggestReviewRequest request) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(aiService.suggestReview(request));
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
        request.setOptions(draft.getOptions().stream().map(this::toOptionRequest).toList());
        return request;
    }

    private QuestionOptionRequest toOptionRequest(AiGeneratedQuestionOption option) {
        QuestionOptionRequest request = new QuestionOptionRequest();
        request.setOptionLabel(option.getOptionLabel());
        request.setOptionContent(option.getOptionContent());
        request.setCorrect(option.getCorrect());
        return request;
    }
}
