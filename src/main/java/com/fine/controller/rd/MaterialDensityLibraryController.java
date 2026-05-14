package com.fine.controller.rd;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Dao.MaterialDensityLibraryMapper;
import com.fine.Utils.ResponseResult;
import com.fine.entity.MaterialDensityLibrary;
import com.fine.modle.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 常用材质密度库（研发）
 */
@RestController
@RequestMapping("/api/rd/material-density")
@PreAuthorize("hasAnyAuthority('admin','rd','sales','production','warehouse','finance','quality','packaging','packing','purchase','coating','plan','scheduler')")
public class MaterialDensityLibraryController {

    @Autowired
    private MaterialDensityLibraryMapper mapper;

    @GetMapping("/list")
    public ResponseResult<?> list(@RequestParam(required = false) Integer isActive) {
        try {
            QueryWrapper<MaterialDensityLibrary> wrapper = new QueryWrapper<>();
            if (isActive != null) {
                wrapper.eq("is_active", isActive);
            }
            wrapper.orderByDesc("is_active").orderByAsc("material_en_name");
            List<MaterialDensityLibrary> list = mapper.selectList(wrapper);
            return ResponseResult.success(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询材质密度库失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseResult<?> getById(@PathVariable Long id) {
        try {
            MaterialDensityLibrary item = mapper.selectById(id);
            if (item == null) {
                return ResponseResult.error("记录不存在");
            }
            return ResponseResult.success(item);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> create(@RequestBody MaterialDensityLibrary body) {
        try {
            if (body.getMaterialEnName() == null || body.getMaterialEnName().trim().isEmpty()
                    || body.getMaterialCnName() == null || body.getMaterialCnName().trim().isEmpty()
                    || body.getDensity() == null || body.getDensity() <= 0) {
                return ResponseResult.error("请填写材质英文名、中文名、密度");
            }

            QueryWrapper<MaterialDensityLibrary> wrapper = new QueryWrapper<>();
            wrapper.eq("material_en_name", body.getMaterialEnName().trim());
            Long count = mapper.selectCount(wrapper);
            if (count != null && count > 0) {
                return ResponseResult.error("材质英文名已存在");
            }

            body.setMaterialEnName(body.getMaterialEnName().trim());
            body.setMaterialCnName(body.getMaterialCnName().trim());
            body.setRemark(body.getRemark() == null ? null : body.getRemark().trim());
            body.setIsActive(body.getIsActive() == null ? 1 : body.getIsActive());
            body.setCreateBy(getCurrentUsername());
            body.setUpdateBy(getCurrentUsername());

            mapper.insert(body);
            return ResponseResult.success(body);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("创建失败: " + e.getMessage());
        }
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> update(@RequestBody MaterialDensityLibrary body) {
        try {
            if (body.getId() == null) {
                return ResponseResult.error("ID不能为空");
            }
            if (body.getMaterialEnName() == null || body.getMaterialEnName().trim().isEmpty()
                    || body.getMaterialCnName() == null || body.getMaterialCnName().trim().isEmpty()
                    || body.getDensity() == null || body.getDensity() <= 0) {
                return ResponseResult.error("请填写材质英文名、中文名、密度");
            }

            QueryWrapper<MaterialDensityLibrary> wrapper = new QueryWrapper<>();
            wrapper.eq("material_en_name", body.getMaterialEnName().trim()).ne("id", body.getId());
            Long count = mapper.selectCount(wrapper);
            if (count != null && count > 0) {
                return ResponseResult.error("材质英文名已存在");
            }

            body.setMaterialEnName(body.getMaterialEnName().trim());
            body.setMaterialCnName(body.getMaterialCnName().trim());
            body.setRemark(body.getRemark() == null ? null : body.getRemark().trim());
            body.setUpdateBy(getCurrentUsername());

            int rows = mapper.updateById(body);
            if (rows <= 0) {
                return ResponseResult.error("更新失败，记录不存在");
            }
            return ResponseResult.success(body);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> delete(@PathVariable Long id) {
        try {
            int rows = mapper.deleteById(id);
            if (rows <= 0) {
                return ResponseResult.error("删除失败，记录不存在");
            }
            return ResponseResult.success("删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("删除失败: " + e.getMessage());
        }
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUsername();
        }
        return "system";
    }
}
