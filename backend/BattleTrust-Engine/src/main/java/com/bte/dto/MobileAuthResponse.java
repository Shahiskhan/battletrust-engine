package com.bte.dto;

public record MobileAuthResponse(AuthResponse authentication, String refreshToken) {
}
