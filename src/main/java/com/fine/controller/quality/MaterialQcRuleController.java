package com.fine.controller.quality;

import com.fine.Utils.ResponseResult;
import com.fine.model.quality.MaterialQcRule;
import com.fine.service.quality.MaterialQcRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/quality/qc-items")
public class MaterialQcRuleController {
    @Autowired
    private MaterialQcRuleService materialQcRuleService;

    @GetMapping("")
    public ResponseResult<List<MaterialQcRule>> getQcItemsByMaterialCode(@RequestParam String materialCode) {
        List<MaterialQcRule> items = materialQcRuleService.getQcItemsByMaterialCode(materialCode);
        return ResponseResult.success(items);
    }
}
