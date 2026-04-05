package com.fine.controller.stock;

import com.fine.Utils.ResponseResult;
import com.fine.model.stock.ChemicalStock;
import com.fine.model.stock.ChemicalStockDetail;
import com.fine.model.stock.ChemicalStockOut;
import com.fine.service.stock.ChemicalStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 化工原料库存管理Controller
 * @author Fine
 * @date 2026-01-15
 */
@PreAuthorize("hasAnyAuthority('admin','warehouse','production','finance','quality')")
@RestController
@RequestMapping("/api/stock/chemical")
@CrossOrigin
public class ChemicalStockController {
    
    @Autowired
    private ChemicalStockService chemicalStockService;
    
    /** 查询所有化工库存 */
    @GetMapping("/list")
    public ResponseResult<List<ChemicalStock>> getChemicalStockList() {
        List<ChemicalStock> list = chemicalStockService.getAllChemicalStock();
        return new ResponseResult<>(20000, "查询成功", list);
    }
    
    /** 按类型查询化工库存 */
    @GetMapping("/type/{chemicalType}")
    public ResponseResult<List<ChemicalStock>> getByType(@PathVariable String chemicalType) {
        List<ChemicalStock> list = chemicalStockService.getByType(chemicalType);
        return new ResponseResult<>(20000, "查询成功", list);
    }
    
    /**
     * 根据ID查询化工库存
     */
    @GetMapping("/{id}")
    public ResponseResult<ChemicalStock> getById(@PathVariable Long id) {
        ChemicalStock chemicalStock = chemicalStockService.getById(id);
        if (chemicalStock == null) {
            return new ResponseResult<>(40004, "化工库存不存在", null);
        }
        return new ResponseResult<>(20000, "查询成功", chemicalStock);
    }
    
    /**
     * 查询化工库存明细
     */
    @GetMapping("/{id}/details")
    public ResponseResult<List<ChemicalStockDetail>> getDetails(@PathVariable Long id) {
        List<ChemicalStockDetail> details = chemicalStockService.getDetailsByChemicalStockId(id);
        return new ResponseResult<>(20000, "查询成功", details);
    }
    
    /** 查询可用的化工明细 */
    @GetMapping("/{id}/available")
    public ResponseResult<List<ChemicalStockDetail>> getAvailableDetails(@PathVariable Long id) {
        List<ChemicalStockDetail> details = chemicalStockService.getAvailableDetails(id);
        return new ResponseResult<>(20000, "查询成功", details);
    }
    
    /** 查询即将过期的化工原料 */
    @GetMapping("/expiring")
    public ResponseResult<List<ChemicalStockDetail>> getExpiringSoon(
            @RequestParam(defaultValue = "30") Integer days
    ) {
        List<ChemicalStockDetail> details = chemicalStockService.getExpiringSoon(days);
        return new ResponseResult<>(20000, "查询成功", details);
    }
    
    /**
     * 查询化工出库记录
     */
    @GetMapping("/outbound/schedule/{scheduleId}")
    public ResponseResult<List<ChemicalStockOut>> getOutboundBySchedule(@PathVariable Long scheduleId) {
        List<ChemicalStockOut> records = chemicalStockService.getOutboundByScheduleId(scheduleId);
        return new ResponseResult<>(20000, "查询成功", records);
    }
}
