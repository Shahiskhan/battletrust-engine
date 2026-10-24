package com.bte.service;
import com.bte.entity.User;
import com.bte.enums.Role;
import com.bte.repository.UserRepository;
import com.bte.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminPasswordServiceTest {
 private final UserRepository users=mock(UserRepository.class);
 private final PasswordEncoder encoder=mock(PasswordEncoder.class);
 private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final AdminPasswordService service=new AdminPasswordService(users,encoder,jdbc);
 private User user(Role role){var u=User.builder().id(1L).email("admin@example.com").role(role).password("oldHash").build();when(users.findByEmailForUpdate(u.getEmail())).thenReturn(Optional.of(u));return u;}
 @Test void rejectsIncorrectCurrentPassword(){user(Role.ROLE_ADMIN);assertThrows(UnauthorizedException.class,()->service.change("admin@example.com","wrong","LongNewPassword123!"));verifyNoInteractions(jdbc);}
 @Test void rejectsNonAdmin(){user(Role.ROLE_PLAYER);assertThrows(UnauthorizedException.class,()->service.change("admin@example.com","current","LongNewPassword123!"));verifyNoInteractions(jdbc);}
 @Test void rejectsShortAndOversizedPasswords(){user(Role.ROLE_ADMIN);when(encoder.matches("current","oldHash")).thenReturn(true);for(String value:new String[]{"short","é".repeat(37)})assertThrows(BadRequestException.class,()->service.change("admin@example.com","current",value));verifyNoInteractions(jdbc);}
 @Test void hashesNewPasswordAndRevokesSessions(){var u=user(Role.ROLE_ADMIN);when(encoder.matches("current","oldHash")).thenReturn(true);when(encoder.encode("LongNewPassword123!")).thenReturn("newHash");service.change(u.getEmail(),"current","LongNewPassword123!");assertEquals("newHash",u.getPassword());verify(jdbc).update(anyString(),eq(1L));}
 @Test void rejectsPasswordReuse(){user(Role.ROLE_ADMIN);when(encoder.matches(anyString(),eq("oldHash"))).thenReturn(true);assertThrows(BadRequestException.class,()->service.change("admin@example.com","current","LongNewPassword123!"));verifyNoInteractions(jdbc);}
}
