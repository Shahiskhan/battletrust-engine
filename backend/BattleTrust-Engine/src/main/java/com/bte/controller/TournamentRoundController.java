package com.bte.controller;
import com.bte.service.*;import com.bte.repository.*;import com.bte.dto.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import org.springframework.security.core.Authentication;
@RestController @RequiredArgsConstructor
public class TournamentRoundController {
 private final TournamentRoundService rounds;private final TournamentRepository tournaments;private final TournamentAccessService access;private final TournamentStandingsService standings;
 public record Plan(@NotNull @Size(min=1,max=20)java.util.List<@NotBlank @Size(max=100)String> maps){}public record RegistrationState(boolean open){}
 @PutMapping("/api/v1/organizer/tournaments/{id}/rounds") public void plan(@PathVariable Long id,@Valid @RequestBody Plan body,Authentication auth){rounds.plan(id,body.maps(),auth);}
 @GetMapping("/api/v1/organizer/tournaments/{id}/rounds")public java.util.List<TournamentRoundService.RoundView> management(@PathVariable Long id,Authentication auth){return rounds.list(id,auth,true,null);}
 @PutMapping("/api/v1/organizer/tournaments/{id}/rounds/{number}/credentials")public void credentials(@PathVariable Long id,@PathVariable int number,@Valid @RequestBody RoomCredentialsRequest body,Authentication auth){rounds.credentials(id,number,body,auth);}
 @PostMapping("/api/v1/organizer/tournaments/{id}/rounds/{number}/start")public void start(@PathVariable Long id,@PathVariable int number,Authentication auth){rounds.start(id,number,auth);}
 @PutMapping("/api/v1/organizer/tournaments/{id}/registration")public void registration(@PathVariable Long id,@RequestBody RegistrationState body,Authentication auth){rounds.registration(id,body.open(),auth);}
 @GetMapping("/api/v1/public/tournaments/{id}/rounds")public java.util.List<TournamentRoundService.RoundView> participant(@PathVariable Long id,Authentication auth,@RequestHeader(value="X-Registration-Ticket",required=false)String ticket){return rounds.list(id,auth,false,ticket);}
 @GetMapping("/api/v1/public/tournaments/{id}/standings")public java.util.List<TournamentStandingsService.Standing> standings(@PathVariable Long id,Authentication auth,@RequestHeader(value="X-Registration-Ticket",required=false)String ticket){var t=tournaments.findById(id).orElseThrow(()->new com.bte.exception.ResourceNotFoundException("Tournament not found"));access.requireAccess(t,auth,ticket);return standings.standings(id);}
}
