package com.smartexam.service;

import com.smartexam.config.AiProperties;
import com.smartexam.dto.ai.GenerateQuestionRequest;
import com.smartexam.dto.ai.SuggestReviewRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    private final AiProperties aiProperties;
    private final RestTemplate restTemplate;

    public AiService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        this.restTemplate = new RestTemplate();
    }

    public String generateQuestion(GenerateQuestionRequest request) {
        String prompt = "为" + request.getSubject() + "生成一个" + request.getDifficulty() + "的" + request.getQuestionType() + "题目";
        if (request.getKnowledgePoint() != null) {
            prompt += "，关于知识点：" + request.getKnowledgePoint();
        }
        return callAi(prompt);
    }
    
    public String explain(String text) {
        String prompt = "请解释以下内容：\n" + text;
        return callAi(prompt);
    }

    public String suggestReview(SuggestReviewRequest request) {
        String prompt = "请对以下主观题答案进行评分，并给出评语。\n题目：" + request.getQuestion() + "\n参考答案：" + request.getCorrectAnswer() + "\n学生答案：" + request.getStudentAnswer();
        return callAi(prompt);
    }

    private String callAi(String prompt) {
        if (!aiProperties.getApiKey().isBlank()) {
            String url = aiProperties.getBaseUrl() + "/chat/completions";
            
            Map<String, Object> body = new HashMap<>();
            body.put("model", aiProperties.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            // Add headers, including Authorization with Bearer token
            // Make POST request and return response
            // This is a simplified example. A real implementation would require a proper HTTP client setup.
            return "Real AI response for: " + prompt;
        }
        return "这是模拟的AI回复，因为没有配置API Key。";
    }
}
