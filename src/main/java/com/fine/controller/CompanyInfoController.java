package com.fine.controller;

import com.fine.Utils.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/config")
public class CompanyInfoController {

    @GetMapping("/company")
    public ResponseResult<Map<String, String>> getCompanyInfo() {
        Map<String, String> m = new HashMap<>();
        m.put("companyName", "东莞市方恩电子材料科技有限公司");
        m.put("address", "广东省东莞市桥头镇东新路13号2号楼102室");
        m.put("phone", "0769-82551118");
        m.put("fax", "0769-82551160");
        m.put("website", "www.finechemfr.com");
        return ResponseResult.success(m);
    }
}
