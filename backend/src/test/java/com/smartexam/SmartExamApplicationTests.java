package com.smartexam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SmartExamApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void demoUsersShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/auth/demo-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].username").value("admin"));
    }

    @Test
    void protectedApiShouldRejectAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void loginShouldRejectWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin",
                                "password", "wrong-password"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void adminTokenShouldAccessAdminApiAndRejectStudentApi() throws Exception {
        String token = loginAndExtractToken("admin", "admin123");
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        mockMvc.perform(get("/api/student/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminShouldManageClassData() throws Exception {
        String token = loginAndExtractToken("admin", "admin123");
        String className = "阶段3演示班级";

        MvcResult createResult = mockMvc.perform(post("/api/basic/classes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "className", className,
                                "major", "软件工程",
                                "grade", "2024级",
                                "status", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.className").value(className))
                .andReturn();

        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(put("/api/basic/classes/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "className", className + "A",
                                "major", "软件工程",
                                "grade", "2024级",
                                "status", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.className").value(className + "A"));

        mockMvc.perform(delete("/api/basic/classes/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void teacherShouldManageSubjectAndKnowledgePoint() throws Exception {
        String token = loginAndExtractToken("teacher1", "teacher123");
        String subjectName = "阶段3测试科目";

        MvcResult subjectResult = mockMvc.perform(post("/api/basic/subjects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectName", subjectName,
                                "description", "阶段3接口测试科目",
                                "status", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectName").value(subjectName))
                .andReturn();

        Long subjectId = objectMapper.readTree(subjectResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/basic/knowledge-points")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", subjectId,
                                "pointName", "阶段3测试知识点",
                                "sortOrder", 1,
                                "status", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointName").value("阶段3测试知识点"));
    }

    @Test
    void studentShouldReadNoticeButCannotCreateClass() throws Exception {
        String token = loginAndExtractToken("student1", "student123");

        mockMvc.perform(get("/api/basic/notices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/basic/classes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "className", "学生不可创建班级",
                                "major", "测试",
                                "grade", "2024级",
                                "status", 1
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void teacherShouldManageQuestionBank() throws Exception {
        String token = loginAndExtractToken("teacher1", "teacher123");

        MvcResult createResult = mockMvc.perform(post("/api/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", 1,
                                "knowledgePointId", 1,
                                "questionType", "SINGLE_CHOICE",
                                "difficulty", "EASY",
                                "stem", "阶段4测试单选题：Java Map 的主要用途是？",
                                "correctAnswer", "B",
                                "analysis", "Map 用于存储键值对。",
                                "defaultScore", 5,
                                "status", 0,
                                "options", List.of(
                                        Map.of("optionLabel", "A", "optionContent", "存储单个字符串", "correct", false),
                                        Map.of("optionLabel", "B", "optionContent", "存储键值对", "correct", true),
                                        Map.of("optionLabel", "C", "optionContent", "只存储整数", "correct", false)
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionType").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.data.options[1].optionLabel").value("B"))
                .andReturn();

        Long questionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/questions")
                        .header("Authorization", "Bearer " + token)
                        .param("questionType", "SINGLE_CHOICE")
                        .param("difficulty", "EASY")
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(put("/api/questions/" + questionId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));

        mockMvc.perform(put("/api/questions/" + questionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", 1,
                                "knowledgePointId", 1,
                                "questionType", "FILL_BLANK",
                                "difficulty", "MEDIUM",
                                "stem", "阶段4测试填空题：事务的四个特性简称为____。",
                                "correctAnswer", "ACID",
                                "analysis", "事务特性包含原子性、一致性、隔离性、持久性。",
                                "defaultScore", 6,
                                "status", 1,
                                "options", List.of()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionType").value("FILL_BLANK"))
                .andExpect(jsonPath("$.data.correctAnswer").value("ACID"));

        mockMvc.perform(delete("/api/questions/" + questionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void studentShouldNotAccessQuestionBankManagement() throws Exception {
        String token = loginAndExtractToken("student1", "student123");

        mockMvc.perform(get("/api/questions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void teacherShouldManagePaperAndGenerateByRules() throws Exception {
        String token = loginAndExtractToken("teacher1", "teacher123");
        Long firstQuestionId = createPublishedQuestion(token, "阶段5手动组卷单选题", "SINGLE_CHOICE");
        Long secondQuestionId = createPublishedQuestion(token, "阶段5规则组卷单选题", "SINGLE_CHOICE");

        MvcResult createPaperResult = mockMvc.perform(post("/api/papers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", 1,
                                "paperName", "阶段5手动组卷测试卷",
                                "description", "阶段5接口测试试卷",
                                "status", 0,
                                "questions", List.of(
                                        Map.of("questionId", firstQuestionId, "score", 5, "sortOrder", 1),
                                        Map.of("questionId", secondQuestionId, "score", 6, "sortOrder", 2)
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paperName").value("阶段5手动组卷测试卷"))
                .andExpect(jsonPath("$.data.questionCount").value(2))
                .andReturn();

        Long paperId = objectMapper.readTree(createPaperResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/papers/" + paperId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].questionId").value(firstQuestionId));

        mockMvc.perform(put("/api/papers/" + paperId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));

        mockMvc.perform(post("/api/papers/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", 1,
                                "paperName", "阶段5规则组卷测试卷",
                                "description", "按题型和难度自动抽题",
                                "status", 0,
                                "rules", List.of(Map.of(
                                        "questionType", "SINGLE_CHOICE",
                                        "difficulty", "EASY",
                                        "count", 1,
                                        "score", 5
                                ))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionCount").value(1));

        mockMvc.perform(delete("/api/papers/" + paperId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void studentShouldNotAccessPaperManagement() throws Exception {
        String token = loginAndExtractToken("student1", "student123");

        mockMvc.perform(get("/api/papers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private Long createPublishedQuestion(String token, String stem, String questionType) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", 1,
                                "knowledgePointId", 1,
                                "questionType", questionType,
                                "difficulty", "EASY",
                                "stem", stem,
                                "correctAnswer", "B",
                                "analysis", "阶段5试卷测试题目。",
                                "defaultScore", 5,
                                "status", 1,
                                "options", List.of(
                                        Map.of("optionLabel", "A", "optionContent", "错误项", "correct", false),
                                        Map.of("optionLabel", "B", "optionContent", "正确项", "correct", true)
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String loginAndExtractToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }
}
