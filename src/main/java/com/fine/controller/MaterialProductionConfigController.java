package com.fine.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.entity.MaterialProductionConfig;
import com.fine.Dao.MaterialProductionConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料生产配置管理Controller
 * 用于管理物料的MOQ、生产参数等
 */
@RestController
@RequestMapping("/api/material-config")
@CrossOrigin
public class MaterialProductionConfigController {

    @Autowired
    private MaterialProductionConfigMapper configMapper;

    /**
     * 分页查询物料配置列表
     */
    @GetMapping("/list")
    public ResponseResult getList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) Integer isActive
    ) {
        try {
            Page<MaterialProductionConfig> page = new Page<>(pageNum, pageSize);
            QueryWrapper<MaterialProductionConfig> wrapper = new QueryWrapper<>();
            
            if (materialCode != null && !materialCode.isEmpty()) {
                wrapper.like("material_code", materialCode);
            }
            if (materialType != null && !materialType.isEmpty()) {
                wrapper.eq("material_type", materialType);
            }
            if (isActive != null) {
                wrapper.eq("is_active", isActive);
            }
            
            wrapper.orderByDesc("update_time");
            
            IPage<MaterialProductionConfig> result = configMapper.selectPage(page, wrapper);
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询物料配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询物料配置
     */
    @GetMapping("/{id}")
    public ResponseResult getById(@PathVariable Long id) {
        try {
            MaterialProductionConfig config = configMapper.selectById(id);
            if (config == null) {
                return ResponseResult.error("物料配置不存在");
            }
            return ResponseResult.success(config);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询物料配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据物料编号查询配置
     */
    @GetMapping("/by-code/{materialCode}")
    public ResponseResult getByMaterialCode(@PathVariable String materialCode) {
        try {
            QueryWrapper<MaterialProductionConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("material_code", materialCode)
                   .eq("is_active", 1);
            
            MaterialProductionConfig config = configMapper.selectOne(wrapper);
            if (config == null) {
                return ResponseResult.error("物料配置不存在");
            }
            return ResponseResult.success(config);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询物料配置失败: " + e.getMessage());
        }
    }

    /**
     * 创建物料配置
     */
    @PostMapping("/create")
    public ResponseResult create(@RequestBody MaterialProductionConfig config) {
        try {
            // 检查物料编号是否已存在
            QueryWrapper<MaterialProductionConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("material_code", config.getMaterialCode());
            
            Long count = configMapper.selectCount(wrapper);
            if (count > 0) {
                return ResponseResult.error("物料编号已存在");
            }
            
            int rows = configMapper.insert(config);
            if (rows > 0) {
                return ResponseResult.success(config);
            } else {
                return ResponseResult.error("创建失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新物料配置
     */
    @PutMapping("/update")
    public ResponseResult update(@RequestBody MaterialProductionConfig config) {
        try {
            if (config.getId() == null) {
                return ResponseResult.error("ID不能为空");
            }
            
            int rows = configMapper.updateById(config);
            if (rows > 0) {
                return ResponseResult.success(config);
            } else {
                return ResponseResult.error("更新失败，记录不存在");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除物料配置（逻辑删除，设置为不启用）
     */
    @DeleteMapping("/delete/{id}")
    public ResponseResult delete(@PathVariable Long id) {
        try {
            MaterialProductionConfig config = new MaterialProductionConfig();
            config.setId(id);
            config.setIsActive(0);
            
            int rows = configMapper.updateById(config);
            if (rows > 0) {
                return ResponseResult.success("删除成功");
            } else {
                return ResponseResult.error("删除失败，记录不存在");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量导入物料配置
     */
    @PostMapping("/batch-import")
    public ResponseResult batchImport(@RequestBody List<MaterialProductionConfig> configs) {
        try {
            int successCount = 0;
            int failCount = 0;
            
            for (MaterialProductionConfig config : configs) {
                try {
                    // 检查是否已存在
                    QueryWrapper<MaterialProductionConfig> wrapper = new QueryWrapper<>();
                    wrapper.eq("material_code", config.getMaterialCode());
                    
                    MaterialProductionConfig existing = configMapper.selectOne(wrapper);
                    if (existing != null) {
                        // 更新
                        config.setId(existing.getId());
                        configMapper.updateById(config);
                    } else {
                        // 插入
                        configMapper.insert(config);
                    }
                    successCount++;
                } catch (Exception e) {
                    e.printStackTrace();
                    failCount++;
                }
            }
            
            String message = String.format("导入完成：成功%d条，失败%d条", successCount, failCount);
            return ResponseResult.success(null, message);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("批量导入失败: " + e.getMessage());
        }
    }
}
