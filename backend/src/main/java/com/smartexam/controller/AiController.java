package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.config.AiProperties;
import com.smartexam.dto.ai.ExplainRequest;
import com.smartexam.dto.ai.GenerateQuestionRequest;
import com.smartexam.dto.ai.SuggestReviewRequest;
import com.smartexam.service.AiService;
import com.smartexam.service.AiStatusService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiStatusService aiStatusService;
    private final AiService aiService;
    private final AiProperties aiProperties;

    public AiController(AiStatusService aiStatusService, AiService aiService, AiProperties aiProperties) {
        this.aiStatusService = aiStatusService;
        this.aiService = aiService;
        this.aiProperties = aiProperties;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(aiStatusService.getStatus());
    }

    @PostMapping("/generate-question")
    public ApiResponse<String> generateQuestion(@Valid @RequestBody GenerateQuestionRequest request) {
        return ApiResponse.ok(aiService.generateQuestion(request));
    }

    @PostMapping("/explain")
    public ApiResponse<String> explain(@Valid @RequestBody ExplainRequest request) {
        return ApiResponse.ok(aiService.explain(request.getText()));
    }

    @PostMapping("/suggest-review")
    public ApiResponse<String> suggestReview(@Valid @RequestBody SuggestReviewRequest request) {
        return ApiResponse.ok(aiService.suggestReview(request));
    }
}
