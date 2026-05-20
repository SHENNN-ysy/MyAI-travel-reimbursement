package com.aidemo.myaitravelreimbursement.controller;

import com.aidemo.myaitravelreimbursement.common.Result;
import com.aidemo.myaitravelreimbursement.dto.*;
import com.aidemo.myaitravelreimbursement.service.UserService;
import com.aidemo.myaitravelreimbursement.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证模块", description = "用户登录、注册接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "注册", description = "新用户注册")
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @Operation(summary = "登录", description = "用户名密码登录，返回JWT Token")
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletResponse response) {
        AuthResponse authResponse = userService.login(request);

        ResponseCookie cookie = ResponseCookie.from("access_token", authResponse.getToken())
                .path("/")
                .maxAge(jwtUtil.getExpiration() / 1000)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());

        return Result.success(authResponse);
    }

    @Operation(summary = "获取当前用户", description = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result<AuthResponse> getCurrentUser() {
        return Result.success(userService.getCurrentUser());
    }
}
