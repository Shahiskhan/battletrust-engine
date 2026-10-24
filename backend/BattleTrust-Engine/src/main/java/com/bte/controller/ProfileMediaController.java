package com.bte.controller;
import com.bte.repository.*;
import com.bte.service.TournamentAccessService;
import com.bte.service.media.MediaService;
import com.bte.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
@RestController @RequiredArgsConstructor
public class ProfileMediaController {
    private final MediaService media;
    private final UserRepository users;
    private final TournamentRepository tournaments;
    private final TournamentAccessService access;
    @PostMapping(value="/api/v1/me/avatar",consumes="multipart/form-data") @Transactional
    public java.util.Map<String,String> avatar(@RequestPart("file")MultipartFile file,Authentication auth) {
        var user=users.findByEmailForUpdate(auth.getName()).orElseThrow();String url=media.uploadFile(file,"avatars");user.setProfilePictureUrl(url);return java.util.Map.of("url",url);
    }
    @PostMapping(value="/api/v1/organizer/tournaments/{id}/banner",consumes="multipart/form-data") @Transactional
    public java.util.Map<String,String> banner(@PathVariable Long id,@RequestPart("file")MultipartFile file,Authentication auth) {
        var tournament=tournaments.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Tournament not found"));
        if(!access.manages(tournament,auth))throw new UnauthorizedException("Only the organizer or admin may change the banner");
        String url=media.uploadFile(file,"tournament-banners");tournament.setTournamentLogoUrl(url);return java.util.Map.of("url",url);
    }
}
