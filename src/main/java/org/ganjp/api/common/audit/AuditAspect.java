package org.ganjp.api.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.ganjp.api.common.audit.AuditService;
import org.ganjp.api.common.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Aspect for auditing API calls.
 * Automatically logs all HTTP requests to controllers.
 * 
 * Note: Authentication endpoints (/auth/login, /auth/logout, /auth/signup, /auth/tokens) are excluded
 * from this aspect as they are specifically handled by AuthenticationAuditInterceptor
 * to prevent duplicate audit logging.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    /**
     * Intercept all controller methods (all HTTP methods including GET)
     */
    @Around("execution(* org.ganjp.api.*.controller.*.*(..))")
    public Object auditApiCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // Get the HTTP request
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        
        HttpServletRequest request = attributes.getRequest();
        String httpMethod = request.getMethod();
        String endpoint = request.getRequestURI();
        
        // Skip authentication endpoints as they are handled by AuthenticationAuditInterceptor
        if (isAuthenticationEndpoint(endpoint)) {
            log.debug("Skipping audit for authentication endpoint: {} {}", httpMethod, endpoint);
            return joinPoint.proceed();
        }
        
        Object result = null;
        
        try {
            // Proceed with the actual method execution
            result = joinPoint.proceed();
            
            // Calculate duration
            long duration = System.currentTimeMillis() - startTime;
            
            // Extract response data and status code
            Object responseData = null;
            Integer statusCode = 200;
            
            if (result instanceof ResponseEntity) {
                ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
                responseData = responseEntity.getBody();
                statusCode = responseEntity.getStatusCode().value();
            }
            
            // Extract result message from response
            String resultMessage = extractResultMessage(responseData, statusCode);
            
            // Log successful operation
            auditService.logSuccess(
                httpMethod,
                endpoint,
                resultMessage,
                statusCode,
                request,
                duration
            );
            
            return result;
            
        } catch (Throwable e) {
            // Calculate duration
            long duration = System.currentTimeMillis() - startTime;
            
            // Determine status code based on exception type
            Integer statusCode = determineStatusCodeFromException(e);
            
            // Extract result message from exception
            String resultMessage = "Error: " + e.getMessage();
            
            // Log failed operation
            auditService.logFailure(
                httpMethod,
                endpoint,
                resultMessage,
                statusCode,
                e.getMessage(),
                request,
                duration
            );
            
            throw e;
        }
    }

    /**
     * Extract result message from response data and status code
     */
    private String extractResultMessage(Object responseData, Integer statusCode) {
        // Extract message from ApiResponse if available
        if (responseData instanceof ApiResponse) {
            ApiResponse<?> apiResponse = (ApiResponse<?>) responseData;
            if (apiResponse.getStatus() != null && apiResponse.getStatus().getMessage() != null) {
                return apiResponse.getStatus().getMessage();
            }
        }
        
        // Default message based on status code
        if (statusCode >= 200 && statusCode < 300) {
            return "Success";
        } else if (statusCode >= 400 && statusCode < 500) {
            return "Client Error";
        } else if (statusCode >= 500) {
            return "Server Error";
        }
        
        return "Unknown";
    }

    /**
     * Determine HTTP status code based on exception type
     */
    private Integer determineStatusCodeFromException(Throwable exception) {
        String exceptionName = exception.getClass().getSimpleName();
        
        if (exceptionName.contains("NotFound") || exceptionName.contains("ResourceNotFound")) {
            return 404;
        } else if (exceptionName.contains("Validation") || exceptionName.contains("IllegalArgument")) {
            return 400;
        } else if (exceptionName.contains("AccessDenied") || exceptionName.contains("Forbidden")) {
            return 403;
        } else if (exceptionName.contains("Authentication") || exceptionName.contains("Unauthorized")) {
            return 401;
        } else if (exceptionName.contains("Conflict")) {
            return 409;
        } else {
            return 500;
        }
    }

    /**
     * Check if the endpoint is an authentication endpoint that should be handled
     * by AuthenticationAuditInterceptor instead of this aspect.
     */
    private boolean isAuthenticationEndpoint(String endpoint) {
        return endpoint != null && (
            endpoint.contains("/auth/login") ||
            endpoint.contains("/auth/logout") ||
            endpoint.contains("/auth/signup") ||
            endpoint.contains("/auth/tokens")
        );
    }
}
