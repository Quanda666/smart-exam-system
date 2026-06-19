package com.smartexam.dto.exam;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 答题草稿暂存请求：answers 为前端答案对象序列化后的 JSON 字符串。
 */
public class DraftRequest {

    @Size(max = 200000, message = "answers must be at most 200000 characters")
    private String answers;

    @Size(max = 80, message = "clientDraftId must be at most 80 characters")
    private String clientDraftId;

    @PositiveOrZero(message = "revision must be greater than or equal to 0")
    private Long revision;

    public String getAnswers() {
        return answers;
    }

    public void setAnswers(String answers) {
        this.answers = answers;
    }

    public String getClientDraftId() {
        return clientDraftId;
    }

    public void setClientDraftId(String clientDraftId) {
        this.clientDraftId = clientDraftId;
    }

    public Long getRevision() {
        return revision;
    }

    public void setRevision(Long revision) {
        this.revision = revision;
    }
}
