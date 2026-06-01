package com.srm.config.interceptor;

import com.srm.common.CacheConstants;
import com.srm.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoginInterceptor.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${srm.agent.api-key:}")
    private String internalApiKey;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 内部服务调用：检查 X-Api-Key 头
        String apiKey = request.getHeader("X-Api-Key");
        if (internalApiKey != null && !internalApiKey.isEmpty() && internalApiKey.equals(apiKey)) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"未登录，请先登录\"}");
            return false;
        }

        // 去掉 "Bearer " 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 1. JWT 有效性校验
        if (!JwtUtil.isValid(token)) {
            log.warn("Token 无效或已过期: {}", token);
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期，请重新登录\"}");
            return false;
        }

        // 2. 单设备登录校验：Redis 中的 Token 必须与请求一致
        Long userId = JwtUtil.getUserId(token);
        String redisToken = stringRedisTemplate.opsForValue().get(CacheConstants.LOGIN_TOKEN_KEY + userId);
        if (!token.equals(redisToken)) {
            log.warn("用户 {} 的 Token 已被新登录覆盖，强制下线", userId);
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"账号已在其他设备登录，请重新登录\"}");
            return false;
        }

        return true;
    }
}
