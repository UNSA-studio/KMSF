package com.unsa.kmsf;

import java.util.concurrent.*;
import java.util.*;

public class RateLimiter {
    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> blockedIPs = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMs;
    private final long blockMs;
    private final int dynamicThreshold;
    private final long dynamicDurationMs;
    private final ConcurrentHashMap<String, Integer> suspiciousCount = new ConcurrentHashMap<>();
    private final boolean dynamicEnabled;

    public RateLimiter(Map<String, Object> config) {
        Map<String, Object> rl = (Map<String, Object>) config.get("rate_limit");
        this.maxRequests = ((Double) rl.get("max_requests")).intValue();
        this.windowMs = ((Double) rl.get("time_window_seconds")).longValue() * 1000;
        this.blockMs = ((Double) rl.get("block_duration_seconds")).longValue() * 1000;
        Map<String, Object> db = (Map<String, Object>) config.get("dynamic_blacklist");
        this.dynamicEnabled = (boolean) db.get("enabled");
        this.dynamicThreshold = ((Double) db.get("threshold")).intValue();
        this.dynamicDurationMs = ((Double) db.get("duration_seconds")).longValue() * 1000;
    }

    public boolean isAllowed(String ip) {
        // 检查静态封禁
        Long blockEnd = blockedIPs.get(ip);
        if (blockEnd != null) {
            if (System.currentTimeMillis() < blockEnd) return false;
            else blockedIPs.remove(ip);
        }

        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestLog.computeIfAbsent(ip, k -> new LinkedList<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                blockedIPs.put(ip, now + blockMs);
                // 动态黑名单计数
                if (dynamicEnabled) {
                    int count = suspiciousCount.merge(ip, 1, Integer::sum);
                    if (count >= dynamicThreshold) {
                        blockedIPs.put(ip, now + dynamicDurationMs); // 延长封禁
                    }
                }
                return false;
            }
            timestamps.addLast(now);
        }
        return true;
    }

    public Map<String, Long> getBlockedIPs() {
        return new HashMap<>(blockedIPs);
    }
}
