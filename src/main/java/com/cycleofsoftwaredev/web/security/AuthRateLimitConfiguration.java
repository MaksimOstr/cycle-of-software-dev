package com.cycleofsoftwaredev.web.security;

import com.cycleofsoftwaredev.web.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Applies {@link AuthRateLimiter} to the endpoints where passwords can be guessed or accounts mass-created. */
@Configuration
public class AuthRateLimitConfiguration implements WebMvcConfigurer {

    private final AuthRateLimiter rateLimiter;

    public AuthRateLimitConfiguration(AuthRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
                    throw new TooManyRequestsException("Too many requests, try again in a minute");
                }
                return true;
            }
        }).addPathPatterns("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/password-reset/**");
    }
}
