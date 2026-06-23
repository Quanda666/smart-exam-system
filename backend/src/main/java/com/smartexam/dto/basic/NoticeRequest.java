package com.smartexam.dto.basic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class NoticeRequest {

    @NotBlank(message = "Notice title is required")
    @Size(max = 128, message = "Notice title must be 128 characters or less")
    private String title;

    @NotBlank(message = "Notice content is required")
    @Size(max = 2000, message = "Notice content must be 2000 characters or less")
    private String content;

    @Min(value = 0, message = "Status must be 0 or 1")
    @Max(value = 1, message = "Status must be 0 or 1")
    private Integer status = 1;

    @Valid
    private List<NoticeTargetRequest> targets = new ArrayList<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status == null ? 1 : status;
    }

    public List<NoticeTargetRequest> getTargets() {
        return targets;
    }

    public void setTargets(List<NoticeTargetRequest> targets) {
        this.targets = targets == null ? new ArrayList<>() : targets;
    }
}
