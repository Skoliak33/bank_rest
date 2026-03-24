package com.example.bankcards.aspect;

import com.example.bankcards.security.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Аудит-лог банковских операций через AOP.
 * Перехватывает все публичные методы CardService и логирует:
 * кто выполнил, какую операцию, с какими параметрами, результат или ошибку.
 */
@Aspect
@Component
@Slf4j
public class AuditAspect {

    @Around("execution(public * com.example.bankcards.service.CardService.*(..))")
    public Object auditCardOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().getName();
        String user = getCurrentUser();
        String args = formatArgs(joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            log.info("[AUDIT] user={} action={} args=[{}] status=SUCCESS", user, method, args);
            return result;
        } catch (Exception ex) {
            log.warn("[AUDIT] user={} action={} args=[{}] status=FAILED reason={}",
                    user, method, args, ex.getMessage());
            throw ex;
        }
    }

    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUsername();
        }
        return "anonymous";
    }

    private String formatArgs(Object[] args) {
        return Arrays.stream(args)
                .filter(arg -> arg != null && !(arg instanceof CustomUserDetails))
                .map(arg -> {
                    try {
                        return arg.toString();
                    } catch (Exception e) {
                        return arg.getClass().getSimpleName() + "(error)";
                    }
                })
                .collect(Collectors.joining(", "));
    }
}
