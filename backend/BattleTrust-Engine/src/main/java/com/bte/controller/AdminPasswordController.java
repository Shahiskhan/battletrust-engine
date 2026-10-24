package com.bte.controller;

import com.bte.service.AdminPasswordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/admin/account")
public class AdminPasswordController {
    private final AdminPasswordService passwords;
    public record ChangePassword(@NotBlank @Size(max=72) String currentPassword,@NotBlank @Size(min=12,max=72) String newPassword){}
    @PutMapping("/password")
    public ResponseEntity<?> change(@Valid @RequestBody ChangePassword body,Authentication auth){
        passwords.change(auth.getName(),body.currentPassword(),body.newPassword());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(java.util.Map.of("message","Password updated. Sign in again with your new password."));
    }
}
