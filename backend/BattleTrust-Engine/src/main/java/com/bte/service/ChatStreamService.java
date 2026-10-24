package com.bte.service;

import com.bte.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ChatStreamService {
    private final ChatService chat;
    private final AuthSessionRepository sessions;
    private final ConcurrentHashMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private record Subscription(Long tournamentId, Authentication auth, String ticket, SseEmitter emitter) {}

    public SseEmitter subscribe(Long tournamentId, Authentication auth, String ticket) {
        var history = chat.history(tournamentId, 0, 50, auth, ticket); // Authorize before writing any bytes.
        if (subscriptions.size() >= 500) throw new com.bte.exception.ServiceUnavailableException("Chat capacity reached; retry shortly");
        SseEmitter emitter = new SseEmitter(25000L);
        String key = UUID.randomUUID().toString();
        subscriptions.put(key, new Subscription(tournamentId, auth, ticket, emitter));
        emitter.onCompletion(() -> subscriptions.remove(key));
        emitter.onTimeout(() -> { subscriptions.remove(key); emitter.complete(); });
        emitter.onError(error -> subscriptions.remove(key));
        try { emitter.send(SseEmitter.event().name("messages").data(history.getContent())); }
        catch (Exception error) { subscriptions.remove(key); emitter.completeWithError(error); }
        return emitter;
    }

    @Scheduled(fixedDelay = 2000)
    public void broadcast() {
        subscriptions.forEach((key, sub) -> {
            try {
                if (sub.auth() != null && sub.auth().getDetails() instanceof String sessionId
                        && sessions.findActiveWithUser(sessionId, java.time.Instant.now()).isEmpty()) {
                    subscriptions.remove(key); sub.emitter().complete(); return;
                }
                var messages = chat.history(sub.tournamentId(), 0, 50, sub.auth(), sub.ticket());
                sub.emitter().send(SseEmitter.event().name("messages").data(messages.getContent()));
            } catch (Exception error) { subscriptions.remove(key); sub.emitter().complete(); }
        });
    }
}
