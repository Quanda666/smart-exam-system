package com.smartexam.dto.basic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NoticeRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 128, message = "公告标题长度不能超过128个字符")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 2000, message = "公告内容长度不能超过2000个字符")
    private String content;

    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status = 1;

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
}
