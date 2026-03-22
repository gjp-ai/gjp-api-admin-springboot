package org.ganjp.api.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ganjp.api.auth.security.AuthenticationAuditInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;

/**
 * Configuration for audit logging functionality.
 */
@Configuration
@EnableAspectJAutoProxy
@EnableAsync
@EnableConfigurationProperties(AuditProperties.class)
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = true)
public class AuditConfig implements WebMvcConfigurer {

    private final AuditProperties auditProperties;
    private final AuthenticationAuditInterceptor authenticationAuditInterceptor;
    
    public AuditConfig(AuditProperties auditProperties, 
                      @Lazy AuthenticationAuditInterceptor authenticationAuditInterceptor) {
        this.auditProperties = auditProperties;
        this.authenticationAuditInterceptor = authenticationAuditInterceptor;
    }

    /**
     * Configure async executor for audit logging to prevent blocking main threads
     * Also serves as the primary task executor for all async operations
     */
    @Bean(name = "auditTaskExecutor")
    @Primary
    public Executor auditTaskExecutor() {
        AuditProperties.ThreadPoolConfig config = auditProperties.getThreadPool();
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCorePoolSize());
        executor.setMaxPoolSize(config.getMaxPoolSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix(config.getThreadNamePrefix());
        executor.setKeepAliveSeconds(config.getKeepAliveSeconds());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * ObjectMapper for JSON serialization in audit logs
     */
    @Bean("auditObjectMapper")
    public ObjectMapper auditObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    /**
     * Register authentication audit interceptor
     */
    @Override
    public void addInterceptors(@org.springframework.lang.NonNull InterceptorRegistry registry) {
        if (auditProperties.isAuditAuthenticationEvents()) {
            registry.addInterceptor(authenticationAuditInterceptor)
                    .addPathPatterns("/v*/auth/**");
        }
    }
}
