package com.bte.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Per-instance abuse protection; production gateways should enforce a shared limit as well. */
@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private static class Window { long start = System.currentTimeMillis(); int count; }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        String path = req.getServletPath();
        boolean auth = path.startsWith("/api/v1/auth/") && !path.endsWith("/refresh") && !path.endsWith("/logout");
        boolean registration = (path.equals("/api/v1/public/registrations") || path.startsWith("/api/v1/squads/invites/"));
        boolean chat = path.equals("/api/v1/chat/send");
        if (!req.getMethod().equals("POST") || (!auth && !registration && !chat)) { chain.doFilter(req, res); return; }
        long now = System.currentTimeMillis();
        if (windows.size() > 10000) windows.entrySet().removeIf(entry -> now - entry.getValue().start > 60000);
        String key = req.getRemoteAddr() + ":" + (chat ? "chat" : auth ? "auth" : "join");
        Window window = windows.computeIfAbsent(key, ignored -> new Window());
        boolean limited;
        synchronized (window) {
            if (now - window.start >= 60000) { window.start = now; window.count = 0; }
            limited = ++window.count > (chat ? 120 : 30);
        }
        if (limited) { res.setStatus(429); res.setContentType("application/json"); res.setHeader("Retry-After", "60"); res.getWriter().write("{\"message\":\"Too many requests. Please retry in a minute.\"}"); return; }
        chain.doFilter(req, res);
    }
}
