package com.fine.service.quality;

import com.fine.model.quality.MaterialQcRule;
import java.util.List;

public interface MaterialQcRuleService {
    List<MaterialQcRule> getQcItemsByMaterialCode(String materialCode);
}
