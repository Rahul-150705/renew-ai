package com.renewai.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {
    private final Map<String, Bucket> agentQueryBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> bulkMessageBuckets = new ConcurrentHashMap<>();

    // 5 per minute per user
    public Bucket resolveAgentQueryBucket(String username) {
        return agentQueryBuckets.computeIfAbsent(username, this::newAgentQueryBucket);
    }
    private Bucket newAgentQueryBucket(String username) {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    // 5 login/signup requests per 5 minutes per IP
    public Bucket resolveAuthBucket(String ip) {
        return loginBuckets.computeIfAbsent(ip, this::newAuthBucket);
    }
    private Bucket newAuthBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(5)));
        return Bucket.builder().addLimit(limit).build();
    }

    // 1 bulk message action per minute per user
    public Bucket resolveBulkMessageBucket(String username) {
        return bulkMessageBuckets.computeIfAbsent(username, this::newBulkMessageBucket);
    }
    private Bucket newBulkMessageBucket(String username) {
        Bandwidth limit = Bandwidth.classic(1, Refill.intervally(1, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
