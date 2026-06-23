package com.smartexam.dto.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class QuestionRequest {

    @NotNull(message = "科目ID不能为空")
    private Long subjectId;

    private Long knowledgePointId;

    @NotBlank(message = "题型不能为空")
    @Size(max = 32, message = "题型长度不能超过32个字符")
    private String questionType;

    @NotBlank(message = "难度不能为空")
    @Size(max = 32, message = "难度长度不能超过32个字符")
    private String difficulty;

    @NotBlank(message = "题干不能为空")
    @Size(max = 4000, message = "题干长度不能超过4000个字符")
    private String stem;

    @Size(max = 4000, message = "参考答案长度不能超过4000个字符")
    private String correctAnswer;

    @Size(max = 4000, message = "解析长度不能超过4000个字符")
    private String analysis;

    @NotNull(message = "默认分值不能为空")
    @DecimalMin(value = "0.1", message = "默认分值必须大于0")
    private BigDecimal defaultScore = BigDecimal.valueOf(5);

    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status = 0;

    @Size(max = 32, message = "来源类型长度不能超过32个字符")
    private String sourceType;

    @Size(max = 255, message = "来源说明长度不能超过255个字符")
    private String sourceDetail;

    private Long materialId;

    private Integer sourcePage;

    private Integer sourceParagraph;

    @Size(max = 500, message = "来源片段长度不能超过500个字符")
    private String sourceExcerpt;

    @Size(max = 64, message = "AI模型长度不能超过64个字符")
    private String aiModel;

    @Size(max = 64, message = "提示词版本长度不能超过64个字符")
    private String promptVersion;

    @Valid
    private List<QuestionOptionRequest> options = new ArrayList<>();

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(Long knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getStem() {
        return stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public BigDecimal getDefaultScore() {
        return defaultScore;
    }

    public void setDefaultScore(BigDecimal defaultScore) {
        this.defaultScore = defaultScore == null ? BigDecimal.valueOf(5) : defaultScore;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status == null ? 0 : status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceDetail() {
        return sourceDetail;
    }

    public void setSourceDetail(String sourceDetail) {
        this.sourceDetail = sourceDetail;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Integer getSourcePage() {
        return sourcePage;
    }

    public void setSourcePage(Integer sourcePage) {
        this.sourcePage = sourcePage;
    }

    public Integer getSourceParagraph() {
        return sourceParagraph;
    }

    public void setSourceParagraph(Integer sourceParagraph) {
        this.sourceParagraph = sourceParagraph;
    }

    public String getSourceExcerpt() {
        return sourceExcerpt;
    }

    public void setSourceExcerpt(String sourceExcerpt) {
        this.sourceExcerpt = sourceExcerpt;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public List<QuestionOptionRequest> getOptions() {
        return options;
    }

    public void setOptions(List<QuestionOptionRequest> options) {
        this.options = options == null ? new ArrayList<>() : options;
    }
}
