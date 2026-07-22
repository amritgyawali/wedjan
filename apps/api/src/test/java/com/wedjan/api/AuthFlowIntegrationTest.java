package com.wedjan.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedjan.api.auth.MailService;
import com.wedjan.api.vendor.VendorDtos;
import com.wedjan.api.vendor.VendorService;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Full auth lifecycle against real Postgres + Redis (Testcontainers):
 * signup → OTP verify → login → refresh rotation → reuse detection →
 * multi-role → sessions → rate limiting.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    VendorService vendorService;

    @MockitoBean
    MailService mailService;

    private static final String REFRESH_COOKIE = "wedjan_refresh";

    @Test
    void vendorOnboardApprovePublishAndVersionPrice() throws Exception {
        clearInvocations(mailService);
        String email = "phase2.vendor@example.com";
        String password = "Phase2-secret-pass";
        String ip = "10.2.0.1";

        mvc.perform(withIp(post("/api/v1/auth/signup"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"VENDOR"} """
                                .formatted(email, password)))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendSignupOtp(eq(email), code.capture());
        mvc.perform(withIp(post("/api/v1/auth/verify-email"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code.getValue())))
                .andExpect(status().isOk());
        MvcResult login = mvc.perform(withIp(post("/api/v1/auth/login"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn();
        JsonNode loginJson = readJson(login);
        String token = loginJson.get("accessToken").asText();
        UUID accountId = UUID.fromString(loginJson.at("/account/account/id").asText());

        // Precise step errors are returned before any work is done.
        mvc.perform(post("/api/v1/vendors/me/submit").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VENDOR_SUBMIT_BLOCKED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("step1.business_basics"));

        String about = "We create thoughtful celebrations with an experienced local team, clear communication, "
                + "careful planning, and transparent service from the first conversation. Every detail is tailored "
                + "to the couple and their guests while our practical process keeps the event calm, personal, and "
                + "beautiful from setup through final delivery.";
        mvc.perform(patch("/api/v1/vendors/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "businessName", "Everlight Events", "tagline", "Celebrations with clarity",
                                "about", about, "baseCity", "Kathmandu", "baseCountry", "NP",
                                "currency", "NPR", "onboardingStep", 2))))
                .andExpect(status().isOk());
        JsonNode categoryResponse = readJson(mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk()).andReturn());
        String categoryId = categoryResponse.at("/items/0/id").asText();
        mvc.perform(put("/api/v1/vendors/me/categories").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"categoryId\":\"%s\",\"isPrimary\":true}]}".formatted(categoryId)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/vendors/me/service-areas").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"CITY_RADIUS\",\"city\":\"Kathmandu\",\"country\":\"NP\",\"lat\":27.7172,\"lng\":85.324,\"radiusKm\":60}"))
                .andExpect(status().isCreated());

        List<UUID> media = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            UUID id = UUID.randomUUID();
            media.add(id);
            String kind = i >= 6 ? "DOC" : "IMAGE";
            String mime = i >= 6 ? "application/pdf" : "image/jpeg";
            jdbc.update("""
                    INSERT INTO media_assets(id,owner_account_id,kind,storage_key,mime,bytes,status,created_by)
                    VALUES (?,?,?,?,?,1024,'READY',?)
                    """, id, accountId, kind, "test/phase2/" + id, mime, accountId);
        }

        // Missing/zero prices cannot enter the product at the API boundary.
        mvc.perform(post("/api/v1/vendors/me/packages").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No price\",\"categoryId\":\"%s\",\"pricingModel\":\"FLAT\"}"
                                .formatted(categoryId)))
                .andExpect(status().isBadRequest());
        MvcResult createdPackage = mvc.perform(post("/api/v1/vendors/me/packages")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Signature Celebration","categoryId":"%s","priceCents":12500000,
                                "pricingModel":"FLAT","whatsIncludedMd":"Planning\\nEvent-day service\\nDelivery",
                                "bookingMode":"REQUEST","depositPct":25,"cancellationPolicy":"MODERATE",
                                "coverMediaId":"%s"}
                                """.formatted(categoryId, media.get(0))))
                .andExpect(status().isCreated()).andReturn();
        String packageId = readJson(createdPackage).get("id").asText();
        mvc.perform(post("/api/v1/vendors/me/packages/{id}/publish", packageId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));

        String mediaBody = """
                {"items":[
                {"mediaId":"%s","kind":"COVER","sort":0},
                {"mediaId":"%s","kind":"GALLERY","sort":0},
                {"mediaId":"%s","kind":"GALLERY","sort":1},
                {"mediaId":"%s","kind":"GALLERY","sort":2},
                {"mediaId":"%s","kind":"GALLERY","sort":3},
                {"mediaId":"%s","kind":"GALLERY","sort":4}]}
                """.formatted(media.get(0), media.get(1), media.get(2), media.get(3), media.get(4), media.get(5));
        mvc.perform(put("/api/v1/vendors/me/media").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(mediaBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.listingStrength").value(88));
        MvcResult government = mvc.perform(post("/api/v1/vendors/me/documents")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GOVERNMENT_ID\",\"mediaId\":\"%s\"}".formatted(media.get(6))))
                .andExpect(status().isCreated()).andReturn();
        MvcResult business = mvc.perform(post("/api/v1/vendors/me/documents")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"BUSINESS_REGISTRATION\",\"mediaId\":\"%s\"}".formatted(media.get(7))))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(post("/api/v1/vendors/me/submit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.status").value("SUBMITTED"));

        vendorService.review(accountId, UUID.fromString(readJson(government).get("id").asText()), true,
                new VendorDtos.VerificationReviewRequest("Valid identity", null));
        vendorService.review(accountId, UUID.fromString(readJson(business).get("id").asText()), true,
                new VendorDtos.VerificationReviewRequest("Valid registration", null));
        mvc.perform(get("/api/v1/vendors/everlight-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packages[0].priceCents").value(12500000))
                .andExpect(jsonPath("$.badges.length()").value(2));

        // A published price edit makes an immutable prior-version row.
        mvc.perform(patch("/api/v1/vendors/me/packages/{id}", packageId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Signature Celebration","categoryId":"%s","priceCents":13500000,
                                "pricingModel":"FLAT","whatsIncludedMd":"Planning\\nEvent-day service\\nDelivery",
                                "bookingMode":"REQUEST","depositPct":25,"cancellationPolicy":"MODERATE",
                                "coverMediaId":"%s"}
                                """.formatted(categoryId, media.get(0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.priceCents").value(13500000));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM package_price_versions WHERE package_id=?",
                Long.class, UUID.fromString(packageId))).isEqualTo(1L);

        // Sensitive edits re-enter review while the already-verified listing remains live.
        mvc.perform(patch("/api/v1/vendors/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"businessName\":\"Everlight Events Nepal\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.status").value("UNDER_REVIEW"));
        mvc.perform(get("/api/v1/vendors/everlight-events")).andExpect(status().isOk());
    }

    @Test
    void fullSignupVerifyLoginRefreshCycle() throws Exception {
        String email = "alice@example.com";
        String password = "Sup3r-secret-pass";
        String ip = "10.0.0.1";

        // Signup → generic 202 + OTP mail
        mvc.perform(withIp(post("/api/v1/auth/signup"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"CUSTOMER"}""".formatted(email, password)))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendSignupOtp(eq(email), codeCaptor.capture());
        String otp = codeCaptor.getValue();

        // Login before verification → blocked
        mvc.perform(withIp(post("/api/v1/auth/login"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_NOT_VERIFIED"));

        // Wrong OTP → 400; right OTP → verified
        mvc.perform(withIp(post("/api/v1/auth/verify-email"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"000000"}""".formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CODE"));
        mvc.perform(withIp(post("/api/v1/auth/verify-email"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}""".formatted(email, otp)))
                .andExpect(status().isOk());

        // Login (web): access token in body, refresh in httpOnly cookie only
        MvcResult login = mvc.perform(withIp(post("/api/v1/auth/login"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","deviceName":"test"}"""
                                .formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.account.roles[0]").value("CUSTOMER"))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                .andReturn();
        String accessToken = readJson(login).get("accessToken").asText();
        Cookie refresh1 = login.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(refresh1).isNotNull();

        // /me with the access token
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.email").value(email));

        // /me without a token → envelope 401
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));

        // Refresh rotation
        MvcResult refreshed = mvc.perform(withIp(post("/api/v1/auth/refresh"), ip).cookie(refresh1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie refresh2 = refreshed.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(refresh2).isNotNull();
        assertThat(refresh2.getValue()).isNotEqualTo(refresh1.getValue());

        // Reusing the rotated token → 401 + whole family revoked
        mvc.perform(withIp(post("/api/v1/auth/refresh"), ip).cookie(refresh1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_REUSED"));
        mvc.perform(withIp(post("/api/v1/auth/refresh"), ip).cookie(refresh2))
                .andExpect(status().isUnauthorized());

        // Fresh login still works; add VENDOR role (idempotent)
        MvcResult relogin = mvc.perform(withIp(post("/api/v1/auth/login"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken2 = readJson(relogin).get("accessToken").asText();
        mvc.perform(post("/api/v1/auth/roles/add")
                        .header("Authorization", "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"VENDOR"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));
        mvc.perform(post("/api/v1/auth/roles/add")
                        .header("Authorization", "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"VENDOR"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));

        // Sessions list shows the active session
        mvc.perform(get("/api/v1/me/sessions")
                        .header("Authorization", "Bearer " + accessToken2)
                        .cookie(relogin.getResponse().getCookie(REFRESH_COOKIE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].current").value(true));

        // Logout-all revokes everything
        mvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + accessToken2))
                .andExpect(status().isNoContent());
        mvc.perform(withIp(post("/api/v1/auth/refresh"), ip)
                        .cookie(relogin.getResponse().getCookie(REFRESH_COOKIE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mobileClientReceivesRefreshTokenInBody() throws Exception {
        String email = "mobile@example.com";
        String password = "An0ther-secret-pw";
        String ip = "10.0.0.2";

        mvc.perform(withIp(post("/api/v1/auth/signup"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"FREELANCER"}"""
                                .formatted(email, password)))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendSignupOtp(eq(email), codeCaptor.capture());
        mvc.perform(withIp(post("/api/v1/auth/verify-email"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}""".formatted(email, codeCaptor.getValue())))
                .andExpect(status().isOk());

        MvcResult login = mvc.perform(withIp(post("/api/v1/auth/login"), ip)
                        .header("X-Wedjan-Client", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        assertThat(login.getResponse().getCookie(REFRESH_COOKIE)).isNull();

        // Mobile refresh via body
        String refreshToken = readJson(login).get("refreshToken").asText();
        mvc.perform(withIp(post("/api/v1/auth/refresh"), ip)
                        .header("X-Wedjan-Client", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void sixthLoginAttemptWithinAMinuteIsRateLimited() throws Exception {
        String ip = "10.9.9.9";
        for (int i = 0; i < 5; i++) {
            mvc.perform(withIp(post("/api/v1/auth/login"), ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"nobody@example.com","password":"wrong-password-1"}"""))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(withIp(post("/api/v1/auth/login"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com","password":"wrong-password-1"}"""))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void signupWithExistingEmailStaysGeneric() throws Exception {
        String ip = "10.0.0.3";
        mvc.perform(withIp(post("/api/v1/auth/signup"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dupe@example.com","password":"Sup3r-secret-pass","role":"CUSTOMER"}"""))
                .andExpect(status().isAccepted());
        // Same email again → identical generic response (no enumeration)
        mvc.perform(withIp(post("/api/v1/auth/signup"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dupe@example.com","password":"Different-pass-99","role":"VENDOR"}"""))
                .andExpect(status().isAccepted());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder withIp(MockHttpServletRequestBuilder builder, String ip) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }
}
