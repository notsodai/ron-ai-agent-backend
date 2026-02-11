package com.ron.ronaiagent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    @Value("${app.admin.token:}")
    private String adminToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (adminToken == null || adminToken.isBlank()) {
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Admin token is not configured");
            return false;
        }

        String headerToken = request.getHeader("X-Admin-Token");
        if (adminToken.equals(headerToken)) {
            return true;
        }

        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid admin token");
        return false;
    }
}
