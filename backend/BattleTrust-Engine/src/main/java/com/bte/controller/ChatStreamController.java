package com.bte.controller;
import com.bte.service.ChatStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class ChatStreamController {
    private final ChatStreamService streams;
    @GetMapping(value = "/api/v1/chat/tournament/{id}/stream", produces = "text/event-stream")
    public SseEmitter stream(@PathVariable Long id, Authentication auth,
            @RequestHeader(value = "X-Registration-Ticket", required = false) String ticket) {
        return streams.subscribe(id, auth, ticket);
    }
}
