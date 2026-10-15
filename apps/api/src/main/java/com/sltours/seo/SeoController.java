package com.sltours.seo;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Used by Nginx server-side includes and for the sitemap. Responses are generated per request (no caching). */
@RestController
@RequestMapping("/api/v1/seo")
public class SeoController {
    private final SeoService seo;
    public SeoController(SeoService seo) { this.seo = seo; }
    @GetMapping(value = "/head", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> head(@RequestParam(defaultValue = "/") String path) { return fresh(MediaType.TEXT_HTML, seo.head(path)); }
    @GetMapping(value = "/body", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> body(@RequestParam(defaultValue = "/") String path) { return fresh(MediaType.TEXT_HTML, seo.body(path)); }
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    ResponseEntity<String> sitemap() {
        return seo.sitemap().map(xml -> fresh(MediaType.APPLICATION_XML, xml)).orElseGet(() -> ResponseEntity.notFound().build());
    }
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> robots() { return fresh(MediaType.TEXT_PLAIN, seo.robots()); }
    /**
     * Route check for Nginx's auth_request: 204 when the page exists, 403 when it does not. auth_request accepts only
     * 2xx/401/403, so 403 here means "unknown page"; Nginx turns it into a 404 with the not-found page.
     */
    @GetMapping("/route")
    ResponseEntity<Void> route(@RequestHeader(value = "X-Original-URI", required = false) String originalUri,
            @RequestParam(required = false) String path) {
        String target = originalUri != null ? originalUri : path != null ? path : "/";
        return ResponseEntity.status(seo.exists(target) ? HttpStatus.NO_CONTENT : HttpStatus.FORBIDDEN).header(HttpHeaders.CACHE_CONTROL, "no-cache").build();
    }
    private static ResponseEntity<String> fresh(MediaType type, String content) {
        return ResponseEntity.ok().contentType(new MediaType(type, java.nio.charset.StandardCharsets.UTF_8))
            .header(HttpHeaders.CACHE_CONTROL, "no-cache").body(content);
    }
}
