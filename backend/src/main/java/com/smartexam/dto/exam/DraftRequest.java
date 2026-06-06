package com.smartexam.dto.exam;

/**
 * 答题草稿暂存请求：answers 为前端答案对象序列化后的 JSON 字符串。
 */
public class DraftRequest {

    private String answers;

    public String getAnswers() {
        return answers;
    }

    public void setAnswers(String answers) {
        this.answers = answers;
    }
}
