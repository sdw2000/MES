package com.fine.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fine.Utils.ResponseResult;
import com.fine.modle.User;
import com.fine.service.LoginServcie;



@RestController
public class LoginController {

    @Autowired
    private LoginServcie loginServcie;
    
    
    

    @PostMapping("/user/login")
    public ResponseResult<?> login(@RequestBody User user){
        System.out.println(user);
        return loginServcie.login(user); 
    }
    
    
//    @PreAuthorize("hasAuthority('admin')")
//    @GetMapping("/user/info")
//    public ResponseResult<T> info(HttpServletRequest request) {
//        
//        ResponseResult<T> responseResult=new ResponseResult<T>(code, str, data);
//        
//            
//            responseResult.setMsg("登陆成功");
//            
//            return responseResult;
//        
//    }
    
    
      // 所有已认证用户都可以获取自己的信息
    @GetMapping("/user/info")
    public  ResponseResult<?> getInfo(@RequestParam("token") String tokenString) {
        System.out.println(tokenString+"这是传进来的token");
        
        ResponseResult<?> response = loginServcie.info(tokenString);
        System.out.println(response);
        return response;
    }
    
    @PreAuthorize("hasAuthority('admin')")
    @GetMapping("transaction/list")
    public  ResponseResult<?> getList() {
        ResponseResult<?> response = loginServcie.getList();
        System.out.println(response);
        return response;
    }
    
    
      // 所有已认证用户都可以退出登录
    @PostMapping("/user/logout")
    public  ResponseResult<?> logout() {
        ResponseResult<?> response = loginServcie.logout();
        return response;
    }
}

