package com.bte.service;

import com.bte.repository.UserRepository;
import com.bte.exception.BadRequestException;
import com.bte.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;

@Service @RequiredArgsConstructor
public class AdminPasswordService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    @Transactional
    public void change(String email,String currentPassword,String newPassword) {
        var user=users.findByEmailForUpdate(email).orElseThrow(()->new UnauthorizedException("Account not found"));
        if(user.getRole()!=com.bte.enums.Role.ROLE_ADMIN)throw new UnauthorizedException("Admin access required");
        if(currentPassword.getBytes(StandardCharsets.UTF_8).length>72 || !encoder.matches(currentPassword,user.getPassword()))throw new UnauthorizedException("Current password is incorrect");
        if(newPassword.isBlank() || newPassword.length()<12 || newPassword.getBytes(StandardCharsets.UTF_8).length>72)throw new BadRequestException("New password must contain at least 12 characters and at most 72 UTF-8 bytes");
        if(encoder.matches(newPassword,user.getPassword()))throw new BadRequestException("Choose a different new password");
        user.setPassword(encoder.encode(newPassword));
        jdbc.update("update auth_sessions set revoked=true, revoked_at=UTC_TIMESTAMP(6) where user_id=? and revoked=false",user.getId());
    }
}
