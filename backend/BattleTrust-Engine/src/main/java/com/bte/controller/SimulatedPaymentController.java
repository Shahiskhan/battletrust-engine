package com.bte.controller;
import com.bte.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@RestController @RequiredArgsConstructor @Profile("dev")
@ConditionalOnProperty(name = "app.payments.simulation-enabled", havingValue = "true")
public class SimulatedPaymentController {
    private final VoucherService service;
    @PostMapping("/api/v1/payments/simulate-pay/{voucherId}")
    public com.bte.dto.RegistrationResponse pay(@PathVariable String voucherId, Authentication auth,
        @RequestHeader(value = "X-Registration-Ticket", required = false) String ticket) { return service.simulate(voucherId, auth, ticket); }
}
