package com.bte.dto;

import com.bte.enums.Role;

public record AuthResponse(String token, String tokenType, long expiresIn, Role role, UserDto user) {
}
