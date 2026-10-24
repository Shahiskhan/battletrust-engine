package com.bte.controller;
import com.bte.service.SquadInviteService;
import com.bte.dto.SquadMemberRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController @RequiredArgsConstructor
public class SquadInviteController {
 private final SquadInviteService invites;
 @GetMapping("/api/v1/public/registrations/{id}/squad-invite")
 public SquadInviteService.Invite captain(@PathVariable Long id,Authentication auth,@RequestHeader(value="X-Registration-Ticket",required=false)String ticket){return invites.captain(id,auth,ticket);}
 @GetMapping("/api/v1/public/squads/invites/{token}")public SquadInviteService.Invite preview(@PathVariable String token){return invites.preview(token);}
 @PostMapping("/api/v1/squads/invites/{token}/join")public SquadInviteService.Seat join(@PathVariable String token,@Valid @RequestBody SquadMemberRequest body,Authentication auth){return invites.join(token,body.name(),body.inGameId(),auth);}
}
