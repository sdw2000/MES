package com.fine.service;

import com.fine.Utils.ResponseResult;
import com.fine.modle.User;




public interface LoginServcie {


    ResponseResult<?> login(User user);


    ResponseResult<?> info(String token);


    ResponseResult<?> logout();


	ResponseResult<?> getList();
    


}
