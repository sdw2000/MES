package com.fine.serviceIMPL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                user.getUsername(), user.getPassword());
        // 调用authenticate方法认证时
        // 会执行UserDetailsServiceImpl中的loadUserByUsername方法
        Authentication authenticate = authenticationManager.authenticate(authenticationToken);
        if (Objects.isNull(authenticate)) {
            throw new RuntimeException("用户名或密码错误");
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
        
        redisCache.setCacheObject("login:" + userId, loginUserJson);
        
        // 验证Redis写入
        String verify = redisCache.getCacheObject("login:" + userId);
        System.out.println("Redis验证: " + (verify != null && verify.length() > 0 ? "成功(长度:" + verify.length() + ")" : "失败"));
        System.out.println("================");
          // 把token响应给前端
        HashMap<String, String> map = new HashMap<>();
        map.put("token", jwt);
        return new ResponseResult<>(20000, "登陆成功", map);    }    @Override
    public ResponseResult<?> info(String token) {
        
        Map<String, Object> map = new HashMap<>();
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        String nameString = loginUser.getUser().getUsername();
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
