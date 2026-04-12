package com.fine.filler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Locale;
import java.util.Set;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.util.StringUtils;
import io.jsonwebtoken.Claims;
import com.alibaba.fastjson.JSON;
import com.fine.Utils.JwtUtil;
import com.fine.Utils.RedisCache;
import com.fine.modle.LoginUser;

@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    @Autowired
    private RedisCache redisCache;
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                   @NonNull HttpServletResponse response, 
                                   @NonNull FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if ("/user/logout".equals(uri) || "/user/login".equals(uri)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 获取token - 支持多种header名称
        String token = request.getHeader("token");
        if (!StringUtils.hasText(token)) {
            token = request.getHeader("X-Token");
        }
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("token");
        }
        if (!StringUtils.hasText(token)) {
            // 无token，放行
            filterChain.doFilter(request, response);
            return;
        }

        String userid;
        try {
            Claims claims = JwtUtil.parseJWT(token);
            userid = claims.getSubject();
        } catch (Exception e) {
            e.printStackTrace();
            // 使用认证异常，以便交由 AuthenticationEntryPoint 统一处理为 401
            throw new org.springframework.security.authentication.BadCredentialsException("token非法", e);
        }
        
        //从redis中获取用户信息
        String redisKey = "login:" + userid;
        String loginUserJson = redisCache.getCacheObject(redisKey);
        if(!StringUtils.hasText(loginUserJson)){
            throw new org.springframework.security.authentication.BadCredentialsException("用户未登录");
        }
        
        // 将JSON字符串反序列化为LoginUser对象
        LoginUser loginUser = JSON.parseObject(loginUserJson, LoginUser.class);
        if(Objects.isNull(loginUser)){
            throw new org.springframework.security.authentication.BadCredentialsException("用户未登录");
        }

        // 兼容历史会话：涂布/coating 自动补齐 production 权限
        loginUser.setPermissions(normalizePermissions(loginUser.getPermissions()));
        
        //存入SecurityContextHolder
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(loginUser,null,loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        //放行
        filterChain.doFilter(request, response);
    }

    private java.util.List<String> normalizePermissions(java.util.List<String> rawPermissions) {
        Set<String> normalized = new LinkedHashSet<>();
        if (rawPermissions != null) {
            for (String raw : rawPermissions) {
                String role = raw == null ? "" : raw.trim();
                if (role.isEmpty()) {
                    continue;
                }
                String lower = role.toLowerCase(Locale.ROOT);
                normalized.add(lower);
                if ("涂布".equals(role) || "coating".equals(lower)) {
                    normalized.add("coating");
                    normalized.add("production");
                }
            }
        }
        return new ArrayList<>(normalized);
    }
}

