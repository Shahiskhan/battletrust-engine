package com.bte.dto;

import com.bte.enums.Role;

public record UserDto(
		Long id,
		String name,
		String email,
		Role role,
		String profilePictureUrl
) {
    public UserDto { if(role==Role.ROLE_ADMIN)profilePictureUrl=null; }
}
