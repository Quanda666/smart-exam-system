package com.smartexam.dto.exam;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ExamRequest {

    @NotNull(message = "Paper id is required")
    private Long paperId;

    @NotBlank(message = "Exam name is required")
    @Size(max = 128, message = "Exam name must be 128 characters or less")
    private String examName;

    @Size(max = 512, message = "Description must be 512 characters or less")
    private String description;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be greater than 0")
    private Integer durationMinutes;

    private List<Long> classIds = new ArrayList<>();

    private List<Long> classCourseIds = new ArrayList<>();

    private List<Long> studentUserIds = new ArrayList<>();

    public Long getPaperId() {
        return paperId;
    }

    public void setPaperId(Long paperId) {
        this.paperId = paperId;
    }

    public String getExamName() {
        return examName;
    }

    public void setExamName(String examName) {
        this.examName = examName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public List<Long> getClassIds() {
        return classIds;
    }

    public void setClassIds(List<Long> classIds) {
        this.classIds = classIds == null ? new ArrayList<>() : classIds;
    }

    public List<Long> getClassCourseIds() {
        return classCourseIds;
    }

    public void setClassCourseIds(List<Long> classCourseIds) {
        this.classCourseIds = classCourseIds == null ? new ArrayList<>() : classCourseIds;
    }

    public List<Long> getStudentUserIds() {
        return studentUserIds;
    }

    public void setStudentUserIds(List<Long> studentUserIds) {
        this.studentUserIds = studentUserIds == null ? new ArrayList<>() : studentUserIds;
    }
}
