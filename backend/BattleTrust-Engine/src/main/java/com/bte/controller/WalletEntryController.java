package com.bte.controller;
import com.bte.service.WalletEntryService;
import com.bte.dto.RegistrationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController @RequiredArgsConstructor
public class WalletEntryController {
    private final WalletEntryService service;
    @PostMapping("/api/v1/me/registrations/{id}/pay")
    public RegistrationResponse pay(@PathVariable Long id, Authentication auth) { return service.pay(id, auth.getName()); }
}
