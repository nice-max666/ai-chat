package com.example.ai_chat.controller;

import com.example.ai_chat.common.LoginRequest;
import com.example.ai_chat.common.Result;
import com.example.ai_chat.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /** 用户注册 */
    @PostMapping("/register")
    public Result<?> register(@RequestBody LoginRequest request) {
        try {
            authService.register(request);
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 用户登录，返回 JWT Token */
    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody LoginRequest request) {
        try {
            Map<String, String> data = authService.login(request);
            return Result.success(data);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
