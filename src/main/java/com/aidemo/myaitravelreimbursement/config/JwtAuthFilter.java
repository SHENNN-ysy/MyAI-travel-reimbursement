package com.aidemo.myaitravelreimbursement.config;

import com.aidemo.myaitravelreimbursement.common.UserContext;
import com.aidemo.myaitravelreimbursement.entity.User;
import com.aidemo.myaitravelreimbursement.mapper.UserMapper;
import com.aidemo.myaitravelreimbursement.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/swagger-ui",
            "/v3/api-docs"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            addCorsHeaders(response);
            filterChain.doFilter(request, response);
            return;
        }

        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = getTokenFromRequest(request);

        if (!StringUtils.hasText(token)) {
            sendUnauthorized(response, "未登录或登录已过期，请重新登录");
            return;
        }

        try {
            if (jwtUtil.isTokenExpired(token)) {
                sendUnauthorized(response, "登录已过期，请重新登录");
                return;
            }

            Long userId = jwtUtil.getUserIdFromToken(token);
            User user = userMapper.selectById(userId);
            if (user != null && user.getStatus() == 1) {
                UserContext.setUser(user);
            } else {
                sendUnauthorized(response, "用户不存在或已被禁用");
                return;
            }
        } catch (Exception e) {
            log.error("JwtAuthFilter 解析token失败", e);
            sendUnauthorized(response, "Token无效，请重新登录");
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::equals) ||
               PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("access_token".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        addCorsHeaders(response);
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }

    private void addCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "DNT, X-Mx-ReqToken, Keep-Alive, User-Agent, X-Requested-With, If-Modified-Since, Cache-Control, Content-Type, Authorization, Accept");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Max-Age", "3600");
    }
}
