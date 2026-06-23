package com.smartexam.scheduler;

import com.smartexam.service.ExamService;
import com.smartexam.service.JobLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ApprovalReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ApprovalReminderScheduler.class);
    private static final String LOCK_KEY = "approval-reminder-scheduler";
    private static final int LOCK_LEASE_SECONDS = 600;

    private final ExamService examService;
    private final JobLockService jobLockService;

    public ApprovalReminderScheduler(ExamService examService, JobLockService jobLockService) {
        this.examService = examService;
        this.jobLockService = jobLockService;
    }

    @Scheduled(
            fixedDelayString = "${smart-exam.approval-reminder-check-delay-ms:300000}",
            initialDelayString = "${smart-exam.approval-reminder-initial-delay-ms:120000}"
    )
    public void remindOverdueApprovals() {
        boolean locked = false;
        try {
            locked = jobLockService.tryAcquire(LOCK_KEY, LOCK_LEASE_SECONDS);
            if (!locked) {
                log.debug("Scheduled approval reminder skipped because another node holds the lock");
                return;
            }
            Map<String, Object> result = examService.sendScheduledApprovalOverdueReminders(jobLockService.ownerId());
            if (Boolean.TRUE.equals(result.get("sent"))) {
                log.info("Scheduled approval reminder sent: overdue={}, recipients={}, node={}, durationMs={}",
                        result.get("overdueExamCount"), result.get("adminCount"),
                        result.get("nodeId"), result.get("durationMs"));
            }
        } catch (Exception ex) {
            log.warn("Scheduled approval reminder failed: {}", ex.getMessage());
        } finally {
            if (locked) {
                jobLockService.release(LOCK_KEY);
            }
        }
    }
}
