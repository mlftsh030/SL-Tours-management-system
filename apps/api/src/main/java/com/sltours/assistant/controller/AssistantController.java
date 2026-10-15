package com.sltours.assistant.controller;
import com.sltours.assistant.dto.AssistantDtos.*;
import com.sltours.assistant.service.*;
import com.sltours.shared.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Public planning assistant. Conversations are tied to the visitor's session and kept in memory only. */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
    private final AssistantService assistant;
    public AssistantController(AssistantService assistant) { this.assistant = assistant; }

    @GetMapping("/status")
    Status status() { return assistant.status(); }

    @PostMapping("/conversations")
    ResponseEntity<Reply> start(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assistant.start(session(request)));
    }

    @PostMapping("/conversations/{id}/messages")
    Reply send(@PathVariable String id, @Valid @RequestBody SendMessage body, HttpServletRequest request) {
        return assistant.send(id, session(request), request.getRemoteAddr(), body.message());
    }

    @PostMapping("/conversations/{id}/enquiry")
    Reply submit(@PathVariable String id, @Valid @RequestBody SubmitPlan body, HttpServletRequest request) {
        return assistant.submit(id, session(request), body);
    }

    @DeleteMapping("/conversations/{id}")
    ResponseEntity<Void> end(@PathVariable String id, HttpServletRequest request) {
        assistant.end(id, session(request));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ConversationStore.LimitException.class)
    ResponseEntity<ApiError> limited(ConversationStore.LimitException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "600").body(ApiError.of(429, "ASSISTANT_LIMIT", e.getMessage()));
    }

    @ExceptionHandler(AssistantService.AssistantDisabledException.class)
    ResponseEntity<ApiError> disabled(AssistantService.AssistantDisabledException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError.of(503, "ASSISTANT_DISABLED", e.getMessage()));
    }

    private static String session(HttpServletRequest request) { return request.getSession(true).getId(); }
}
