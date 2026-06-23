package com.smartexam.controller;

import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.dto.ai.AiGeneratedQuestion;
import com.smartexam.service.ExcelQuestionTemplateService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/question-template")
public class QuestionExcelTemplateController {

    private final ExcelQuestionTemplateService excelQuestionTemplateService;

    public QuestionExcelTemplateController(ExcelQuestionTemplateService excelQuestionTemplateService) {
        this.excelQuestionTemplateService = excelQuestionTemplateService;
    }

    @GetMapping("/download")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] templateBytes = excelQuestionTemplateService.generateTemplate();

        String filename = "题目导入模板.xlsx";
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(templateBytes);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<List<AiGeneratedQuestion>> importFromExcel(@RequestPart("file") MultipartFile file,
                                                                   @RequestParam Long subjectId,
                                                                   @RequestParam String subjectName,
                                                                   @RequestParam(required = false) Long knowledgePointId,
                                                                   @RequestParam(required = false) String knowledgePointName,
                                                                   @RequestParam(defaultValue = "MEDIUM") String difficulty,
                                                                   @RequestParam(defaultValue = "5") BigDecimal defaultScore) {
        List<AiGeneratedQuestion> questions = excelQuestionTemplateService.parseExcel(
                file, subjectId, knowledgePointId, difficulty, defaultScore);

        for (AiGeneratedQuestion question : questions) {
            question.setSubjectId(subjectId);
            question.setKnowledgePointId(knowledgePointId);
        }

        return ApiResponse.ok(questions);
    }
}
