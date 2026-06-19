package com.smartexam.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ExamDraftCacheService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final boolean enabled;
    private final long ttlSeconds;
    private final AtomicLong reads = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong writes = new AtomicLong();
    private final AtomicLong deletes = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong flushSuccess = new AtomicLong();
    private final AtomicLong flushSkipped = new AtomicLong();
    private final AtomicLong lastFlushAtEpochMillis = new AtomicLong();
    private final AtomicLong lastFlushChecked = new AtomicLong();
    private final AtomicLong lastFlushFlushed = new AtomicLong();
    private final AtomicLong lastFlushSkipped = new AtomicLong();
    private final AtomicLong lastFlushCleaned = new AtomicLong();

    public ExamDraftCacheService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                 @Value("${app.exam.draft-cache.redis-enabled:false}") boolean enabled,
                                 @Value("${app.exam.draft-cache.ttl-seconds:21600}") long ttlSeconds) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.enabled = enabled;
        this.ttlSeconds = ttlSeconds;
    }

    public boolean available() {
        return enabled && redisTemplateProvider.getIfAvailable() != null;
    }

    public Map<String, Object> get(Long attemptId) {
        if (!available()) {
            return Map.of();
        }
        reads.incrementAndGet();
        try {
            String raw = redisTemplateProvider.getObject().opsForValue().get(key(attemptId));
            if (raw == null || raw.isBlank()) {
                return Map.of();
            }
            hits.incrementAndGet();
            return OBJECT_MAPPER.readValue(raw, new TypeReference<>() {});
        } catch (Exception ex) {
            errors.incrementAndGet();
            return Map.of();
        }
    }

    public void put(Long attemptId, String answers, String clientDraftId, long revision, Object savedAt) {
        put(attemptId, answers, clientDraftId, revision, savedAt, false);
    }

    public void put(Long attemptId, String answers, String clientDraftId, long revision, Object savedAt, boolean dirty) {
        if (!available()) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("attemptId", attemptId);
            payload.put("answers", answers);
            payload.put("clientDraftId", clientDraftId);
            payload.put("revision", revision);
            payload.put("savedAt", savedAt == null ? null : String.valueOf(savedAt));
            payload.put("cachedAt", System.currentTimeMillis());
            payload.put("dirty", dirty);
            redisTemplateProvider.getObject().opsForValue().set(
                    key(attemptId),
                    OBJECT_MAPPER.writeValueAsString(payload),
                    Duration.ofSeconds(Math.max(60, ttlSeconds))
            );
            writes.incrementAndGet();
        } catch (Exception ex) {
            errors.incrementAndGet();
            // Redis is an acceleration layer only. Database remains the source of truth.
        }
    }

    public void delete(Long attemptId) {
        if (!available()) {
            return;
        }
        try {
            redisTemplateProvider.getObject().delete(key(attemptId));
            deletes.incrementAndGet();
        } catch (Exception ex) {
            errors.incrementAndGet();
            // Ignore cache cleanup failure.
        }
    }

    public List<Map<String, Object>> dirtyDrafts(int max) {
        if (!available()) {
            return List.of();
        }
        try {
            Set<String> keys = redisTemplateProvider.getObject().keys(keyPattern());
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (String key : keys) {
                if (result.size() >= Math.max(1, max)) {
                    break;
                }
                String raw = redisTemplateProvider.getObject().opsForValue().get(key);
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                Map<String, Object> payload = OBJECT_MAPPER.readValue(raw, new TypeReference<>() {});
                if (Boolean.TRUE.equals(payload.get("dirty"))) {
                    payload.put("cacheKey", key);
                    result.add(payload);
                }
            }
            return result;
        } catch (Exception ex) {
            errors.incrementAndGet();
            return List.of();
        }
    }

    public void markClean(Long attemptId, String answers, String clientDraftId, long revision, Object savedAt) {
        put(attemptId, answers, clientDraftId, revision, savedAt, false);
        flushSuccess.incrementAndGet();
    }

    public void markFlushSkipped() {
        flushSkipped.incrementAndGet();
    }

    public void recordFlushRun(int checked, int flushed, int skipped, int cleaned) {
        lastFlushAtEpochMillis.set(System.currentTimeMillis());
        lastFlushChecked.set(Math.max(0, checked));
        lastFlushFlushed.set(Math.max(0, flushed));
        lastFlushSkipped.set(Math.max(0, skipped));
        lastFlushCleaned.set(Math.max(0, cleaned));
    }

    public Map<String, Object> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", enabled);
        data.put("available", available());
        data.put("ttlSeconds", ttlSeconds);
        data.put("reads", reads.get());
        data.put("hits", hits.get());
        data.put("writes", writes.get());
        data.put("deletes", deletes.get());
        data.put("errors", errors.get());
        data.put("flushSuccess", flushSuccess.get());
        data.put("flushSkipped", flushSkipped.get());
        data.put("dirtyCount", dirtyCount());
        data.put("lastFlushAtEpochMillis", lastFlushAtEpochMillis.get());
        data.put("lastFlushChecked", lastFlushChecked.get());
        data.put("lastFlushFlushed", lastFlushFlushed.get());
        data.put("lastFlushSkipped", lastFlushSkipped.get());
        data.put("lastFlushCleaned", lastFlushCleaned.get());
        return data;
    }

    private long dirtyCount() {
        if (!available()) {
            return 0L;
        }
        try {
            return dirtyDrafts(Integer.MAX_VALUE).size();
        } catch (Exception ex) {
            errors.incrementAndGet();
            return 0L;
        }
    }

    private String key(Long attemptId) {
        return "smart-exam:attempt-draft:" + attemptId;
    }

    private String keyPattern() {
        return "smart-exam:attempt-draft:*";
    }
}
