package com.fine.controller.dev;

import com.alibaba.fastjson.JSON;
import com.fine.Utils.JwtUtil;
import com.fine.Utils.RedisCache;
import com.fine.Utils.ResponseResult;
import com.fine.modle.LoginUser;
import com.fine.modle.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/dev")
public class DevAuthController {

    @Autowired
    private RedisCache redisCache;

    /**
     * 简易开发令牌生成：创建 Redis 登录态并返回 JWT。
     * 示例：/api/dev/token?userId=1&username=admin&permissions=admin,production
     */
    @GetMapping("/token")
    public ResponseResult<Object> token(
            @RequestParam Long userId,
            @RequestParam String username,
            @RequestParam(defaultValue = "admin") String permissions
    ) {
        List<String> perms = Arrays.asList(permissions.split(","));

        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        user.setPassword("");

        LoginUser loginUser = new LoginUser(user, perms);
        String key = "login:" + userId;
        redisCache.setCacheObject(key, JSON.toJSONString(loginUser));

        String token = JwtUtil.createJWT(String.valueOf(userId));
        return ResponseResult.success(new java.util.HashMap<String, Object>() {{
            put("token", token);
            put("userId", userId);
            put("permissions", perms);
        }});
    }
}
