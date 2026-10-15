package com.sltours.accounts.service;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded, per-process limiter for sign-in and public submissions. The edge must also rate-limit distributed deployments. */
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();
    private record Window(long start, int count) {}
    private static final java.util.Set<String> LIMITED_PATHS = java.util.Set.of("/api/v1/auth/login", "/api/v1/auth/register",
        "/api/v1/auth/verify-email", "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/confirm", "/api/v1/account/verification-email",
        "/api/v1/enquiries", "/api/v1/trip-planning-requests", "/api/v1/private-trips/requests", "/api/v1/private-trips/quote-requests", "/api/v1/itineraries",
        "/api/v1/assistant/conversations", "/api/v1/partner-applications");
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST") || !LIMITED_PATHS.contains(request.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        long now = clock.millis();
        String key = request.getServletPath() + "|" + request.getRemoteAddr();
        attempts.entrySet().removeIf(e -> now - e.getValue().start() >= 60_000);
        if (attempts.size() >= 10_000 && !attempts.containsKey(key)) {
            reject(response); return;
        }
        Window window = attempts.compute(key, (ignored, old) ->
            old == null || now - old.start() >= 60_000 ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (window.count() > 10) { reject(response); return; }
        chain.doFilter(request, response);
    }
    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429); response.setHeader("Retry-After", "60"); response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Too many attempts. Please wait a minute and try again.\"}");
    }
}
