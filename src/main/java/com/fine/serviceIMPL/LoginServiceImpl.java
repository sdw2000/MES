package com.fine.serviceIMPL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson.JSON;
import com.fine.Utils.JwtUtil;
import com.fine.Utils.RedisCache;
import com.fine.Utils.ResponseResult;
import com.fine.modle.LoginUser;
import com.fine.modle.Order2;
import com.fine.modle.User;
import com.fine.service.LoginServcie;
import com.fine.service.RoleService;


@Service
public class LoginServiceImpl implements LoginServcie {

    @Autowired
    @Lazy
    private AuthenticationManager authenticationManager;
    @Autowired
    @Lazy
    private RedisCache redisCache;
    @Autowired
    private RoleService roleService;@Override
    public ResponseResult<?> login(User user) {
        if (user == null || !StringUtils.hasText(user.getUsername()) || !StringUtils.hasText(user.getPassword())) {
            return new ResponseResult<>(400, "用户名和密码不能为空");
        }

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                user.getUsername(), user.getPassword());
        // 调用authenticate方法认证时
        // 会执行UserDetailsServiceImpl中的loadUserByUsername方法
        Authentication authenticate;
        try {
            authenticate = authenticationManager.authenticate(authenticationToken);
        } catch (BadCredentialsException e) {
            return new ResponseResult<>(401, "用户名或密码错误");
        } catch (AuthenticationException e) {
            return new ResponseResult<>(401, "认证失败：" + e.getMessage());
        }

        if (Objects.isNull(authenticate)) {
            return new ResponseResult<>(401, "用户名或密码错误");
        }        // 使用userid生成token
        LoginUser loginUser = (LoginUser) authenticate.getPrincipal();
        String userId = loginUser.getUser().getId().toString();
        String jwt = JwtUtil.createJWT(userId);
        
        // 同步写入Redis（核心修复）
        String loginUserJson = JSON.toJSONString(loginUser);
        System.out.println("=== 登录调试 ===");
        System.out.println("User ID: " + userId);
        System.out.println("JSON长度: " + loginUserJson.length());
        System.out.println("JSON前100字符: " + (loginUserJson.length() > 100 ? loginUserJson.substring(0, 100) : loginUserJson));
        
                try {
                        redisCache.setCacheObject("login:" + userId, loginUserJson);

                        // 验证Redis写入
                        String verify = redisCache.getCacheObject("login:" + userId);
                        System.out.println("Redis验证: " + (verify != null && verify.length() > 0 ? "成功(长度:" + verify.length() + ")" : "失败"));
                        System.out.println("================");
                } catch (Exception e) {
                        System.err.println("登录写入Redis失败: " + e.getMessage());
                        return new ResponseResult<>(500, "登录失败：会话服务异常，请检查Redis");
                }

          // 把token响应给前端
        HashMap<String, String> map = new HashMap<>();
        map.put("token", jwt);
        return new ResponseResult<>(20000, "登陆成功", map);    }    @Override
    public ResponseResult<?> info(String token) {
        
        Map<String, Object> map = new HashMap<>();
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = null;
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            loginUser = (LoginUser) authentication.getPrincipal();
        }

        // 兼容老接口通过 query 参数传 token 的场景，避免 SecurityContext 为空时抛 500
        if (loginUser == null && StringUtils.hasText(token)) {
            try {
                String userId = JwtUtil.parseJWT(token).getSubject();
                String loginUserJson = redisCache.getCacheObject("login:" + userId);
                if (StringUtils.hasText(loginUserJson)) {
                    loginUser = JSON.parseObject(loginUserJson, LoginUser.class);
                }
            } catch (Exception ignored) {
                // 交由下方统一返回未登录
            }
        }

        if (loginUser == null || loginUser.getUser() == null) {
            return new ResponseResult<>(401, "未登录或登录已过期");
        }

        String nameString = loginUser.getUser().getUsername();
        String realName = loginUser.getUser().getRealName();
        Long id = loginUser.getUser().getId();
        
        // 从数据库获取用户角色
        List<String> roleNames = roleService.getRoleNamesByUserId(id);
        String[] roles;
        if (roleNames == null || roleNames.isEmpty()) {
            // 如果没有分配角色，默认给一个基础角色
            roles = new String[]{"user"};
        } else {
            roles = roleNames.toArray(new String[0]);
        }
        
        map.put("roles", roles);
        map.put("introduction", "I'm very big man");
        map.put("avatar", "https://www.baidu.com/img/flexible/logo/pc/result@2.png");
        map.put("name", nameString);
        map.put("realName", realName != null ? realName : "");
        map.put("id", id);
          
        return new ResponseResult<>(20000, "登陆成功", map);
    }
    
    @Override
    public ResponseResult<?> logout() {
        try {
            // 获取当前认证信息
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
                return new ResponseResult<>(20000, "退出成功");
            }
            
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();
            Long userid = loginUser.getUser().getId();
            
            // 从Redis中删除用户登录信息
            String redisKey = "login:" + userid;
            redisCache.deleteObject(redisKey);
            
            // 清除SecurityContext
            SecurityContextHolder.clearContext();
            
            System.out.println("=== 用户退出 ===");
            System.out.println("User ID: " + userid);
            System.out.println("Redis键已删除: " + redisKey);
            System.out.println("===============");
            
            return new ResponseResult<>(20000, "退出成功");
        } catch (Exception e) {
            System.err.println("退出失败: " + e.getMessage());
            e.printStackTrace();
            // 即使出错也返回成功，让前端清除token
            return new ResponseResult<>(20000, "退出成功");
        }
    }	@Override
	public ResponseResult<?> getList() {
		// Mock data for dashboard - returns sample order data
		Map<String, Object> map=new HashMap<>();
		map.put("total", 20);
		
		Order2 order=new Order2("45fFA30B-0FBc-54D8-ABAC-BF8F5a30E95E", 3l, "Mary Miller",5833.70, "pending");		Order2[] orders=new Order2[1];
		orders[0]=order;
		map.put("items", orders);		
		return new ResponseResult<>(20000, "登陆成功", map);
	}
}
