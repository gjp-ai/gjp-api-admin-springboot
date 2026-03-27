package org.ganjp.api.auth.register;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.ganjp.api.common.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for user registration operations
 * 
 * This RESTful controller focuses exclusively on user registration functionality
 * as part of the authentication system. It follows RESTful API design principles
 * by using appropriate HTTP methods and status codes.
 * 
 * Base URI: /v1/register
 * 
 * Resources:
 * - POST / : Register a new user
 * 
 * For account management operations, see {@link UserController}
 * For authentication operations, see {@link TokenController}
 */
@Slf4j
@RestController
@RequestMapping("/v1/register")
@RequiredArgsConstructor
public class RegisterController {

    private final RegisterService registerService;

    /**
     * Register a new user with ROLE_USER role
     *
     * @param registerRequest The registration request data
     * @return The created user data
     */
    @PreAuthorize("permitAll()")
    @PostMapping
    public ResponseEntity<ApiResponse<RegisterResponse>> registerUser(
            @Valid @RequestBody RegisterRequest registerRequest,
            HttpServletRequest request) {
        // Store request data for audit logging (before service call so it's available on exception)
        request.setAttribute("loginUsername", registerRequest.getUsername());
        request.setAttribute("loginRequestData", sanitizeRegisterRequest(registerRequest));

        RegisterResponse registerResponse = registerService.register(registerRequest);
        
        // Store response data and resource ID for audit logging
        request.setAttribute("loginResponseData", sanitizeRegisterResponse(registerResponse));
        request.setAttribute("loginResourceId", registerResponse.getId());
        
        ApiResponse<RegisterResponse> response = ApiResponse.<RegisterResponse>success(registerResponse, "User registered successfully");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Helper method to sanitize registration request for audit logging (remove password)
     */
    private Object sanitizeRegisterRequest(RegisterRequest registerRequest) {
        Map<String, Object> sanitized = new HashMap<>();
        sanitized.put("username", registerRequest.getUsername());
        sanitized.put("email", registerRequest.getEmail());
        sanitized.put("mobileCountryCode", registerRequest.getMobileCountryCode());
        sanitized.put("mobileNumber", registerRequest.getMobileNumber());
        sanitized.put("nickname", registerRequest.getNickname());
        // Deliberately exclude password for security
        return sanitized;
    }

    /**
     * Helper method to sanitize registration response for audit logging
     */
    private Object sanitizeRegisterResponse(RegisterResponse registerResponse) {
        Map<String, Object> sanitized = new HashMap<>();
        sanitized.put("id", registerResponse.getId());
        sanitized.put("username", registerResponse.getUsername());
        sanitized.put("email", registerResponse.getEmail());
        sanitized.put("accountStatus", registerResponse.getAccountStatus());
        sanitized.put("active", registerResponse.getActive());
        return sanitized;
    }
    
    // Validation errors are handled by GlobalExceptionHandler
}
