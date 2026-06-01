package com.srm.modules.user.controller;

import com.srm.common.CacheConstants;
import com.srm.common.Result;
import com.srm.common.exception.BusinessException;
import com.srm.common.util.JwtUtil;
import com.srm.modules.user.dto.LoginDTO;
import com.srm.modules.user.entity.User;
import com.srm.modules.user.service.UserService;
import com.srm.modules.user.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import at.favre.lib.crypto.bcrypt.BCrypt;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Tag(name = "登录认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final StringRedisTemplate stringRedisTemplate;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        // 1. 查用户
        User user = userService.getByUsername(dto.getUsername());
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 2. 验密码（BCrypt）
        BCrypt.Result result = BCrypt.verifyer().verify(dto.getPassword().toCharArray(), user.getPassword());
        if (!result.verified) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 3. 验状态
        if (user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }

        // 4. 生成 Token
        String token = JwtUtil.generate(user.getId(), user.getUsername(), user.getIsAdmin());

        // 5. 存储 Token 到 Redis（覆盖旧 Token，实现单设备登录）
        String tokenKey = CacheConstants.LOGIN_TOKEN_KEY + user.getId();
        stringRedisTemplate.opsForValue().set(tokenKey, token,
                Duration.ofHours(CacheConstants.TOKEN_TTL_HOURS));

        // 6. 构造响应
        LoginVO vo = new LoginVO();
        BeanUtils.copyProperties(user, vo);
        vo.setToken(token);
        vo.setUserId(user.getId());

        return Result.ok("登录成功", vo);
    }

    @Operation(summary = "用户退出")
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (JwtUtil.isValid(token)) {
                Long userId = JwtUtil.getUserId(token);
                stringRedisTemplate.delete(CacheConstants.LOGIN_TOKEN_KEY + userId);
            }
        }
        return Result.ok("已退出");
    }
}
