package app.api.exception;

import app.api.user.controller.UserController;
import app.api.user.service.UserService;
import jakarta.validation.Validation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.web.SortHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private UserService service;
    private MockMvc mvc;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = mock(UserService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UserController(service, 100), new ParameterController())
                .setCustomArgumentResolvers(new SortHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void businessCodesMapToStatusAndEnvelope(ErrorCode code) throws Exception {
        when(service.findById(id)).thenThrow(new BusinessException(code));
        var result = mvc.perform(get("/api/v1/users/" + id).queryParam("secret", "hidden"))
                .andExpect(status().is(code.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code.getCode()))
                .andExpect(jsonPath("$.message").value(code.getMessage()))
                .andExpect(jsonPath("$.path").value("/api/v1/users/" + id))
                .andExpect(jsonPath("$.errors").isMap()).andReturn();
        Instant.parse(JsonMapper.builder().build().readTree(result.getResponse().getContentAsString())
                .get("timestamp").asString());
    }

    @Test
    void resourceNotFoundPreservesCustomMessage() throws Exception {
        when(service.findById(id)).thenThrow(new ResourceNotFoundException("Profile unavailable"));
        mvc.perform(get("/api/v1/users/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Profile unavailable"));
    }

    @Test
    void malformedJsonIsSafe() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{secret"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request"));
        verifyNoInteractions(service);
    }

    @Test
    void missingParameterAndTypeMismatchUseEnvelope() throws Exception {
        mvc.perform(get("/required")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/users/bad-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void validationIncludesFieldsAndPaginationUsesSameCode() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"displayName\":\"\",\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.username").exists()).andExpect(jsonPath("$.errors.email").exists());
        mvc.perform(get("/api/v1/users").param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void constraintViolationIncludesPropertyErrors() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new ValidatedValue(""));
            when(service.findById(id)).thenThrow(new ConstraintViolationException(violations));
            mvc.perform(get("/api/v1/users/" + id)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors.name").exists());
        }
    }

    @Test
    void unexpectedAndDatabaseErrorsHideDetails() throws Exception {
        when(service.findById(id)).thenThrow(new IllegalStateException("secret database credentials"));
        mvc.perform(get("/api/v1/users/" + id)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
        reset(service);
        when(service.findById(id)).thenThrow(new DataIntegrityViolationException("secret SQL"));
        mvc.perform(get("/api/v1/users/" + id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message").value(ErrorCode.RESOURCE_CONFLICT.getMessage()));
    }

    @Test
    void authenticationInfrastructureFailureReturns500WithoutDetails() throws Exception {
        when(service.findById(id)).thenThrow(
                new org.springframework.security.authentication.InternalAuthenticationServiceException("private database error"));
        mvc.perform(get("/api/v1/users/" + id)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void frameworkMethodNotAllowedPreservesStatusAndAllowHeader() throws Exception {
        mvc.perform(patch("/api/v1/users/" + id)).andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow")).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    record ValidatedValue(@NotBlank String name) {}

    @RestController
    static class ParameterController {
        @GetMapping("/required")
        String required(@RequestParam String value) { return value; }
    }
}
