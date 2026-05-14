package com.fine.Dao.quality;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.quality.MaterialQcRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface MaterialQcRuleMapper extends BaseMapper<MaterialQcRule> {
    @Select("SELECT * FROM material_qc_rule WHERE material_code = #{materialCode} AND is_active = 1 ORDER BY sort ASC, id ASC")
    List<MaterialQcRule> selectByMaterialCode(String materialCode);
}
