package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.modle.stock.Warehouse;
import com.fine.modle.stock.WarehouseLocation;
import com.fine.service.stock.WarehouseLocationService;
import com.fine.service.stock.WarehouseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/warehouse")
public class WarehouseManagementController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WarehouseManagementController.class);

    @Autowired
    private WarehouseService warehouseService;

    @Autowired
    private WarehouseLocationService locationService;

    // ================= 仓库管理 =================

    @GetMapping("/list")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> listWarehouses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        log.info("Querying warehouse list: page={}, size={}, keyword={}", page, size, keyword);
        Page<Warehouse> warehousePage = new Page<>(page, size);
        LambdaQueryWrapper<Warehouse> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Warehouse::getWarehouseCode, keyword)
                    .or().like(Warehouse::getWarehouseName, keyword));
        }
        wrapper.orderByAsc(Warehouse::getWarehouseCode);
        IPage<Warehouse> result = warehouseService.page(warehousePage, wrapper);
        
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        return ResponseResult.success("查询成功", data);
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> saveWarehouse(@RequestBody Warehouse warehouse) {
        warehouseService.saveOrUpdate(warehouse);
        return ResponseResult.success("保存成功");
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> deleteWarehouse(@PathVariable Integer id) {
        // 检查是否有库位
        long count = locationService.count(new LambdaQueryWrapper<WarehouseLocation>().eq(WarehouseLocation::getWarehouseId, id));
        if (count > 0) {
            return ResponseResult.error("该仓库下存在库位，不能删除");
        }
        warehouseService.removeById(id);
        return ResponseResult.success("删除成功");
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> getAllWarehouses() {
        List<Warehouse> list = warehouseService.list(new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getStatus, 1).orderByAsc(Warehouse::getWarehouseCode));
        return ResponseResult.success("查询成功", list);
    }

    // ================= 库位管理 =================

    @GetMapping("/location/list")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> listLocations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer warehouseId,
            @RequestParam(required = false) String keyword) {
        Page<WarehouseLocation> locationPage = new Page<>(page, size);
        IPage<WarehouseLocation> result = locationService.listLocations(locationPage, warehouseId, keyword);
        
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        return ResponseResult.success("查询成功", data);
    }

    @PostMapping("/location/save")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> saveLocation(@RequestBody WarehouseLocation location) {
        locationService.saveOrUpdate(location);
        return ResponseResult.success("保存成功");
    }

    @DeleteMapping("/location/delete/{id}")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> deleteLocation(@PathVariable Integer id) {
        locationService.removeById(id);
        return ResponseResult.success("删除成功");
    }
}
