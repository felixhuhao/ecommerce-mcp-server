package com.ecommerce.agent.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ecommerce.agent.auth.TrustedActor;
import com.ecommerce.agent.auth.TrustedActorContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TrustedActorFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TrustedActorFilter.class);

    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String SESSION_ID_HEADER = "X-Session-Id";

    private final String serviceToken;
    private final TrustedActorContext trustedActorContext;

    public TrustedActorFilter(
            @Value("${app.auth.service-token}") String serviceToken,
            TrustedActorContext trustedActorContext) {
        this.serviceToken = serviceToken;
        this.trustedActorContext = trustedActorContext;
        if (serviceToken == null || serviceToken.isBlank()) {
            logger.warn("app.auth.service-token is not configured; all non-actuator requests will return 401");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/actuator/health") || path.equals("/actuator/info");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isValidServiceToken(request.getHeader(SERVICE_TOKEN_HEADER))) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "missing or invalid service token");
            return;
        }

        TrustedActor actor = trustedActor(request, response);
        if (actor == null) {
            return;
        }

        trustedActorContext.setCurrentActor(actor);
        try {
            filterChain.doFilter(request, response);
        } finally {
            trustedActorContext.clear();
        }
    }

    private TrustedActor trustedActor(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            Long userId = Long.valueOf(request.getHeader(USER_ID_HEADER));
            return new TrustedActor(userId, request.getHeader(SESSION_ID_HEADER));
        } catch (RuntimeException e) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "missing or invalid trusted actor");
            return null;
        }
    }

    private boolean isValidServiceToken(String candidate) {
        if (serviceToken == null || serviceToken.isBlank() || candidate == null) {
            return false;
        }

        return MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }
}
