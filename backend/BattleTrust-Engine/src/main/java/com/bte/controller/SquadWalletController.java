package com.bte.controller;
import com.bte.service.SquadWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
@RestController @RequiredArgsConstructor
public class SquadWalletController {
    private final SquadWalletService service;
    public record Claim(@NotBlank String claimTicket){}
    @GetMapping("/api/v1/public/registrations/{id}/squad-wallets") public java.util.List<SquadWalletService.MemberWallet> details(@PathVariable Long id,Authentication auth,@RequestHeader(value="X-Registration-Ticket",required=false)String ticket){return service.details(id,auth,ticket);}
    @PostMapping("/api/v1/me/squad-members/claim") public SquadWalletService.MemberWallet claim(@Valid @RequestBody Claim request,Authentication auth){return service.claim(request.claimTicket(),auth.getName());}
    @GetMapping("/api/v1/me/squad-memberships") public java.util.List<SquadWalletService.Membership> memberships(Authentication auth){return service.memberships(auth.getName());}
}
