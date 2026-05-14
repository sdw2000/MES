package com.fine.serviceIMPL.quality;

import com.fine.Dao.quality.MaterialQcRuleMapper;
import com.fine.model.quality.MaterialQcRule;
import com.fine.service.quality.MaterialQcRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MaterialQcRuleServiceImpl implements MaterialQcRuleService {
    @Autowired
    private MaterialQcRuleMapper materialQcRuleMapper;

    @Override
    public List<MaterialQcRule> getQcItemsByMaterialCode(String materialCode) {
        return materialQcRuleMapper.selectByMaterialCode(materialCode);
    }
}
