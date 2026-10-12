package com.moduDrive.common.core.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    @AfterThrowing(pointcut = "execution(* com.moduDrive.*.application.service.*.*(..))", throwing = "ex")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable ex) {
        logger.error("[Service] [Exception] {}.{}(): {}",
                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(), ex.getMessage());
    }

    @Before("execution(* com.moduDrive.*.application.service.*.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        logger.info("[Service] [Executing] {}.{}()",
                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName());
    }

    @After("execution(* com.moduDrive.*.application.service.*.*(..))")
    public void logAfter(JoinPoint joinPoint) {
        logger.info("[Service] [Completed] {}.{}()",
                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName());
    }
}
