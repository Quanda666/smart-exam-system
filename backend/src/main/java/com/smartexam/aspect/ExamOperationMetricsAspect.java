package com.smartexam.aspect;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Aspect
@Component
public class ExamOperationMetricsAspect {

    private static final String TIMER_NAME = "smart_exam.exam.operation.duration";
    private static final String COUNTER_NAME = "smart_exam.exam.operation.total";

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public ExamOperationMetricsAspect(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Around("""
            execution(public * com.smartexam.service.ExamService.startExam(..)) ||
            execution(public * com.smartexam.service.ExamService.saveDraft(..)) ||
            execution(public * com.smartexam.service.ExamService.submitExam(..)) ||
            execution(public * com.smartexam.service.ExamService.attemptHeartbeat(..)) ||
            execution(public * com.smartexam.service.ExamService.forceSubmitAttempt(..)) ||
            execution(public * com.smartexam.service.ExamService.flushRedisDrafts(..)) ||
            execution(public * com.smartexam.service.ExamService.prepareAttemptResilienceFixture(..)) ||
            execution(public * com.smartexam.service.ExamService.cleanupAttemptResilienceFixtures(..)) ||
            execution(public * com.smartexam.service.MonitorService.recordCheatEvent(..)) ||
            execution(public * com.smartexam.service.MonitorService.recordCheatEvents(..))
            """)
    public Object recordExamOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        String operation = joinPoint.getSignature().getName();
        try {
            Object result = joinPoint.proceed();
            record(operation, outcome(operation, result), startedAt);
            return result;
        } catch (Throwable ex) {
            record(operation, "error", startedAt);
            throw ex;
        }
    }

    private void record(String operation, String outcome, long startedAt) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        Tags tags = Tags.of("operation", operation, "outcome", outcome);
        Duration duration = Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt));
        Timer.builder(TIMER_NAME)
                .description("Exam critical operation duration")
                .tags(tags)
                .register(registry)
                .record(duration);
        Counter.builder(COUNTER_NAME)
                .description("Exam critical operation count")
                .tags(tags)
                .register(registry)
                .increment();
    }

    private String outcome(String operation, Object result) {
        if (!(result instanceof Map<?, ?> map)) {
            return "success";
        }
        return switch (operation) {
            case "startExam" -> startOutcome(map);
            case "saveDraft" -> draftOutcome(map);
            case "submitExam" -> submitOutcome(map);
            case "attemptHeartbeat" -> heartbeatOutcome(map);
            case "forceSubmitAttempt" -> "forced";
            case "flushRedisDrafts" -> flushOutcome(map);
            case "prepareAttemptResilienceFixture" -> "prepared";
            case "cleanupAttemptResilienceFixtures" -> cleanupOutcome(map);
            case "recordCheatEvent" -> "accepted";
            case "recordCheatEvents" -> monitorOutcome(map);
            default -> "success";
        };
    }

    private String startOutcome(Map<?, ?> map) {
        if (bool(map.get("autoSubmitted"))) {
            return "auto_submitted";
        }
        return "started";
    }

    private String draftOutcome(Map<?, ?> map) {
        if (!bool(map.get("saved"))) {
            return bool(map.get("stale")) ? "stale" : "rejected";
        }
        if (bool(map.get("writeBack"))) {
            return "saved_redis_writeback";
        }
        Object source = map.get("draftSource");
        return source == null ? "saved" : "saved_" + String.valueOf(source).toLowerCase();
    }

    private String submitOutcome(Map<?, ?> map) {
        if (bool(map.get("submitPayloadMismatch"))) {
            return "replay_payload_mismatch";
        }
        if (bool(map.get("submitTokenMismatch"))) {
            return "replay_token_mismatch";
        }
        if (bool(map.get("responseReplayed"))) {
            return "replayed";
        }
        if (bool(map.get("alreadySubmitted"))) {
            return "already_submitted";
        }
        Object submitType = map.get("submitType");
        return submitType == null ? "submitted" : "submitted_" + String.valueOf(submitType).toLowerCase();
    }

    private String heartbeatOutcome(Map<?, ?> map) {
        if (bool(map.get("autoSubmitted"))) {
            return "auto_submitted";
        }
        if (bool(map.get("submitted"))) {
            return "submitted";
        }
        return "ok";
    }

    private String flushOutcome(Map<?, ?> map) {
        long errors = longValue(map.get("errors"));
        long flushed = longValue(map.get("flushed"));
        if (errors > 0 && flushed > 0) {
            return "partial";
        }
        if (errors > 0) {
            return "failed";
        }
        return flushed > 0 ? "flushed" : "empty";
    }

    private String cleanupOutcome(Map<?, ?> map) {
        if (!bool(map.get("cleaned"))) {
            return "dry_run";
        }
        return "cleaned";
    }

    private String monitorOutcome(Map<?, ?> map) {
        long accepted = longValue(map.get("accepted"));
        long duplicates = longValue(map.get("duplicates"));
        if (accepted > 0 && duplicates > 0) {
            return "accepted_with_duplicates";
        }
        if (accepted > 0) {
            return "accepted";
        }
        return duplicates > 0 ? "duplicates" : "empty";
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
