package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.service.AiStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiStatusService aiStatusService;

    public AiController(AiStatusService aiStatusService) {
        this.aiStatusService = aiStatusService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(aiStatusService.getStatus());
    }
}
