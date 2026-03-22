package org.ganjp.api.common.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous operations with MDC context propagation.
 * This ensures that logging context (request ID, session ID, etc.) is preserved
 * when operations are executed in a different thread.
 */
@Configuration
@EnableAsync
public class AsyncLoggerConfig {

    /**
     * Bean for the main async task executor.
     * This executor will propagate MDC context to the async methods.
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Configure thread pool
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("GjpbAsync-");
        
        // Add MDC context propagation decorator
        executor.setTaskDecorator(new MdcContextDecorator());
        
        executor.initialize();
        return executor;
    }
    
    /**
     * Task decorator that propagates the MDC context from the calling thread
     * to the thread that executes the task.
     */
    static class MdcContextDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            // Capture the context from the current thread
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            
            // Return a runnable that sets up the MDC context before execution
            // and clears it afterward
            return () -> {
                try {
                    // Set up the MDC context in the worker thread
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    // Execute the original task
                    runnable.run();
                } finally {
                    // Clear the MDC context
                    MDC.clear();
                }
            };
        }
    }
}
