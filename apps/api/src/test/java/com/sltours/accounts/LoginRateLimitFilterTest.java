package com.sltours.accounts;
import com.sltours.accounts.service.LoginRateLimitFilter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
class LoginRateLimitFilterTest {
    @Test void repeatedAttemptsAreLimitedWithoutBlockingNormalReads() throws Exception {
        var filter = new LoginRateLimitFilter(); var allowed = new AtomicInteger();
        for (int i = 0; i < 11; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setServletPath("/api/v1/auth/login"); request.setRemoteAddr("192.0.2.1");
            var response = new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)->allowed.incrementAndGet());
            if (i == 10) { assertThat(response.getStatus()).isEqualTo(429); assertThat(response.getHeader("Retry-After")).isEqualTo("60"); }
        }
        assertThat(allowed.get()).isEqualTo(10);
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setServletPath("/api/v1/auth/me"); request.setRemoteAddr("192.0.2.1");
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->allowed.incrementAndGet());
        assertThat(allowed.get()).isEqualTo(11);
    }
}
