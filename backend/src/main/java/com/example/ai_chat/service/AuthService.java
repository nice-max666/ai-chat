package com.example.ai_chat.service;

import com.example.ai_chat.common.LoginRequest;
import com.example.ai_chat.entity.User;
import com.example.ai_chat.repository.UserRepository;
import com.example.ai_chat.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    /** 用户注册，返回注册成功的用户信息 */
    public User register(LoginRequest request) {
        // 校验用户名格式
        if (request.getUsername() == null || request.getUsername().length() < 3
                || request.getUsername().length() > 20) {
            throw new RuntimeException("用户名长度需在3-20个字符之间");
        }
        // 校验密码格式
        if (request.getPassword() == null || request.getPassword().length() < 6
                || request.getPassword().length() > 20) {
            throw new RuntimeException("密码长度需在6-20个字符之间");
        }
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已被注册");
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    /** 用户登录，返回 token 和用户名 */
    public Map<String, String> login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername());
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        Map<String, String> result = new HashMap<>();
        result.put("token", token);
        result.put("username", user.getUsername());
        return result;
    }
}
