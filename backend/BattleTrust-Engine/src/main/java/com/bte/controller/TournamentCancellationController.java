package com.bte.controller;
import com.bte.service.TournamentCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController @RequiredArgsConstructor
public class TournamentCancellationController {
    private final TournamentCancellationService service;
    @DeleteMapping({"/api/v1/tournaments/{id}", "/api/v1/organizer/tournaments/{id}"})
    public com.bte.dto.TournamentResponse cancel(@PathVariable Long id, @RequestParam(defaultValue = "Organizer cancellation") String reason,
        @RequestParam(defaultValue = "false") boolean waiveForfeiture, Authentication auth) { return service.cancel(id, reason, waiveForfeiture, auth); }
}
