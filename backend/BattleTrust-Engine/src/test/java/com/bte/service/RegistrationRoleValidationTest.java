package com.bte.service;
import com.bte.dto.RegisterRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RegistrationRoleValidationTest {
 @Test void acceptsOnlyPublicSignupRoles(){try(var factory=Validation.buildDefaultValidatorFactory()){var validator=factory.getValidator();for(String role:new String[]{"ROLE_PLAYER","ROLE_ORGANIZER"})assertTrue(validator.validate(new RegisterRequest("TEST","test@example.com","TestPassword123!",null,role)).isEmpty());for(String role:new String[]{"ROLE_ADMIN","ROLE_LEADER","ROLE_GUEST",""})assertFalse(validator.validate(new RegisterRequest("TEST","test@example.com","TestPassword123!",null,role)).isEmpty());}}
}
