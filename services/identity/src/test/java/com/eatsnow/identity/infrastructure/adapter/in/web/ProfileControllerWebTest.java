package com.eatsnow.identity.infrastructure.adapter.in.web;

import com.eatsnow.common.domain.exception.EmailAlreadyRegisteredException;
import com.eatsnow.common.domain.exception.InvalidRoleLinkException;
import com.eatsnow.common.domain.exception.InvalidTransitionException;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.common.web.GlobalExceptionHandler;
import com.eatsnow.common.web.filter.CorrelationIdFilter;
import com.eatsnow.identity.domain.model.IssuedToken;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.in.AdminProfileUseCase;
import com.eatsnow.identity.domain.port.in.AuthenticateUseCase;
import com.eatsnow.identity.domain.port.in.GetProfileUseCase;
import com.eatsnow.identity.domain.port.in.UpdateMyProfileUseCase;
import com.eatsnow.identity.domain.port.out.TokenIssuerPort;
import com.eatsnow.identity.infrastructure.adapter.out.crypto.RsaTokenIssuer;
import com.eatsnow.identity.infrastructure.config.security.JwtAuthenticationFilter;
import com.eatsnow.identity.infrastructure.config.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProfileController.class, AdminProfileController.class, AuthController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class, CorrelationIdFilter.class})
class ProfileControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetProfileUseCase getProfileUseCase;

    @MockBean
    private UpdateMyProfileUseCase updateMyProfileUseCase;

    @MockBean
    private AdminProfileUseCase adminProfileUseCase;

    @MockBean
    private AuthenticateUseCase authenticateUseCase;

    @MockBean
    private RsaTokenIssuer rsaTokenIssuer;

    private final UUID userId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private final UUID adminId = UUID.fromString("00000000-0000-4000-8000-000000000099");

    @Test
    @DisplayName("GET /api/v1/profiles/me - Returns 200 with user profile")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000001", roles = "USER")
    void testGetMyProfile_Success() throws Exception {
        Profile profile = new Profile(
                userId, "Alex", "alex@example.test", Set.of(Role.USER),
                null, null, "hash", true, Instant.now(), Instant.now()
        );
        when(getProfileUseCase.getProfileById(userId)).thenReturn(profile);

        mockMvc.perform(get("/api/v1/profiles/me")
                        .header("X-Correlation-ID", "00000000-0000-4000-8000-000000000040"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "00000000-0000-4000-8000-000000000040"))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.displayName").value("Alex"))
                .andExpect(jsonPath("$.email").value("alex@example.test"))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/profiles/me - Returns 401 UNAUTHENTICATED when unauthenticated")
    void testGetMyProfile_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/profiles/me - Returns 200 with updated displayName")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000001", roles = "USER")
    void testUpdateMyProfile_Success() throws Exception {
        Profile updated = new Profile(
                userId, "Alex Rivera", "alex@example.test", Set.of(Role.USER),
                null, null, "hash", true, Instant.now(), Instant.now()
        );
        when(updateMyProfileUseCase.updateDisplayName(eq(userId), eq("Alex Rivera"), any(), any()))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/v1/profiles/me")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alex Rivera\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alex Rivera"));
    }

    @Test
    @DisplayName("PATCH /api/v1/profiles/me - Returns 400 when missing Idempotency-Key")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000001", roles = "USER")
    void testUpdateMyProfile_MissingIdempotencyKey() throws Exception {
        mockMvc.perform(patch("/api/v1/profiles/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alex Rivera\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/profiles - Returns 403 FORBIDDEN when called by USER")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000001", roles = "USER")
    void testAdminGetProfiles_ForbiddenForUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profiles"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/profiles - Returns 200 paginated list when called by ADMIN")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000099", roles = "ADMIN")
    void testAdminGetProfiles_SuccessForAdmin() throws Exception {
        Profile profile = new Profile(
                userId, "Alex", "alex@example.test", Set.of(Role.USER),
                null, null, "hash", true, Instant.now(), Instant.now()
        );
        when(adminProfileUseCase.listProfiles(anyInt(), any(), any()))
                .thenReturn(new PageResult<>(List.of(profile), null));

        mockMvc.perform(get("/api/v1/admin/profiles?role=USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].displayName").value("Alex"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/profiles - Returns 201 Created for ADMIN with generated driverId")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000099", roles = "ADMIN")
    void testAdminCreateProfile_Success() throws Exception {
        UUID driverId = UUID.randomUUID();
        Profile created = new Profile(
                UUID.randomUUID(), "Sam", "sam@example.test", Set.of(Role.DRIVER),
                null, driverId, "hash", true, Instant.now(), Instant.now()
        );
        when(adminProfileUseCase.createProfile(eq(adminId), eq("Sam"), eq("sam@example.test"), any(), any(), any(), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/admin/profiles")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Sam\",\"email\":\"sam@example.test\",\"roles\":[\"DRIVER\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Sam"))
                .andExpect(jsonPath("$.driverId").value(driverId.toString()))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/admin/profiles - Returns 422 INVALID_ROLE_LINK on invalid role links")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000099", roles = "ADMIN")
    void testAdminCreateProfile_InvalidRoleLink() throws Exception {
        when(adminProfileUseCase.createProfile(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new InvalidRoleLinkException("RESTAURANT role requires a valid restaurantId"));

        mockMvc.perform(post("/api/v1/admin/profiles")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Restaurant\",\"email\":\"resto@example.test\",\"roles\":[\"RESTAURANT\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ROLE_LINK"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/profiles - Returns 409 EMAIL_ALREADY_REGISTERED on duplicate email")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000099", roles = "ADMIN")
    void testAdminCreateProfile_DuplicateEmail() throws Exception {
        when(adminProfileUseCase.createProfile(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new EmailAlreadyRegisteredException("Email already registered: sam@example.test"));

        mockMvc.perform(post("/api/v1/admin/profiles")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Sam\",\"email\":\"sam@example.test\",\"roles\":[\"DRIVER\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/profiles/{profileId} - Returns 409 INVALID_TRANSITION when admin deactivates self")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000099", roles = "ADMIN")
    void testAdminUpdateStatus_AdminSelfDeactivation_Forbidden() throws Exception {
        when(adminProfileUseCase.updateProfileStatus(eq(adminId), eq(adminId), anyBoolean(), any(), any()))
                .thenThrow(new InvalidTransitionException("An ADMIN cannot deactivate their own profile"));

        mockMvc.perform(patch("/api/v1/admin/profiles/" + adminId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/token - Returns 200 with access token")
    void testCreateToken_Success() throws Exception {
        IssuedToken token = new IssuedToken("header.payload.signature", "Bearer", 900, userId, Set.of(Role.USER), null, null);
        when(authenticateUseCase.authenticate("alex@example.test", "secret123")).thenReturn(token);

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.test\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("header.payload.signature"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("GET /.well-known/jwks.json - Returns 200 with JWKS JSON")
    void testGetJwks_Success() throws Exception {
        when(rsaTokenIssuer.getPublicJwksJson()).thenReturn("{\"keys\":[{\"kty\":\"RSA\"}]}");

        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/token - Returns 400 when email or password is blank")
    void testCreateToken_BlankCredentials_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/profiles - Returns 400 when displayName or email is invalid")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000099", roles = "ADMIN")
    void testAdminCreateProfile_InvalidFields_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/profiles")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\",\"email\":\"not-an-email\",\"roles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/profiles - Returns 400 when missing Idempotency-Key header")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000099", roles = "ADMIN")
    void testAdminCreateProfile_MissingIdempotencyKey_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Sam\",\"email\":\"sam@example.test\",\"roles\":[\"DRIVER\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/profiles/{profileId} - Returns 400 when missing Idempotency-Key header")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000099", roles = "ADMIN")
    void testAdminUpdateStatus_MissingIdempotencyKey_Returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/profiles/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/profiles/{profileId} - Returns 400 when active field is null")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000099", roles = "ADMIN")
    void testAdminUpdateStatus_NullActive_Returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/profiles/" + userId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /api/v1/profiles/me - Returns 400 on malformed JSON payload")
    @WithMockUser(username = "00000000-0000-4000-8000-000000000001", roles = "USER")
    void testUpdateMyProfile_MalformedJson_Returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/profiles/me")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed json!!}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
