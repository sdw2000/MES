package com.fine.handle;

import com.alibaba.fastjson.JSON;
import com.fine.Utils.ResponseResult;
import com.fine.Utils.WebUtils;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        ResponseResult<?> result = new ResponseResult<>(HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage(), null);
        String json = JSON.toJSONString(result);
        WebUtils.renderString(response, json);
    }
}
