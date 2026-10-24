package com.bte.service;

import com.bte.dto.AuthResponse;

public record IssuedAuthTokens(AuthResponse response, String refreshToken) {
}
