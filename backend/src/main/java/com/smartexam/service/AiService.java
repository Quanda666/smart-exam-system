package com.smartexam.service;

import com.smartexam.config.AiProperties;
import com.smartexam.dto.ai.GenerateQuestionRequest;
import com.smartexam.dto.ai.SuggestReviewRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = Math.max(1, aiProperties.getTimeoutSeconds()) * 1000;
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
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

    private String requestRemote(String prompt, String apiKey) {
        try {
            String url = aiProperties.getBaseUrl() + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", aiProperties.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

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
}
