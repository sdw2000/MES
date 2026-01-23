package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.SafetyStock;
import com.fine.service.production.SafetyStockService;
import com.fine.Utils.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全库存Controller
 */
@Api(tags = "安全库存管理")
@RestController
@RequestMapping("/production/safety-stock")
@PreAuthorize("hasAnyAuthority('admin','production','warehouse')")
public class SafetyStockController {

    @Autowired
    private SafetyStockService safetyStockService;

    @ApiOperation("分页查询安全库存列表")
    @GetMapping("/list")
    public ResponseResult<Map<String, Object>> getList(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String stockType,
            @RequestParam(required = false, defaultValue = "false") Boolean lowStockOnly,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        
        // 使用MyBatis-Plus分页方式
        IPage<SafetyStock> pageResult = safetyStockService.getSafetyStockPage(
            materialCode, stockType, lowStockOnly, page, size);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("pages", pageResult.getPages());
        result.put("hasNextPage", pageResult.getCurrent() < pageResult.getPages());
        
        return ResponseResult.success(result);
    }

    @ApiOperation("根据料号和库存类型查询")
    @GetMapping("/get")
    public ResponseResult<SafetyStock> getByMaterialAndType(
            @RequestParam String materialCode,
            @RequestParam String stockType) {
        SafetyStock safetyStock = safetyStockService.getByMaterialAndType(materialCode, stockType);
        return ResponseResult.success(safetyStock);
    }

    @ApiOperation("获取安全库存详情")
    @GetMapping("/{id}")
    public ResponseResult<SafetyStock> getById(@PathVariable Long id) {
        SafetyStock safetyStock = safetyStockService.getById(id);
        return ResponseResult.success(safetyStock);
    }

    @ApiOperation("新增安全库存配置")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin','production')")
    public ResponseResult<Void> add(@RequestBody SafetyStock safetyStock) {
        try {
            boolean success = safetyStockService.addSafetyStock(safetyStock);
            return success ? ResponseResult.success() : ResponseResult.fail("新增失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("更新安全库存配置")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin','production')")
    public ResponseResult<Void> update(@PathVariable Long id, @RequestBody SafetyStock safetyStock) {
        try {
            safetyStock.setId(id);
            boolean success = safetyStockService.updateSafetyStock(safetyStock);
            return success ? ResponseResult.success() : ResponseResult.fail("更新失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("删除安全库存配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin','production')")
    public ResponseResult<Void> delete(@PathVariable Long id) {
        boolean success = safetyStockService.deleteSafetyStock(id);
        return success ? ResponseResult.success() : ResponseResult.fail("删除失败");
    }

    @ApiOperation("查询需要补货的产品列表")
    @GetMapping("/need-restock")
    public ResponseResult<List<SafetyStock>> getNeedRestockList() {
        List<SafetyStock> list = safetyStockService.getNeedRestockList();
        return ResponseResult.success(list);
    }

    @ApiOperation("获取库存预警统计")
    @GetMapping("/warning-stats")
    public ResponseResult<Map<String, Object>> getWarningStats() {
        Map<String, Object> stats = safetyStockService.getStockWarningStats();
        return ResponseResult.success(stats);
    }
}
