package com.sltours.accounts.service;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the signed-in idle timeout independently of the servlet container's session timeout.
 * Background refreshes (polling) are marked by the web client and keep the session alive without
 * counting as user activity, so an unattended admin or driver screen is signed out after the idle period.
 */
public class IdleSessionFilter extends OncePerRequestFilter {
    public static final String BACKGROUND_HEADER = "X-SLTours-Background";
    public static final String LAST_ACTIVITY = "sltours.lastActivity";
    private static final String SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";
    private final Duration idleTimeout;
    private final Clock clock;
    public IdleSessionFilter(Duration idleTimeout) { this(idleTimeout, Clock.systemUTC()); }
    public IdleSessionFilter(Duration idleTimeout, Clock clock) { this.idleTimeout = idleTimeout; this.clock = clock; }

    public static void markActive(HttpServletRequest request, Clock clock) {
        HttpSession session = request.getSession(false);
        if (session != null) session.setAttribute(LAST_ACTIVITY, clock.millis());
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SECURITY_CONTEXT) != null) {
            long now = clock.millis();
            Object last = session.getAttribute(LAST_ACTIVITY);
            if (last instanceof Long lastActivity && now - lastActivity >= idleTimeout.toMillis()) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                response.setHeader("X-SLTours-Session", "idle-expired");
            } else if (last == null || !"1".equals(request.getHeader(BACKGROUND_HEADER))) {
                session.setAttribute(LAST_ACTIVITY, now);
            }
        }
        chain.doFilter(request, response);
    }
}
