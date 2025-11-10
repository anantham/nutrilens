package com.nutritheous.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aspect for monitoring performance of service methods and repositories.
 * Logs slow queries and API operations for performance analysis.
 */
@Aspect
@Component
@Slf4j
public class PerformanceMonitoringAspect {

    @Value("${app.performance.slow-query-threshold-ms:1000}")
    private long slowQueryThresholdMs;

    @Value("${app.performance.very-slow-query-threshold-ms:5000}")
    private long verySlowQueryThresholdMs;

    @Value("${app.performance.api-response-warning-ms:500}")
    private long apiResponseWarningMs;

    @Value("${app.performance.api-response-critical-ms:2000}")
    private long apiResponseCriticalMs;

    /**
     * Monitor all repository method executions.
     * Logs slow database queries for optimization.
     */
    @Around("execution(* com.nutritheous..repository..*(..))")
    public Object monitorRepositoryPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;

        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            // Log based on execution time
            if (executionTime >= verySlowQueryThresholdMs) {
                log.error("🐌 VERY SLOW QUERY: {} took {}ms (threshold: {}ms)",
                        fullMethodName, executionTime, verySlowQueryThresholdMs);
            } else if (executionTime >= slowQueryThresholdMs) {
                log.warn("⚠️  SLOW QUERY: {} took {}ms (threshold: {}ms)",
                        fullMethodName, executionTime, slowQueryThresholdMs);
            } else if (executionTime > 100) {
                log.debug("Query: {} took {}ms", fullMethodName, executionTime);
            }

            // Log if exception occurred
            if (exception != null) {
                log.error("❌ Query failed: {} - Exception: {}",
                        fullMethodName, exception.getMessage());
            }
        }
    }

    /**
     * Monitor all controller method executions.
     * Logs slow API responses for client experience analysis.
     */
    @Around("execution(* com.nutritheous..controller..*(..))")
    public Object monitorControllerPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;

        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            // Log based on execution time
            if (executionTime >= apiResponseCriticalMs) {
                log.error("🔴 CRITICAL: API response {} took {}ms (threshold: {}ms)",
                        fullMethodName, executionTime, apiResponseCriticalMs);
            } else if (executionTime >= apiResponseWarningMs) {
                log.warn("🟡 WARNING: API response {} took {}ms (threshold: {}ms)",
                        fullMethodName, executionTime, apiResponseWarningMs);
            } else if (executionTime > 100) {
                log.debug("✅ API response: {} took {}ms", fullMethodName, executionTime);
            }

            // Log if exception occurred
            if (exception != null) {
                log.error("❌ API error: {} - Exception: {}",
                        fullMethodName, exception.getMessage());
            }
        }
    }

    /**
     * Monitor service method executions for business logic performance.
     */
    @Around("execution(* com.nutritheous..service..*(..))")
    public Object monitorServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;

        long startTime = System.currentTimeMillis();

        try {
            return joinPoint.proceed();
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            // Only log if execution takes significant time
            if (executionTime > 200) {
                log.debug("Service: {} took {}ms", fullMethodName, executionTime);
            }
        }
    }
}
