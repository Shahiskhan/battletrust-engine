package com.bte.controller;
import com.bte.repository.*;
import com.bte.service.*;
import com.bte.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
@RestController @RequiredArgsConstructor @Transactional
public class OrganizerRoomControls {
 private final TournamentRepository tournaments;private final TournamentAccessService access;private final InviteQrService qr;private final UserRepository users;
 @org.springframework.beans.factory.annotation.Value("${app.auth.allowed-origin}")private String origin;
 public record Settings(@NotNull @Pattern(regexp="PUBLIC|PRIVATE")String visibility,@NotNull Boolean roomChatEnabled){}
 private com.bte.entity.Tournament managed(Long id,Authentication auth){var t=tournaments.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Room not found"));if(!access.manages(t,auth))throw new org.springframework.security.access.AccessDeniedException("Only the room organizer or admin can manage this room");return t;}
 @PutMapping("/api/v1/organizer/tournaments/{id}/controls")public java.util.Map<String,Object> update(@PathVariable Long id,@Valid @RequestBody Settings settings,Authentication auth){var t=managed(id,auth);t.setVisibility(settings.visibility());t.setRoomChatEnabled(settings.roomChatEnabled());return data(t);}
 @GetMapping("/api/v1/organizer/tournaments/{id}/controls")public java.util.Map<String,Object> get(@PathVariable Long id,Authentication auth){return data(managed(id,auth));}
 private java.util.Map<String,Object> data(com.bte.entity.Tournament t){if(t.getRoomInviteToken()==null)t.setRoomInviteToken(java.util.UUID.randomUUID().toString());String link=origin+"/rooms/"+t.getId()+"?invite="+t.getRoomInviteToken();return java.util.Map.of("visibility",t.getVisibility(),"roomChatEnabled",t.isRoomChatEnabled(),"inviteLink",link,"qrDataUrl",qr.image(link));}
 @PostMapping("/api/v1/admin/maintenance/archive-demo-rooms")public java.util.Map<String,Integer> archive(){int count=0;for(var t:tournaments.findAll())if(!t.isArchived() && (t.getTitle().startsWith("TEST ") || java.util.Set.of("Friday Night Showdown","The Clutch Protocol","Survival of the Fittest").contains(t.getTitle()))){t.setArchived(true);count++;}for(var u:users.findAll())if(u.getName().startsWith("TEST ") && u.getEmail().endsWith("@example.com"))u.setDemoAccount(true);return java.util.Map.of("archived",count);}
}
