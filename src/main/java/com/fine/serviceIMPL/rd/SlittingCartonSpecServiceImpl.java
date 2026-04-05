package com.fine.serviceIMPL.rd;

import com.fine.Dao.rd.SlittingCartonSpecMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.rd.SlittingCartonSpec;
import com.fine.service.rd.SlittingCartonSpecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SlittingCartonSpecServiceImpl implements SlittingCartonSpecService {

    @Autowired
    private SlittingCartonSpecMapper slittingCartonSpecMapper;

    @Override
    public ResponseResult<?> getList(int page, int size, String materialCode, String specName, Integer status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;

        List<SlittingCartonSpec> records = slittingCartonSpecMapper.selectList(materialCode, specName, status, offset, safeSize);
        int total = slittingCartonSpecMapper.selectCount(materialCode, specName, status);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return ResponseResult.success("查询成功", result);
    }

    @Override
    public ResponseResult<?> getById(Long id) {
        SlittingCartonSpec spec = slittingCartonSpecMapper.selectById(id);
        if (spec == null) {
            return ResponseResult.fail("记录不存在");
        }
        return ResponseResult.success("查询成功", spec);
    }

    @Override
    public ResponseResult<?> getByMaterialCode(String materialCode, Integer status) {
        String code = materialCode == null ? "" : materialCode.trim().toUpperCase();
        if (code.isEmpty()) {
            return ResponseResult.fail("料号不能为空");
        }
        List<SlittingCartonSpec> list = slittingCartonSpecMapper.selectByMaterialCode(code, status);
        return ResponseResult.success("查询成功", list);
    }

    @Override
    public ResponseResult<?> create(SlittingCartonSpec spec, String operator) {
        ResponseResult<?> valid = validate(spec, false);
        if (valid != null) return valid;

        spec.setMaterialCode(spec.getMaterialCode().trim().toUpperCase());
        if (slittingCartonSpecMapper.checkExists(spec.getMaterialCode(), spec.getSpecName().trim(), 0L) > 0) {
            return ResponseResult.fail("同料号下规格名称已存在");
        }

        spec.setStatus(spec.getStatus() == null ? 1 : spec.getStatus());
        spec.setCreateBy(operator);
        spec.setUpdateBy(operator);
        slittingCartonSpecMapper.insert(spec);
        return ResponseResult.success("新增成功", spec);
    }

    @Override
    public ResponseResult<?> update(SlittingCartonSpec spec, String operator) {
        if (spec == null || spec.getId() == null) {
            return ResponseResult.fail("ID不能为空");
        }
        ResponseResult<?> valid = validate(spec, true);
        if (valid != null) return valid;

        spec.setMaterialCode(spec.getMaterialCode().trim().toUpperCase());
        if (slittingCartonSpecMapper.checkExists(spec.getMaterialCode(), spec.getSpecName().trim(), spec.getId()) > 0) {
            return ResponseResult.fail("同料号下规格名称已存在");
        }

        SlittingCartonSpec db = slittingCartonSpecMapper.selectById(spec.getId());
        if (db == null) {
            return ResponseResult.fail("记录不存在");
        }

        spec.setUpdateBy(operator);
        slittingCartonSpecMapper.update(spec);
        return ResponseResult.success("更新成功", null);
    }

    @Override
    public ResponseResult<?> delete(Long id) {
        if (id == null) {
            return ResponseResult.fail("ID不能为空");
        }
        int rows = slittingCartonSpecMapper.deleteById(id);
        if (rows <= 0) {
            return ResponseResult.fail("删除失败，记录不存在");
        }
        return ResponseResult.success("删除成功", null);
    }

    private ResponseResult<?> validate(SlittingCartonSpec spec, boolean update) {
        if (spec == null) return ResponseResult.fail("参数不能为空");
        String materialCode = spec.getMaterialCode() == null ? "" : spec.getMaterialCode().trim();
        String specName = spec.getSpecName() == null ? "" : spec.getSpecName().trim();
        if (materialCode.isEmpty()) return ResponseResult.fail("料号不能为空");
        if (specName.isEmpty()) return ResponseResult.fail("规格名称不能为空");
        if (spec.getLengthMm() == null || spec.getLengthMm() <= 0) return ResponseResult.fail("纸箱长必须大于0");
        if (spec.getWidthMm() == null || spec.getWidthMm() <= 0) return ResponseResult.fail("纸箱宽必须大于0");
        if (spec.getHeightMm() == null || spec.getHeightMm() <= 0) return ResponseResult.fail("纸箱高必须大于0");
        if (spec.getStatus() != null && spec.getStatus() != 0 && spec.getStatus() != 1) {
            return ResponseResult.fail("状态值非法");
        }
        if (update && spec.getId() == null) return ResponseResult.fail("ID不能为空");
        return null;
    }
}
