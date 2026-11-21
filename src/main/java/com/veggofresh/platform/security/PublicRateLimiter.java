package com.veggofresh.platform.security;

import com.veggofresh.platform.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limit for the public (guest) endpoints, applied per device AND per IP address.
 *
 * <p>{@code X-Device-Id} is chosen by the client, so on its own it is not a security control; a caller can
 * simply change it. That is why the IP address is limited as well (with a higher ceiling, because many
 * real users can share one IP behind mobile carrier NAT).
 *
 * <p>In-memory, so the limits are per application instance (same trade-off as {@code ChatRateLimiter}).
 * Behind a reverse proxy the real client IP is read from {@code X-Forwarded-For}; make sure your proxy sets
 * or overwrites that header, otherwise a caller could forge it.
 */
@Component
public class PublicRateLimiter {

    private static final long WINDOW_MS = 60_000L;
    private static final int PRUNE_THRESHOLD = 20_000;

    private final int maxPerDevicePerMinute;
    private final int maxPerIpPerMinute;
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public PublicRateLimiter(
            @Value("${veggofresh.public.max-requests-per-minute:120}") int maxPerDevicePerMinute,
            @Value("${veggofresh.public.max-requests-per-minute-per-ip:300}") int maxPerIpPerMinute) {
        this.maxPerDevicePerMinute = Math.max(1, maxPerDevicePerMinute);
        this.maxPerIpPerMinute = Math.max(1, maxPerIpPerMinute);
    }

    /** Records one request, or throws 429 when this device or this IP is over its limit. */
    public void check(HttpServletRequest request) {
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;

        String ip = clientIp(request);
        String deviceId = request.getHeader("X-Device-Id");

        record("ip:" + ip, maxPerIpPerMinute, now, cutoff);
        if (StringUtils.hasText(deviceId)) {
            record("dev:" + deviceId.trim(), maxPerDevicePerMinute, now, cutoff);
        }
        if (hits.size() > PRUNE_THRESHOLD) {
            prune(cutoff);
        }
    }

    private void record(String key, int max, long now, long cutoff) {
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() < cutoff) {
                window.pollFirst();
            }
            if (window.size() >= max) {
                throw new BusinessException("RATE_LIMITED",
                        "Too many requests. Please wait a moment and try again.", HttpStatus.TOO_MANY_REQUESTS);
            }
            window.addLast(now);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    private void prune(long cutoff) {
        hits.entrySet().removeIf(e -> {
            Deque<Long> d = e.getValue();
            synchronized (d) {
                return d.isEmpty() || d.peekLast() < cutoff;
            }
        });
    }
}
