package com.sltours.accounts;
import com.sltours.accounts.service.IdleSessionFilter;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
class IdleSessionFilterTest {
    private final Instant start = Instant.parse("2026-10-02T08:00:00Z");
    private MockHttpSession signedIn() {
        var session = new MockHttpSession(); session.setAttribute("SPRING_SECURITY_CONTEXT", new Object()); return session;
    }
    private MockHttpServletRequest request(MockHttpSession session, boolean background) {
        var request = new MockHttpServletRequest("GET", "/api/v1/admin/dashboard"); request.setSession(session);
        if (background) request.addHeader(IdleSessionFilter.BACKGROUND_HEADER, "1");
        return request;
    }
    @Test void backgroundPollingDoesNotPostponeTheIdleTimeout() throws Exception {
        var session = signedIn(); var calls = new AtomicInteger();
        new IdleSessionFilter(Duration.ofMinutes(30), Clock.fixed(start, ZoneOffset.UTC))
            .doFilter(request(session, false), new MockHttpServletResponse(), (req, res) -> calls.incrementAndGet());
        for (int minute = 1; minute < 30; minute++) {
            new IdleSessionFilter(Duration.ofMinutes(30), Clock.fixed(start.plus(Duration.ofMinutes(minute)), ZoneOffset.UTC))
                .doFilter(request(session, true), new MockHttpServletResponse(), (req, res) -> calls.incrementAndGet());
        }
        assertThat(session.isInvalid()).isFalse();
        var response = new MockHttpServletResponse();
        new IdleSessionFilter(Duration.ofMinutes(30), Clock.fixed(start.plus(Duration.ofMinutes(30)), ZoneOffset.UTC))
            .doFilter(request(session, true), response, (req, res) -> calls.incrementAndGet());
        assertThat(session.isInvalid()).isTrue();
        assertThat(response.getHeader("X-SLTours-Session")).isEqualTo("idle-expired");
        assertThat(calls.get()).isEqualTo(31);
    }
    @Test void userRequestsRenewActivityAndAnonymousSessionsAreIgnored() throws Exception {
        var session = signedIn();
        new IdleSessionFilter(Duration.ofMinutes(30), Clock.fixed(start, ZoneOffset.UTC))
            .doFilter(request(session, false), new MockHttpServletResponse(), (req, res) -> {});
        new IdleSessionFilter(Duration.ofMinutes(30), Clock.fixed(start.plus(Duration.ofMinutes(25)), ZoneOffset.UTC))
            .doFilter(request(session, false), new MockHttpServletResponse(), (req, res) -> {});
        new IdleSessionFilter(Duration.ofMinutes(30), Clock.fixed(start.plus(Duration.ofMinutes(50)), ZoneOffset.UTC))
            .doFilter(request(session, true), new MockHttpServletResponse(), (req, res) -> {});
        assertThat(session.isInvalid()).isFalse();
        var anonymous = new MockHttpSession();
        new IdleSessionFilter(Duration.ofMinutes(30), Clock.fixed(start, ZoneOffset.UTC))
            .doFilter(request(anonymous, false), new MockHttpServletResponse(), (req, res) -> {});
        assertThat(anonymous.getAttribute(IdleSessionFilter.LAST_ACTIVITY)).isNull();
    }
}
