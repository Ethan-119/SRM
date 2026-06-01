package com.srm.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {

    private static final String SECRET = "SRM-2024-Supplier-Relationship-Management-JWT-Secret-Key-256bit!!";
    private static final long EXPIRE_SECONDS = 24 * 60 * 60 * 1000L; // 24小时

    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    /** 生成 Token */
    public static String generate(Long userId, String username, Integer isAdmin) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + EXPIRE_SECONDS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("isAdmin", isAdmin)
                .issuedAt(now)
                .expiration(expire)
                .signWith(KEY)
                .compact();
    }

    /** 解析 Token */
    public static Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 校验 Token 是否有效 */
    public static boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 从 Token 中获取用户ID */
    public static Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }
}
