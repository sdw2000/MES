package com.fine.service;

import com.fine.Utils.ResponseResult;
import java.util.Map;

public interface GlService {
    ResponseResult<?> postEntry(Map<String, Object> payload);
}
