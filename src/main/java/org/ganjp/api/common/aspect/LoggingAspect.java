package org.ganjp.api.common.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.ganjp.api.common.config.LoggingConfig;
import org.ganjp.api.common.util.RequestUtils;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Aspect for logging method execution details.
 * Adds entry/exit logging for services and controllers with timing information.
 * Also captures exceptions and enhances logs with request/user context.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class LoggingAspect {

    /**
     * Pointcut for all service methods, excluding AuditService to prevent circular dependencies
     */
    @Pointcut("within(@org.springframework.stereotype.Service *) && !within(org.ganjp.api.common.audit.AuditService)")
    public void servicePointcut() {
    }

    /**
     * Pointcut for all controller methods 
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerPointcut() {
    }

    /**
     * Log around service methods
     */
    @Around("servicePointcut()")
    public Object logAroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "Service");
    }

    /**
     * Log around controller methods 
     */
    @Around("controllerPointcut()")
    public Object logAroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "Controller");
    }

    /**
     * Log exceptions thrown from services and controllers
     */
    @AfterThrowing(pointcut = "servicePointcut() || controllerPointcut()", throwing = "e")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable e) {
        // Get authenticated user if any
        String username = extractUsername();
        String userId = extractUserId();
        
        // Add user info to MDC temporarily for this log
        try {
            if (username != null) MDC.put(LoggingConfig.MDC_USERNAME_KEY, username);
            if (userId != null) MDC.put(LoggingConfig.MDC_USER_ID_KEY, userId);
            
            log.error("Exception in {}.{}() with message: {}", 
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    e.getMessage() != null ? e.getMessage() : "NULL");
        } finally {
            if (username != null) MDC.remove(LoggingConfig.MDC_USERNAME_KEY);
            if (userId != null) MDC.remove(LoggingConfig.MDC_USER_ID_KEY);
        }
    }
    
    /**
     * Helper method to log method execution with timing
     */
    private Object logMethodExecution(ProceedingJoinPoint joinPoint, String type) throws Throwable {
        // Get method signature for logging
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        
        // Get authenticated user if any
        String username = extractUsername();
        String userId = extractUserId();
        
        // Temporarily enhance MDC with user info if available
        boolean mcdUpdated = false;
        try {
            if (username != null && MDC.get(LoggingConfig.MDC_USERNAME_KEY) == null) {
                MDC.put(LoggingConfig.MDC_USERNAME_KEY, username);
                mcdUpdated = true;
            }
            if (userId != null && MDC.get(LoggingConfig.MDC_USER_ID_KEY) == null) {
                MDC.put(LoggingConfig.MDC_USER_ID_KEY, userId);
                mcdUpdated = true;
            }

            // For debug, consider logging arguments - be careful about sensitive data
            if (log.isDebugEnabled()) {
                log.debug("Enter: {}.{}() with argument[s] = {}", className, methodName, 
                        Arrays.toString(joinPoint.getArgs()));
            } else {
                log.info("Enter: {}.{}()", className, methodName);
            }

            long start = System.currentTimeMillis();
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - start;

            // Log method execution time
            if (log.isDebugEnabled() && result != null) {
                log.debug("Exit: {}.{}() with result = {} in {}ms", className, methodName, 
                        result.toString(), executionTime);
            } else {
                log.info("Exit: {}.{}() in {}ms", className, methodName, executionTime);
            }
            
            return result;
        } finally {
            // Clean up MDC additions
            if (mcdUpdated) {
                if (username != null) MDC.remove(LoggingConfig.MDC_USERNAME_KEY);
                if (userId != null) MDC.remove(LoggingConfig.MDC_USER_ID_KEY); 
            }
        }
    }
    
    /**
     * Extract username from Spring Security context
     */
    private String extractUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }
    
    /**
     * Extract user ID from Spring Security context
     */
    private String extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            Object principal = auth.getPrincipal();
            if (principal instanceof org.ganjp.api.auth.user.User) {
                return ((org.ganjp.api.auth.user.User) principal).getId();
            }
        }
        return null;
    }
}
