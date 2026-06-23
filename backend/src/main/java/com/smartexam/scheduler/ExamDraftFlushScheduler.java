package com.smartexam.scheduler;

import com.smartexam.service.ExamService;
import com.smartexam.service.JobLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ExamDraftFlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExamDraftFlushScheduler.class);
    private static final String LOCK_KEY = "exam-draft-redis-flush";
    private static final int LOCK_LEASE_SECONDS = 300;

    private final ExamService examService;
    private final JobLockService jobLockService;

    public ExamDraftFlushScheduler(ExamService examService, JobLockService jobLockService) {
        this.examService = examService;
        this.jobLockService = jobLockService;
    }

    @Scheduled(
            fixedDelayString = "${smart-exam.draft-flush-delay-ms:60000}",
            initialDelayString = "${smart-exam.draft-flush-initial-delay-ms:180000}"
    )
    public void flushDrafts() {
        boolean locked = false;
        try {
            locked = jobLockService.tryAcquire(LOCK_KEY, LOCK_LEASE_SECONDS);
            if (!locked) {
                log.debug("Redis draft flush skipped because another node holds the lock");
                return;
            }
            Map<String, Object> result = examService.flushRedisDrafts(jobLockService.ownerId());
            Number flushed = (Number) result.get("flushed");
            if (flushed != null && flushed.intValue() > 0) {
                log.info("Redis draft flush completed: flushed={}, skipped={}, cleaned={}",
                        result.get("flushed"), result.get("skipped"), result.get("cleaned"));
            }
        } catch (Exception ex) {
            log.warn("Redis draft flush failed: {}", ex.getMessage());
        } finally {
            if (locked) {
                jobLockService.release(LOCK_KEY);
            }
        }
    }
}
