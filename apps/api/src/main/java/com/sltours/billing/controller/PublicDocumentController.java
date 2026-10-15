package com.sltours.billing.controller;
import com.sltours.billing.service.BillingShareService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Opens the single document behind a customer link. Errors are a short plain page, since customers open these links
 * from WhatsApp or email in a browser.
 */
@RestController
@RequestMapping("/api/v1/public/documents")
public class PublicDocumentController {
    private final BillingShareService sharing;
    private final com.sltours.billing.service.BillingSettingsService settings;
    public PublicDocumentController(BillingShareService sharing, com.sltours.billing.service.BillingSettingsService settings) {
        this.sharing = sharing; this.settings = settings;
    }

    @GetMapping("/{token}")
    ResponseEntity<byte[]> open(@PathVariable String token) {
        try {
            BillingShareService.PublicFile file = sharing.open(token);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.filename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Robots-Tag", "noindex, nofollow")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .body(file.bytes());
        } catch (ResponseStatusException e) {
            String page = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta name=\"robots\" content=\"noindex\"><title>SL Tours document</title></head>"
                + "<body style=\"font-family:system-ui,sans-serif;max-width:32rem;margin:3rem auto;padding:0 1rem;color:#14213d\">"
                + "<h1 style=\"color:#051e62;font-size:1.4rem\">SL Tours document</h1><p>" + escape(e.getReason()) + "</p>"
                + contact() + "</body></html>";
            return ResponseEntity.status(e.getStatusCode()).contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Robots-Tag", "noindex, nofollow")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                .body(page.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String contact() {
        var s = settings.current();
        StringBuilder b = new StringBuilder("<p>Contact ").append(escape(s.getCompanyName()));
        if (s.getWhatsapp() != null) b.append(" on WhatsApp ").append(escape(s.getWhatsapp()));
        if (s.getEmail() != null) b.append(s.getWhatsapp() != null ? " or by email at " : " by email at ").append(escape(s.getEmail()));
        return b.append(".</p>").toString();
    }

    private static String escape(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
